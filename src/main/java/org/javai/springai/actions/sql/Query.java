package org.javai.springai.actions.sql;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.util.TablesNamesFinder;

/**
 * Represents a validated SQL SELECT query.
 * 
 * <p>This type provides a validated, ready-to-use SELECT statement to action methods.
 * By the time an action receives a Query, the following guarantees are met:</p>
 * 
 * <ul>
 *   <li><b>Syntactic validity</b> - The SQL parses correctly</li>
 *   <li><b>SELECT-only</b> - No INSERT, UPDATE, DELETE, or DDL statements</li>
 *   <li><b>Schema compliance</b> - Only references tables that exist in the catalog (if provided)</li>
 * </ul>
 * 
 * <p>The action body can focus on business logic rather than defensive validation.</p>
 * 
 * <h2>Dialect Configuration</h2>
 * 
 * <p>The SQL dialect can be configured once on the {@link SqlCatalog} and will be used
 * automatically by {@link #sqlString()}:</p>
 * 
 * <pre>{@code
 * // Configure dialect once at startup
 * SqlCatalog catalog = new InMemorySqlCatalog()
 *     .withDialect(Query.Dialect.POSTGRES)
 *     .addTable("orders", "Order table");
 * 
 * // In action method - dialect is automatic
 * @Action(description = "Run a SQL query against the database")
 * public void runSqlQuery(@ActionParam(description = "The SQL query to run") Query query) {
 *     String sql = query.sqlString();  // Automatically uses POSTGRES from catalog
 *     // Execute against database
 * }
 * }</pre>
 */
public record Query(Select select, SqlCatalog catalog) {

	/**
	 * Supported SQL dialects for output generation.
	 */
	public enum Dialect {
		/** Standard ANSI SQL (default) */
		ANSI,
		/** PostgreSQL-specific syntax */
		POSTGRES
	}

	/**
	 * Creates a Query from a raw SQL string with full validation.
	 * 
	 * <p>Processing steps:</p>
	 * <ol>
	 *   <li>Parse SQL into AST</li>
	 *   <li>Verify it's a SELECT statement</li>
	 *   <li>De-tokenize table/column tokens (if catalog has tokenization enabled)</li>
	 *   <li>Substitute synonyms with canonical names</li>
	 *   <li>Validate schema references</li>
	 * </ol>
	 * 
	 * <p>If the SQL uses tokens (when the catalog has tokenization enabled), the framework
	 * will automatically replace tokens with canonical names. If the SQL uses synonyms,
	 * they will be substituted with canonical names from the catalog.</p>
	 * 
	 * @param sql the SQL string to parse
	 * @param catalog optional schema catalog for table/column validation (may be null)
	 * @return a validated Query object
	 * @throws QueryValidationException if SQL is invalid, not a SELECT, or references invalid schema objects
	 */
	public static Query fromSql(String sql, SqlCatalog catalog) {
		if (sql == null || sql.isBlank()) {
			throw new QueryValidationException("SQL string cannot be null or blank");
		}

		// 1. Parse the SQL once - we'll reuse this AST for all processing
		Statement stmt;
		try {
			stmt = CCJSqlParserUtil.parse(sql);
		} catch (JSQLParserException e) {
			throw new QueryValidationException("Invalid SQL syntax: " + e.getMessage(), e);
		}

		// 2. Verify it's a SELECT statement only
		if (!(stmt instanceof Select selectStmt)) {
			throw new QueryValidationException(
					"Only SELECT statements are allowed, got: " + stmt.getClass().getSimpleName());
		}

		// 3. De-tokenize if catalog has tokenization enabled
		if (catalog != null && catalog.isTokenized()) {
			applyDeTokenization(selectStmt, catalog);
		}

		// 4. Apply synonym substitution on the AST (modifies in place)
		if (catalog != null && !catalog.tables().isEmpty()) {
			applySynonymSubstitution(selectStmt, catalog);
		}

		// 5. Validate schema references (after de-tokenization and synonym substitution)
		if (catalog != null && !catalog.tables().isEmpty()) {
			validateSchemaReferences(selectStmt, catalog);
		}

		return new Query(selectStmt, catalog);
	}

