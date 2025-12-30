package org.javai.springai.actions.sql;

import java.util.Optional;
import java.util.Set;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
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
	 * @param sql the SQL string to parse
	 * @param catalog optional schema catalog for table/column validation (may be null)
	 * @return a validated Query object
	 * @throws QueryValidationException if SQL is invalid, not a SELECT, or references invalid schema objects
	 */
	public static Query fromSql(String sql, SqlCatalog catalog) {
		if (sql == null || sql.isBlank()) {
			throw new QueryValidationException("SQL string cannot be null or blank");
		}

		// 1. Parse the SQL
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

		// 3. Validate schema references if catalog is provided
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
	 * Validates that the SELECT statement only references tables in the catalog.
	 */
	private static void validateSchemaReferences(Select select, SqlCatalog catalog) {
		TablesNamesFinder tablesFinder = new TablesNamesFinder();
		// Cast to Statement to resolve method ambiguity in JSqlParser
		Set<String> tables = tablesFinder.getTables((Statement) select);

		for (String table : tables) {
			// Handle potential aliases (e.g., "orders o" -> "orders")
			String tableName = extractTableName(table).orElse(table);
			if (!catalog.tables().containsKey(tableName)) {
				throw new QueryValidationException("Unknown table: " + tableName + 
						". Available tables: " + catalog.tables().keySet());
			}
		}
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
