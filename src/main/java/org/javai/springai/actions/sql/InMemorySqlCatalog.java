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

	public InMemorySqlCatalog addTable(String tableName, String description, String... tags) {
		if (tableName == null || tableName.isBlank()) {
			return this;
		}
		tables.putIfAbsent(tableName, new TableBuilder(tableName, description, tags));
		return this;
	}

	public InMemorySqlCatalog addColumn(String tableName, String columnName, String description, String dataType,
			String[] tags, String[] constraints) {
		if (tableName == null || tableName.isBlank() || columnName == null || columnName.isBlank()) {
			return this;
		}
		TableBuilder table = tables.computeIfAbsent(tableName, t -> new TableBuilder(tableName, null, null));
		table.columns.add(new SqlCatalog.SqlColumn(
				columnName,
				description,
				dataType,
				tags != null ? List.of(tags) : EMPTY,
				constraints != null ? List.of(constraints) : EMPTY
		));
		return this;
	}

	@Override
	public Map<String, SqlTable> tables() {
		Map<String, SqlTable> copy = new LinkedHashMap<>();
		for (TableBuilder builder : tables.values()) {
			copy.put(builder.name, builder.build());
		}
		return Collections.unmodifiableMap(copy);
	}

	private record TableBuilder(String name, String description, List<String> tags, List<SqlCatalog.SqlColumn> columns,
			List<String> constraints) {

		TableBuilder(String name, String description, String[] tags) {
			this(name, description, tags != null ? List.of(tags) : EMPTY, new ArrayList<>(), EMPTY);
		}

		SqlTable build() {
			return new SqlTable(name, description, List.copyOf(columns), tags, constraints);
		}
	}
}