	/**
	 * Creates a Query from a raw SQL string without schema validation.
	 * 
	 * @param sql the SQL string to parse
	 * @return a validated Query object
	 * @throws QueryValidationException if SQL is invalid or not a SELECT
	 */
	public static Query fromSql(String sql) {
		return fromSql(sql, null);
	}

	/**
	 * Returns the SQL string using the catalog's configured dialect.
	 * 
	 * <p>If this Query was created with a {@link SqlCatalog}, the catalog's
	 * {@link SqlCatalog#dialect()} is used. Otherwise, defaults to ANSI.</p>
	 * 
	 * @return the SQL string in the appropriate dialect
	 */
	public String sqlString() {
		Dialect effectiveDialect = (catalog != null) ? catalog.dialect() : Dialect.ANSI;
		return sqlString(effectiveDialect);
	}

	/**
	 * Returns the SQL string, optionally transformed for a specific dialect.
	 * 
	 * @param dialect the target SQL dialect
	 * @return the SQL string in the specified dialect
	 */
	public String sqlString(Dialect dialect) {
		return switch (dialect) {
			case ANSI -> select.toString();
			case POSTGRES -> toPostgres(select);
		};
	}

	/**
	 * Returns the SQL string with tokens instead of canonical names.
	 * 
	 * <p>This is useful for debugging what the LLM should produce when 
	 * tokenization is enabled. The returned SQL will have table and column
	 * names replaced with their tokens.</p>
	 * 
	 * <p>If the catalog does not have tokenization enabled, returns the 
	 * normal SQL string (same as {@link #sqlString()}).</p>
	 * 
	 * @return the SQL string with tokens
	 */
	public String tokenizedSql() {
		if (catalog == null || !catalog.isTokenized()) {
			return sqlString();
		}
		
		// Create a string-based replacement since we want to show what the LLM would see
		String sql = sqlString();
		
		// Replace table names with tokens (longest first to avoid partial matches)
		java.util.List<String> tableNames = catalog.tables().keySet().stream()
				.sorted((a, b) -> Integer.compare(b.length(), a.length()))
				.toList();
		
		for (String tableName : tableNames) {
			String token = catalog.getTableToken(tableName).orElse(tableName);
			// Replace as a whole word (case-insensitive)
			sql = sql.replaceAll("(?i)\\b" + java.util.regex.Pattern.quote(tableName) + "\\b", token);
		}
		
		// Replace column names with tokens (per table)
		for (var entry : catalog.tables().entrySet()) {
			String tableName = entry.getKey();
			var table = entry.getValue();
			if (table.columns() != null) {
				// Sort by length descending
				java.util.List<String> columnNames = table.columns().stream()
						.map(SqlCatalog.SqlColumn::name)
						.sorted((a, b) -> Integer.compare(b.length(), a.length()))
						.toList();
				
				for (String columnName : columnNames) {
					String token = catalog.getColumnToken(tableName, columnName).orElse(columnName);
					// Replace as a whole word (case-insensitive)
					sql = sql.replaceAll("(?i)\\b" + java.util.regex.Pattern.quote(columnName) + "\\b", token);
				}
			}
		}
		
		return sql;
	}

	/**
	 * De-tokenizes table and column tokens back to their canonical names.
	 * 
	 * <p>This is called when the catalog has tokenization enabled and the LLM returns
	 * SQL with tokens instead of real names (e.g., "SELECT c_abc123 FROM ft_def456").</p>
	 * 
	 * @param select the parsed SELECT statement (modified in place)
	 * @param catalog the catalog containing token mappings
	 */
	private static void applyDeTokenization(Select select, SqlCatalog catalog) {
		if (select.getPlainSelect() == null) {
			return;
		}
		
		PlainSelect plainSelect = select.getPlainSelect();
		
		// First pass: de-tokenize table names and collect alias -> canonical table mappings
		Map<String, String> aliasToCanonicalTable = new HashMap<>();
		deTokenizeTableNames(plainSelect, catalog, aliasToCanonicalTable);
		
		// Second pass: de-tokenize column names
		deTokenizeColumnNames(plainSelect, catalog, aliasToCanonicalTable);
	}

