package org.javai.springai.actions.sql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 */
public final class InMemorySqlCatalog implements SqlCatalog {

	private static final List<String> EMPTY = List.of();

	private final Map<String, TableBuilder> tables = new LinkedHashMap<>();
	private Query.Dialect dialect = Query.Dialect.ANSI;
	private boolean validateColumns = false;

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

	public InMemorySqlCatalog addTable(String tableName, String description, String... tags) {
		if (tableName == null || tableName.isBlank()) {
			return this;
		}
		tables.putIfAbsent(tableName, new TableBuilder(tableName, description, tags));
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
		String lowerSynonym = synonym.toLowerCase();
		
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

