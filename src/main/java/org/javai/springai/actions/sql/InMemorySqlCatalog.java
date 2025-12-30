package org.javai.springai.actions.sql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory SqlCatalog implementation for tests or simple configurations.
 * 
 * <p>Supports fluent configuration of tables, columns, and the target SQL dialect:</p>
 * 
 * <pre>{@code
 * SqlCatalog catalog = new InMemorySqlCatalog()
 *     .withDialect(Query.Dialect.POSTGRES)
 *     .addTable("orders", "Order fact table", "fact")
 *     .addColumn("orders", "id", "Primary key", "bigint", new String[]{"pk"}, null);
 * }</pre>
 * 
 * <h2>Tokenization</h2>
 * 
 * <p>When tokenization is enabled, the catalog generates opaque tokens for table
 * and column names. This prevents exposure of real schema names to external LLMs:</p>
 * 
 * <pre>{@code
 * SqlCatalog catalog = new InMemorySqlCatalog()
 *     .withTokenization(true)
 *     .addTable("fct_orders", "Order transactions", "fact")
 *     .addColumn("fct_orders", "customer_id", "FK to customers", "integer", null, null);
 * 
 * // Tokens: ft_abc123, c_def456 (hides real names from LLM)
 * }</pre>
 */
public final class InMemorySqlCatalog implements SqlCatalog {

	private static final List<String> EMPTY = List.of();

	private final Map<String, TableBuilder> tables = new LinkedHashMap<>();
	private Query.Dialect dialect = Query.Dialect.ANSI;
	private boolean validateColumns = false;
	private boolean tokenizationEnabled = false;

	// Token mappings (lazily populated when tokenization is enabled)
	private Map<String, String> tableNameToToken = null;  // tableName -> token
	private Map<String, String> tableTokenToName = null;  // token -> tableName
	private Map<String, String> columnNameToToken = null; // "table.column" -> token
	private Map<String, String> columnTokenToName = null; // "tableToken.columnToken" -> columnName

	/**
	 * Sets the target SQL dialect for queries using this catalog.
	 * 
	 * <p>When a {@link Query} is created with this catalog, calling {@link Query#sqlString()}
	 * without arguments will return SQL formatted for this dialect.</p>
	 * 
	 * @param dialect the target SQL dialect
	 * @return this catalog for fluent chaining
	 */
	public InMemorySqlCatalog withDialect(Query.Dialect dialect) {
		this.dialect = dialect != null ? dialect : Query.Dialect.ANSI;
		return this;
	}

	@Override
	public Query.Dialect dialect() {
		return dialect;
	}

	/**
	 * Enables or disables column-level validation for queries using this catalog.
	 * 
	 * <p>When enabled, {@link Query#fromSql(String, SqlCatalog)} will validate that
	 * all column references in the SQL exist in the catalog. This is off by default
	 * for backward compatibility with LLM-generated SQL.</p>
	 * 
	 * @param validateColumns true to enable column validation
	 * @return this catalog for fluent chaining
	 */
	public InMemorySqlCatalog withValidateColumns(boolean validateColumns) {
		this.validateColumns = validateColumns;
		return this;
	}

	@Override
	public boolean validateColumns() {
		return validateColumns;
	}

	/**
	 * Enables or disables tokenization for this catalog.
	 * 
	 * <p>When enabled, the catalog will generate opaque tokens for all table and
	 * column names. These tokens can be used in system prompts to hide real schema
	 * names from external LLMs. The framework will automatically de-tokenize
	 * LLM-generated SQL before validation and execution.</p>
	 * 
	 * <p>Tokenization must be enabled <b>before</b> adding tables and columns.
	 * Token mappings are generated lazily when first accessed.</p>
	 * 
	 * @param enabled true to enable tokenization
	 * @return this catalog for fluent chaining
	 */
	public InMemorySqlCatalog withTokenization(boolean enabled) {
		this.tokenizationEnabled = enabled;
		// Clear any existing token mappings when toggling
		this.tableNameToToken = null;
		this.tableTokenToName = null;
		this.columnNameToToken = null;
		this.columnTokenToName = null;
		return this;
	}