	/**
	 * De-tokenizes table names in FROM and JOIN clauses.
	 */
	private static void deTokenizeTableNames(PlainSelect plainSelect, SqlCatalog catalog,
			Map<String, String> aliasToCanonicalTable) {
		
		// FROM clause
		if (plainSelect.getFromItem() instanceof Table fromTable) {
			String deTokenized = deTokenizeTableName(fromTable.getName(), catalog);
			if (deTokenized != null) {
				fromTable.setName(deTokenized);
			}
			String tableName = fromTable.getName();
			String alias = (fromTable.getAlias() != null) 
					? fromTable.getAlias().getName().toLowerCase() 
					: tableName.toLowerCase();
			aliasToCanonicalTable.put(alias, tableName);
		}
		
		// JOINs
		if (plainSelect.getJoins() != null) {
			for (Join join : plainSelect.getJoins()) {
				if (join.getRightItem() instanceof Table joinTable) {
					String deTokenized = deTokenizeTableName(joinTable.getName(), catalog);
					if (deTokenized != null) {
						joinTable.setName(deTokenized);
					}
					String tableName = joinTable.getName();
					String alias = (joinTable.getAlias() != null) 
							? joinTable.getAlias().getName().toLowerCase() 
							: tableName.toLowerCase();
					aliasToCanonicalTable.put(alias, tableName);
				}
			}
		}
	}

	/**
	 * De-tokenizes a single table name if it's a token.
	 * 
	 * <p>Tokens can be either:</p>
	 * <ul>
	 *   <li>A synonym (e.g., "orders" for "fct_orders") - more readable</li>
	 *   <li>A cryptic hash (e.g., "ft_abc123") - when no synonyms defined</li>
	 * </ul>
	 * 
	 * @return the canonical name if input was a token, null otherwise
	 */
	private static String deTokenizeTableName(String tableRef, SqlCatalog catalog) {
		if (tableRef == null) {
			return null;
		}
		// Let the catalog determine if this is a token - it knows its own mappings
		return catalog.resolveTableToken(tableRef).orElse(null);
	}

	/**
	 * De-tokenizes column names in SELECT, WHERE, and JOIN ON clauses.
	 */
	private static void deTokenizeColumnNames(PlainSelect plainSelect, SqlCatalog catalog,
			Map<String, String> aliasToCanonicalTable) {
		
		// SELECT items
		if (plainSelect.getSelectItems() != null) {
			for (SelectItem<?> item : plainSelect.getSelectItems()) {
				if (item.getExpression() instanceof Column col) {
					deTokenizeColumn(col, catalog, aliasToCanonicalTable);
				}
			}
		}
		
		// WHERE clause
		if (plainSelect.getWhere() != null) {
			deTokenizeColumnsInExpression(plainSelect.getWhere(), catalog, aliasToCanonicalTable);
		}
		
		// JOIN ON conditions
		if (plainSelect.getJoins() != null) {
			for (Join join : plainSelect.getJoins()) {
				if (join.getOnExpressions() != null) {
					for (Expression onExpr : join.getOnExpressions()) {
						deTokenizeColumnsInExpression(onExpr, catalog, aliasToCanonicalTable);
					}
				}
			}
		}
	}

	/**
	 * De-tokenizes columns in an expression tree.
	 */
	private static void deTokenizeColumnsInExpression(Expression expr, SqlCatalog catalog,
			Map<String, String> aliasToCanonicalTable) {
		if (expr instanceof Column col) {
			deTokenizeColumn(col, catalog, aliasToCanonicalTable);
		} else if (expr instanceof BinaryExpression binExpr) {
			deTokenizeColumnsInExpression(binExpr.getLeftExpression(), catalog, aliasToCanonicalTable);
			deTokenizeColumnsInExpression(binExpr.getRightExpression(), catalog, aliasToCanonicalTable);
		}
	}

