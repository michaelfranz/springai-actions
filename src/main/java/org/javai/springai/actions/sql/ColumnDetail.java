package org.javai.springai.actions.sql;

import java.util.List;

/**
 * Detailed information about a database column, suitable for LLM tool responses.
 * 
 * @param name column name (or token if tokenized)
 * @param description human-readable description of the column's purpose
 * @param dataType SQL data type (e.g., "string", "integer", "decimal")
 * @param tags semantic tags (e.g., "pk", "fk:other_table.id", "measure", "attribute")
 * @param constraints column constraints (e.g., "unique", "not null")
 * @param synonyms alternative names that refer to this column
 */
public record ColumnDetail(
		String name,
		String description,
		String dataType,
		List<String> tags,
		List<String> constraints,
		List<String> synonyms
) {
	/**
	 * Creates a ColumnDetail from an SqlColumn.
	 * 
	 * @param displayName the name to display (may be tokenized)
	 * @param column the source column
	 * @return a ColumnDetail
	 */
	public static ColumnDetail from(String displayName, SqlCatalog.SqlColumn column) {
		return new ColumnDetail(
				displayName,
				column.description(),
				column.dataType(),
				column.tags() != null ? column.tags() : List.of(),
				column.constraints() != null ? column.constraints() : List.of(),
				column.synonyms() != null ? column.synonyms() : List.of()
		);
	}
}