	@Override
	public boolean isTokenized() {
		return tokenizationEnabled;
	}

	@Override
	public Optional<String> getTableToken(String tableName) {
		if (!tokenizationEnabled || tableName == null) {
			return Optional.empty();
		}
		ensureTokenMappingsBuilt();
		return Optional.ofNullable(tableNameToToken.get(tableName));
	}

	@Override
	public Optional<String> getColumnToken(String tableName, String columnName) {
		if (!tokenizationEnabled || tableName == null || columnName == null) {
			return Optional.empty();
		}
		ensureTokenMappingsBuilt();
		String key = tableName + "." + columnName;
		return Optional.ofNullable(columnNameToToken.get(key));
	}

	@Override
	public Optional<String> resolveTableToken(String token) {
		if (!tokenizationEnabled || token == null) {
			return Optional.empty();
		}
		ensureTokenMappingsBuilt();
		return Optional.ofNullable(tableTokenToName.get(token));
	}

	@Override
	public Optional<String> resolveColumnToken(String tableToken, String columnToken) {
		if (!tokenizationEnabled || tableToken == null || columnToken == null) {
			return Optional.empty();
		}
		ensureTokenMappingsBuilt();
		String key = tableToken + "." + columnToken;
		return Optional.ofNullable(columnTokenToName.get(key));
	}

	@Override
	public Map<String, String> tokenMappings() {
		if (!tokenizationEnabled) {
			return Map.of();
		}
		ensureTokenMappingsBuilt();
		Map<String, String> all = new LinkedHashMap<>();
		all.putAll(tableTokenToName);
		// For column tokens, include table context
		columnTokenToName.forEach((key, value) -> {
			// key is "tableToken.columnToken", extract just the column token for display
			String[] parts = key.split("\\.", 2);
			if (parts.length == 2) {
				String tableToken = parts[0];
				String columnToken = parts[1];
				String tableName = tableTokenToName.get(tableToken);
				all.put(columnToken, tableName + "." + value);
			}
		});
		return Collections.unmodifiableMap(all);
	}

	/**
	 * Invalidates token mappings so they will be rebuilt on next access.
	 */
	private void invalidateTokenMappings() {
		tableNameToToken = null;
		tableTokenToName = null;
		columnNameToToken = null;
		columnTokenToName = null;
	}

	/**
	 * Lazily builds token mappings for all tables and columns.
	 * 
	 * <p>Token strategy:</p>
	 * <ul>
	 *   <li>If synonyms are defined, the first synonym becomes the token (more readable)</li>
	 *   <li>If no synonyms, falls back to cryptic hash-based token (full obfuscation)</li>
	 * </ul>
	 */
	private void ensureTokenMappingsBuilt() {
		if (tableNameToToken != null) {
			return; // Already built
		}

		tableNameToToken = new HashMap<>();
		tableTokenToName = new HashMap<>();
		columnNameToToken = new HashMap<>();
		columnTokenToName = new HashMap<>();

		for (TableBuilder table : tables.values()) {
			// Use first synonym as token if available, otherwise generate cryptic token
			String tableToken;
			if (!table.synonyms.isEmpty()) {
				tableToken = table.synonyms.get(0);  // First synonym is the token
			} else {
				String[] tagsArray = table.tags.toArray(new String[0]);
				tableToken = TokenGenerator.tableToken(table.name, tagsArray);  // Cryptic fallback
			}
			tableNameToToken.put(table.name, tableToken);
			tableTokenToName.put(tableToken, table.name);

			// Generate column tokens (same strategy)
			for (ColumnBuilder column : table.columns.values()) {
				String columnToken;
				if (!column.synonyms.isEmpty()) {
					columnToken = column.synonyms.get(0);  // First synonym is the token
				} else {
					columnToken = TokenGenerator.columnToken(table.name, column.name);  // Cryptic fallback
				}
				String nameKey = table.name + "." + column.name;
				String tokenKey = tableToken + "." + columnToken;
				columnNameToToken.put(nameKey, columnToken);
				columnTokenToName.put(tokenKey, column.name);
			}
		}
	}