	/**
	 * De-tokenizes a single column if its name is a token.
	 * 
	 * <p>Column tokens can be either synonyms or cryptic hashes.
	 * The catalog is consulted to determine if the column name is a token.</p>
	 */
	private static void deTokenizeColumn(Column col, SqlCatalog catalog,
			Map<String, String> aliasToCanonicalTable) {
		String columnName = col.getColumnName();
		if (columnName == null) {
			return;
		}
		
		// Determine which table this column belongs to
		String tableRef = (col.getTable() != null && col.getTable().getName() != null)
				? col.getTable().getName().toLowerCase()
				: null;
		
		// Get the canonical table name (either from alias or by resolving token)
		String canonicalTable = null;
		if (tableRef != null) {
			// First check if tableRef is an alias
			canonicalTable = aliasToCanonicalTable.get(tableRef);
			if (canonicalTable == null) {
				// tableRef might be a token itself (shouldn't happen after deTokenizeTableNames, but be defensive)
				canonicalTable = catalog.resolveTableToken(tableRef).orElse(null);
			}
		}
		
		// Find the table token for this canonical table and try to resolve column token
		if (canonicalTable != null) {
			String tableToken = catalog.getTableToken(canonicalTable).orElse(null);
			if (tableToken != null) {
				String resolved = catalog.resolveColumnToken(tableToken, columnName).orElse(null);
				if (resolved != null) {
					col.setColumnName(resolved);
				}
			}
		} else {
			// Unqualified column - try all tables
			for (String tableName : aliasToCanonicalTable.values()) {
				String tableToken = catalog.getTableToken(tableName).orElse(null);
				if (tableToken != null) {
					String resolved = catalog.resolveColumnToken(tableToken, columnName).orElse(null);
					if (resolved != null) {
						col.setColumnName(resolved);
						break;
					}
				}
			}
		}
	}

	/**
	 * Applies synonym substitution by walking the AST and modifying Table and Column nodes in place.
	 * 
	 * <p>This approach is safer than string replacement because it only modifies actual table and column
	 * references, not SQL keywords (like ORDER BY) or string literals.</p>
	 * 
	 * @param select the parsed SELECT statement (modified in place)
	 * @param catalog the catalog containing synonym mappings
	 */
	private static void applySynonymSubstitution(Select select, SqlCatalog catalog) {
		if (select.getPlainSelect() == null) {
			return;
		}
		
		PlainSelect plainSelect = select.getPlainSelect();
		
		// Build a map of table synonyms for quick lookup (case-insensitive)
		Map<String, String> tableSynonymMap = buildTableSynonymMap(catalog);
		
		// First pass: substitute table names and collect alias mappings
		Map<String, String> aliasToCanonicalTable = new HashMap<>();
		substituteTableNames(plainSelect, tableSynonymMap, aliasToCanonicalTable);
		
		// Build column synonym maps per table
		Map<String, Map<String, String>> columnSynonymMaps = buildColumnSynonymMaps(catalog);
		
		// Second pass: substitute column names
		substituteColumnNames(plainSelect, aliasToCanonicalTable, columnSynonymMaps);
	}

	/**
	 * Builds a map of table synonyms (lowercase synonym -> canonical name).
	 */
	private static Map<String, String> buildTableSynonymMap(SqlCatalog catalog) {
		Map<String, String> map = new HashMap<>();
		for (SqlCatalog.SqlTable table : catalog.tables().values()) {
			if (table.synonyms() != null) {
				for (String synonym : table.synonyms()) {
					map.put(synonym.toLowerCase(), table.name());
				}
			}
		}
		return map;
	}

