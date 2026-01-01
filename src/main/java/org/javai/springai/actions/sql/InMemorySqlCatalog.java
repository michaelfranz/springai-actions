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
 * <h2>Model Name Mapping</h2>
 * 
 * <p>When model name mapping is enabled, the catalog provides alternative names
 * for tables and columns. The LLM sees and generates SQL using these "model names"
 * (derived from synonyms or generated). The framework automatically resolves 
 * model names back to canonical names during query processing:</p>
 * 
 * <pre>{@code
 * SqlCatalog catalog = new InMemorySqlCatalog()
 *     .withModelNames(true)
 *     .addTable("fct_orders", "Order transactions", "fact")
 *     .withSynonyms("fct_orders", "orders")  // LLM sees "orders"
 *     .addColumn("fct_orders", "customer_id", "FK to customers", "integer", null, null);
 * }</pre>
 */
public final class InMemorySqlCatalog implements SqlCatalog {

	private static final List<String> EMPTY = List.of();

	private final Map<String, TableBuilder> tables = new LinkedHashMap<>();
	private Query.Dialect dialect = Query.Dialect.ANSI;
	private boolean validateColumns = false;
	private boolean modelNamesEnabled = false;

	// Model name mappings (lazily populated when model names are enabled)
	private Map<String, String> tableNameToModelName = null;  // tableName -> modelName
	private Map<String, String> modelNameToTableName = null;  // modelName -> tableName
	private Map<String, String> columnNameToModelName = null; // "table.column" -> modelName
	private Map<String, String> modelNameToColumnName = null; // "tableModelName.columnModelName" -> columnName

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
	 * Enables or disables model name mapping for this catalog.
	 * 
	 * <p>When enabled, the catalog provides model-facing names (derived from
	 * synonyms or generated) for all tables and columns. The LLM sees and 
	 * generates SQL using these model names. The framework automatically
	 * resolves model names back to canonical names during query processing.</p>
	 * 
	 * <p>Model name mapping must be enabled <b>before</b> adding tables and columns.
	 * Mappings are generated lazily when first accessed.</p>
	 * 
	 * @param enabled true to enable model name mapping
	 * @return this catalog for fluent chaining
	 */
	public InMemorySqlCatalog withModelNames(boolean enabled) {
		this.modelNamesEnabled = enabled;
		// Clear any existing mappings when toggling
		this.tableNameToModelName = null;
		this.modelNameToTableName = null;
		this.columnNameToModelName = null;
		this.modelNameToColumnName = null;
		return this;
	}

	@Override
	public boolean usesModelNames() {
		return modelNamesEnabled;
	}

	@Override
	public Optional<String> getTableModelName(String tableName) {
		if (!modelNamesEnabled || tableName == null) {
			return Optional.empty();
		}
		ensureModelNameMappingsBuilt();
		return Optional.ofNullable(tableNameToModelName.get(tableName));
	}

	@Override
	public Optional<String> getColumnModelName(String tableName, String columnName) {
		if (!modelNamesEnabled || tableName == null || columnName == null) {
			return Optional.empty();
		}
		ensureModelNameMappingsBuilt();
		String key = tableName + "." + columnName;
		return Optional.ofNullable(columnNameToModelName.get(key));
	}

	@Override
	public Optional<String> resolveTableFromModelName(String modelName) {
		if (!modelNamesEnabled || modelName == null) {
			return Optional.empty();
		}
		ensureModelNameMappingsBuilt();
		return Optional.ofNullable(modelNameToTableName.get(modelName));
	}

	@Override
	public Optional<String> resolveColumnFromModelName(String tableModelName, String columnModelName) {
		if (!modelNamesEnabled || tableModelName == null || columnModelName == null) {
			return Optional.empty();
		}
		ensureModelNameMappingsBuilt();
		String key = tableModelName + "." + columnModelName;
		return Optional.ofNullable(modelNameToColumnName.get(key));
	}

	@Override
	public Map<String, String> modelNameMappings() {
		if (!modelNamesEnabled) {
			return Map.of();
		}
		ensureModelNameMappingsBuilt();
		Map<String, String> all = new LinkedHashMap<>();
		all.putAll(modelNameToTableName);
		// For column model names, include table context
		modelNameToColumnName.forEach((key, value) -> {
			// key is "tableModelName.columnModelName", extract just the column model name for display
			String[] parts = key.split("\\.", 2);
			if (parts.length == 2) {
				String tableModelName = parts[0];
				String columnModelName = parts[1];
				String tableName = modelNameToTableName.get(tableModelName);
				all.put(columnModelName, tableName + "." + value);
			}
		});
		return Collections.unmodifiableMap(all);
	}

	/**
	 * Invalidates model name mappings so they will be rebuilt on next access.
	 */
	private void invalidateModelNameMappings() {
		tableNameToModelName = null;
		modelNameToTableName = null;
		columnNameToModelName = null;
		modelNameToColumnName = null;
	}

	/**
	 * Lazily builds model name mappings for all tables and columns.
	 * 
	 * <p>Model name strategy:</p>
	 * <ul>
	 *   <li>If synonyms are defined, the first synonym becomes the model name (more readable)</li>
	 *   <li>If no synonyms, falls back to generated identifier (full obfuscation)</li>
	 * </ul>
	 */
	private void ensureModelNameMappingsBuilt() {
		if (tableNameToModelName != null) {
			return; // Already built
		}

		tableNameToModelName = new HashMap<>();
		modelNameToTableName = new HashMap<>();
		columnNameToModelName = new HashMap<>();
		modelNameToColumnName = new HashMap<>();

		for (TableBuilder table : tables.values()) {
			// Use first synonym as model name if available, otherwise generate one
			String tableModelName;
			if (!table.synonyms.isEmpty()) {
				tableModelName = table.synonyms.get(0);  // First synonym is the model name
			} else {
				String[] tagsArray = table.tags.toArray(new String[0]);
				tableModelName = TokenGenerator.tableToken(table.name, tagsArray);  // Generated fallback
			}
			tableNameToModelName.put(table.name, tableModelName);
			modelNameToTableName.put(tableModelName, table.name);

			// Generate column model names (same strategy)
			for (ColumnBuilder column : table.columns.values()) {
				String columnModelName;
				if (!column.synonyms.isEmpty()) {
					columnModelName = column.synonyms.get(0);  // First synonym is the model name
				} else {
					columnModelName = TokenGenerator.columnToken(table.name, column.name);  // Generated fallback
				}
				String nameKey = table.name + "." + column.name;
				String modelKey = tableModelName + "." + columnModelName;
				columnNameToModelName.put(nameKey, columnModelName);
				modelNameToColumnName.put(modelKey, column.name);
			}
		}
	}

	public InMemorySqlCatalog addTable(String tableName, String description, String... tags) {
		if (tableName == null || tableName.isBlank()) {
			return this;
		}
		tables.putIfAbsent(tableName, new TableBuilder(tableName, description, tags));
		invalidateModelNameMappings();
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
		invalidateModelNameMappings();
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