	public InMemorySqlCatalog addTable(String tableName, String description, String... tags) {
		if (tableName == null || tableName.isBlank()) {
			return this;
		}
		tables.putIfAbsent(tableName, new TableBuilder(tableName, description, tags));
		invalidateTokenMappings();
		return this;
	}

	/**
	 * Adds synonyms (alternative names) for a table.
	 * 
	 * <p>Synonyms allow the framework to automatically map informal table names 
	 * (e.g., "orders", "customers") to canonical catalog names (e.g., "fct_orders", 
	 * "dim_customer") without requiring an LLM retry.</p>
	 * 
	 * <p>Example:</p>
	 * <pre>{@code
	 * catalog.addTable("fct_orders", "Order transactions", "fact")
	 *        .withSynonyms("fct_orders", "orders", "order", "sales");
	 * }</pre>
	 * 
	 * @param tableName the canonical table name (must already exist in catalog)
	 * @param synonyms alternative names that should map to this table
	 * @return this catalog for fluent chaining
	 */
	public InMemorySqlCatalog withSynonyms(String tableName, String... synonyms) {
		if (tableName == null || tableName.isBlank() || synonyms == null || synonyms.length == 0) {
			return this;
		}
		TableBuilder builder = tables.get(tableName);
		if (builder != null) {
			for (String synonym : synonyms) {
				if (synonym != null && !synonym.isBlank()) {
					validateSynonymUniqueness(synonym, tableName);
					builder.synonyms.add(synonym);
				}
			}
		}
		return this;
	}

	/**
	 * Validates that a synonym is not already in use by another table (as a name or synonym).
	 */
	private void validateSynonymUniqueness(String synonym, String targetTable) {
		// Check if synonym matches any existing table name
		for (String existingTableName : tables.keySet()) {
			if (existingTableName.equalsIgnoreCase(synonym) && !existingTableName.equals(targetTable)) {
				throw new IllegalArgumentException(
						"Synonym '%s' conflicts with existing table name '%s'"
								.formatted(synonym, existingTableName));
			}
		}
		
		// Check if synonym is already used by another table
		for (TableBuilder existingTable : tables.values()) {
			if (existingTable.name.equals(targetTable)) {
				continue; // Skip the table we're adding synonyms to
			}
			for (String existingSynonym : existingTable.synonyms) {
				if (existingSynonym.equalsIgnoreCase(synonym)) {
					throw new IllegalArgumentException(
							"Synonym '%s' is already defined for table '%s'"
									.formatted(synonym, existingTable.name));
				}
			}
		}
	}

	public InMemorySqlCatalog addColumn(String tableName, String columnName, String description, String dataType,
			String[] tags, String[] constraints) {
		if (tableName == null || tableName.isBlank() || columnName == null || columnName.isBlank()) {
			return this;
		}
		TableBuilder table = tables.computeIfAbsent(tableName, t -> new TableBuilder(tableName, null, null));
		table.columns.put(columnName, new ColumnBuilder(columnName, description, dataType, tags, constraints));
		invalidateTokenMappings();
		return this;
	}