	/**
	 * Builds column synonym maps for each table (table name -> (lowercase synonym -> canonical column name)).
	 */
	private static Map<String, Map<String, String>> buildColumnSynonymMaps(SqlCatalog catalog) {
		Map<String, Map<String, String>> result = new HashMap<>();
		for (SqlCatalog.SqlTable table : catalog.tables().values()) {
			Map<String, String> columnMap = new HashMap<>();
			if (table.columns() != null) {
				for (SqlCatalog.SqlColumn column : table.columns()) {
					if (column.synonyms() != null) {
						for (String synonym : column.synonyms()) {
							columnMap.put(synonym.toLowerCase(), column.name());
						}
					}
				}
			}
			if (!columnMap.isEmpty()) {
				result.put(table.name(), columnMap);
			}
		}
		return result;
	}

	/**
	 * Substitutes table names in the AST, modifying Table nodes in place.
	 * Also builds a map of aliases to canonical table names for column substitution.
	 */
	private static void substituteTableNames(PlainSelect plainSelect, 
			Map<String, String> tableSynonymMap, Map<String, String> aliasToCanonicalTable) {
		
		// FROM clause
		if (plainSelect.getFromItem() instanceof Table fromTable) {
			String canonical = substituteTableName(fromTable, tableSynonymMap);
			String alias = (fromTable.getAlias() != null) 
					? fromTable.getAlias().getName().toLowerCase() 
					: canonical.toLowerCase();
			aliasToCanonicalTable.put(alias, canonical);
		}
		
		// JOINs
		if (plainSelect.getJoins() != null) {
			for (Join join : plainSelect.getJoins()) {
				if (join.getRightItem() instanceof Table joinTable) {
					String canonical = substituteTableName(joinTable, tableSynonymMap);
					String alias = (joinTable.getAlias() != null) 
							? joinTable.getAlias().getName().toLowerCase() 
							: canonical.toLowerCase();
					aliasToCanonicalTable.put(alias, canonical);
				}
			}
		}
	}

	/**
	 * Substitutes a single table name if it matches a synonym.
	 * @return the canonical table name (whether substituted or original)
	 */
	private static String substituteTableName(Table table, Map<String, String> tableSynonymMap) {
		String original = table.getName();
		String canonical = tableSynonymMap.get(original.toLowerCase());
		if (canonical != null) {
			table.setName(canonical);
			return canonical;
		}
		return original;
	}

	/**
	 * Substitutes column names in the AST, modifying Column nodes in place.
	 */
	private static void substituteColumnNames(PlainSelect plainSelect,
			Map<String, String> aliasToCanonicalTable, Map<String, Map<String, String>> columnSynonymMaps) {
		
		// SELECT items
		if (plainSelect.getSelectItems() != null) {
			for (SelectItem<?> item : plainSelect.getSelectItems()) {
				if (item.getExpression() instanceof Column col) {
					substituteColumnName(col, aliasToCanonicalTable, columnSynonymMaps);
				}
			}
		}
		
		// WHERE clause
		if (plainSelect.getWhere() != null) {
			substituteColumnsInExpression(plainSelect.getWhere(), aliasToCanonicalTable, columnSynonymMaps);
		}
		
		// JOIN ON conditions
		if (plainSelect.getJoins() != null) {
			for (Join join : plainSelect.getJoins()) {
				if (join.getOnExpressions() != null) {
					for (Expression onExpr : join.getOnExpressions()) {
						substituteColumnsInExpression(onExpr, aliasToCanonicalTable, columnSynonymMaps);
					}
				}
			}
		}
	}

	/**
	 * Substitutes column names in an expression tree.
	 */
	private static void substituteColumnsInExpression(Expression expr,
			Map<String, String> aliasToCanonicalTable, Map<String, Map<String, String>> columnSynonymMaps) {
		if (expr instanceof Column col) {
			substituteColumnName(col, aliasToCanonicalTable, columnSynonymMaps);
		} else if (expr instanceof BinaryExpression binExpr) {
			substituteColumnsInExpression(binExpr.getLeftExpression(), aliasToCanonicalTable, columnSynonymMaps);
			substituteColumnsInExpression(binExpr.getRightExpression(), aliasToCanonicalTable, columnSynonymMaps);
		}
	}

