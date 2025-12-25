package org.javai.springai.dsl.prompt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory SqlCatalog implementation for tests or simple configurations.
 */
public final class InMemorySqlCatalog implements SqlCatalog {

	private static final List<String> EMPTY = List.of();

	private final Map<String, TableBuilder> tables = new LinkedHashMap<>();

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