	/**
	 * Adds synonyms (alternative names) for a column.
	 * 
	 * <p>Synonyms allow the framework to automatically map informal column names 
	 * (e.g., "name", "value") to canonical catalog names (e.g., "customer_name", 
	 * "order_value") without requiring an LLM retry.</p>
	 * 
	 * <p>Example:</p>
	 * <pre>{@code
	 * catalog.addColumn("dim_customer", "customer_name", "Customer name", "varchar", null, null)
	 *        .withColumnSynonyms("dim_customer", "customer_name", "name", "cust_name");
	 * }</pre>
	 * 
	 * @param tableName the table containing the column
	 * @param columnName the canonical column name (must already exist in table)
	 * @param synonyms alternative names that should map to this column
	 * @return this catalog for fluent chaining
	 */
	public InMemorySqlCatalog withColumnSynonyms(String tableName, String columnName, String... synonyms) {
		if (tableName == null || tableName.isBlank() || columnName == null || columnName.isBlank() 
				|| synonyms == null || synonyms.length == 0) {
			return this;
		}
		TableBuilder table = tables.get(tableName);
		if (table == null) {
			return this;
		}
		ColumnBuilder column = table.columns.get(columnName);
		if (column != null) {
			for (String synonym : synonyms) {
				if (synonym != null && !synonym.isBlank()) {
					validateColumnSynonymUniqueness(table, synonym, columnName);
					column.synonyms.add(synonym);
				}
			}
		}
		return this;
	}

	/**
	 * Validates that a column synonym is not already in use by another column in the same table.
	 */
	private void validateColumnSynonymUniqueness(TableBuilder table, String synonym, String targetColumn) {
		// Check if synonym matches any existing column name in this table
		for (String existingColumnName : table.columns.keySet()) {
			if (existingColumnName.equalsIgnoreCase(synonym) && !existingColumnName.equals(targetColumn)) {
				throw new IllegalArgumentException(
						"Column synonym '%s' conflicts with existing column name '%s' in table '%s'"
								.formatted(synonym, existingColumnName, table.name));
			}
		}
		
		// Check if synonym is already used by another column in this table
		for (ColumnBuilder existingColumn : table.columns.values()) {
			if (existingColumn.name.equals(targetColumn)) {
				continue; // Skip the column we're adding synonyms to
			}
			for (String existingSynonym : existingColumn.synonyms) {
				if (existingSynonym.equalsIgnoreCase(synonym)) {
					throw new IllegalArgumentException(
							"Column synonym '%s' is already defined for column '%s' in table '%s'"
									.formatted(synonym, existingColumn.name, table.name));
				}
			}
		}
	}

	@Override
	public Map<String, SqlTable> tables() {
		Map<String, SqlTable> copy = new LinkedHashMap<>();
		for (TableBuilder builder : tables.values()) {
			copy.put(builder.name, builder.build());
		}
		return Collections.unmodifiableMap(copy);
	}

	private static final class TableBuilder {
		private final String name;
		private final String description;
		private final List<String> tags;
		private final Map<String, ColumnBuilder> columns;
		private final List<String> constraints;
		private final List<String> synonyms;

		TableBuilder(String name, String description, String[] tags) {
			this.name = name;
			this.description = description;
			this.tags = tags != null ? List.of(tags) : EMPTY;
			this.columns = new LinkedHashMap<>();
			this.constraints = EMPTY;
			this.synonyms = new ArrayList<>();
		}

		SqlTable build() {
			List<SqlCatalog.SqlColumn> builtColumns = columns.values().stream()
					.map(ColumnBuilder::build)
					.toList();
			return new SqlTable(name, description, builtColumns, tags, constraints, List.copyOf(synonyms));
		}
	}

	private static final class ColumnBuilder {
		private final String name;
		private final String description;
		private final String dataType;
		private final List<String> tags;
		private final List<String> constraints;
		private final List<String> synonyms;

		ColumnBuilder(String name, String description, String dataType, String[] tags, String[] constraints) {
			this.name = name;
			this.description = description;
			this.dataType = dataType;
			this.tags = tags != null ? List.of(tags) : EMPTY;
			this.constraints = constraints != null ? List.of(constraints) : EMPTY;
			this.synonyms = new ArrayList<>();
		}

		SqlCatalog.SqlColumn build() {
			return new SqlCatalog.SqlColumn(name, description, dataType, tags, constraints, List.copyOf(synonyms));
		}
	}
}