	/**
	 * Substitutes a single column name if it matches a synonym.
	 */
	private static void substituteColumnName(Column col,
			Map<String, String> aliasToCanonicalTable, Map<String, Map<String, String>> columnSynonymMaps) {
		String columnName = col.getColumnName();
		
		// Determine which table this column belongs to
		String tableRef = (col.getTable() != null && col.getTable().getName() != null)
				? col.getTable().getName().toLowerCase()
				: null;
		
		// Find the canonical table name
		String canonicalTable = (tableRef != null) ? aliasToCanonicalTable.get(tableRef) : null;
		
		// If we know the table, look up column synonyms for that specific table
		if (canonicalTable != null) {
			Map<String, String> columnMap = columnSynonymMaps.get(canonicalTable);
			if (columnMap != null) {
				String canonical = columnMap.get(columnName.toLowerCase());
				if (canonical != null) {
					col.setColumnName(canonical);
				}
			}
		} else {
			// Unqualified column - check all tables
			for (Map.Entry<String, Map<String, String>> entry : columnSynonymMaps.entrySet()) {
				String canonical = entry.getValue().get(columnName.toLowerCase());
				if (canonical != null) {
					col.setColumnName(canonical);
					break; // First match wins
				}
			}
		}
	}

	/**
	 * Validates that the SELECT statement only references tables and columns in the catalog.
	 */
	private static void validateSchemaReferences(Select select, SqlCatalog catalog) {
		TablesNamesFinder tablesFinder = new TablesNamesFinder();
		// Cast to Statement to resolve method ambiguity in JSqlParser
		Set<String> tables = tablesFinder.getTables((Statement) select);

		// Build a map of table aliases to canonical table names
		Map<String, String> aliasToTable = new HashMap<>();
		for (String table : tables) {
			String tableName = extractTableName(table).orElse(table);
			if (!catalog.tables().containsKey(tableName)) {
				throw new QueryValidationException("Unknown table: " + tableName + 
						". Available tables: " + catalog.tables().keySet());
			}
			// Map both the canonical name and any aliases found
			aliasToTable.put(tableName.toLowerCase(), tableName);
		}

		// Extract aliases from the parsed SELECT statement
		extractTableAliases(select, aliasToTable);

		// Extract and validate column references (only if enabled on catalog)
		if (catalog.validateColumns()) {
			java.util.ArrayList<Column> columns = new java.util.ArrayList<>();
			extractColumns(select, columns);
			validateColumns(columns, catalog, aliasToTable);
		}
	}

	/**
	 * Extracts table aliases from the SELECT statement.
	 */
	private static void extractTableAliases(Select select, Map<String, String> aliasToTable) {
		if (select.getPlainSelect() != null) {
			PlainSelect plainSelect = select.getPlainSelect();
			
			// FROM clause
			if (plainSelect.getFromItem() instanceof Table fromTable) {
				if (fromTable.getAlias() != null) {
					aliasToTable.put(fromTable.getAlias().getName().toLowerCase(), fromTable.getName());
				}
			}
			
			// JOINs
			if (plainSelect.getJoins() != null) {
				for (Join join : plainSelect.getJoins()) {
					if (join.getRightItem() instanceof Table joinTable) {
						if (joinTable.getAlias() != null) {
							aliasToTable.put(joinTable.getAlias().getName().toLowerCase(), joinTable.getName());
						}
					}
				}
			}
		}
	}

	/**
	 * Extracts column references from a SELECT statement.
	 */
	private static void extractColumns(Select select, java.util.ArrayList<Column> columns) {
		if (select.getPlainSelect() != null) {
			PlainSelect plainSelect = select.getPlainSelect();
			
			// SELECT items
			if (plainSelect.getSelectItems() != null) {
				for (SelectItem<?> item : plainSelect.getSelectItems()) {
					if (item.getExpression() instanceof Column col) {
						columns.add(col);
					}
				}
			}
			
			// WHERE clause
			if (plainSelect.getWhere() != null) {
				collectColumnsFromExpression(plainSelect.getWhere(), columns);
			}
			
			// JOIN ON conditions
			if (plainSelect.getJoins() != null) {
				for (Join join : plainSelect.getJoins()) {
					if (join.getOnExpressions() != null) {
						for (Expression onExpr : join.getOnExpressions()) {
							collectColumnsFromExpression(onExpr, columns);
						}
					}
				}
			}
		}
	}

	/**
	 * Validates column references against the catalog.
	 */
	private static void validateColumns(java.util.ArrayList<Column> columns,
			SqlCatalog catalog, Map<String, String> aliasToTable) {
		for (Column col : columns) {
			String columnName = col.getColumnName();
			Table table = col.getTable();
			
			if (table != null && table.getName() != null) {
				// Qualified column reference (e.g., t.column_name or table.column_name)
				String tableRef = table.getName().toLowerCase();
				String actualTableName = aliasToTable.get(tableRef);
				if (actualTableName != null) {
					SqlCatalog.SqlTable catalogTable = catalog.tables().get(actualTableName);
					if (catalogTable != null && !columnExistsInTable(catalogTable, columnName)) {
						throw new QueryValidationException(
								"Unknown column '%s' in table '%s'. Available columns: %s"
										.formatted(columnName, actualTableName, getColumnNames(catalogTable)));
					}
				}
			} else {
				// Unqualified column reference - check all tables in query
				boolean found = false;
				for (String actualTableName : aliasToTable.values()) {
					SqlCatalog.SqlTable catalogTable = catalog.tables().get(actualTableName);
					if (catalogTable != null && columnExistsInTable(catalogTable, columnName)) {
						found = true;
						break;
					}
				}
				if (!found && !aliasToTable.isEmpty()) {
					throw new QueryValidationException(
							"Unknown column '%s'. Not found in any table: %s"
									.formatted(columnName, aliasToTable.values()));
				}
			}
		}
	}

	/**
	 * Recursively collects Column nodes from an expression.
	 */
	private static void collectColumnsFromExpression(Expression expr, java.util.ArrayList<Column> columns) {
		if (expr instanceof Column col) {
			columns.add(col);
		} else if (expr instanceof BinaryExpression binExpr) {
			collectColumnsFromExpression(binExpr.getLeftExpression(), columns);
			collectColumnsFromExpression(binExpr.getRightExpression(), columns);
		}
	}

	/**
	 * Checks if a column exists in a table (by name or synonym).
	 */
	private static boolean columnExistsInTable(SqlCatalog.SqlTable table, String columnName) {
		return table.findColumn(columnName).isPresent();
	}

	/**
	 * Gets the list of column names for error messages.
	 */
	private static List<String> getColumnNames(SqlCatalog.SqlTable table) {
		return table.columns().stream().map(SqlCatalog.SqlColumn::name).toList();
	}

	/**
	 * Extracts the table name from a potentially aliased table reference.
	 */
	private static Optional<String> extractTableName(String tableRef) {
		if (tableRef == null || tableRef.isBlank()) {
			return Optional.empty();
		}
		// TablesNamesFinder returns just the table name, not aliases
		return Optional.of(tableRef.trim());
	}

	/**
	 * Converts a SELECT statement to PostgreSQL dialect.
	 * Currently returns the default toString() which is largely ANSI-compatible.
	 * Can be extended for PostgreSQL-specific transformations as needed.
	 */
	private static String toPostgres(Select select) {
		// JSqlParser's default toString is largely ANSI SQL which PostgreSQL supports.
		// For PostgreSQL-specific features (e.g., ILIKE), we could use a custom visitor.
		return select.toString();
	}
}
