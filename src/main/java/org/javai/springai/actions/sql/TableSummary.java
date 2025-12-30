package org.javai.springai.actions.sql;

import java.util.List;

/**
 * Summary information about a database table, suitable for LLM tool responses.
 * 
 * <p>Provides enough information for the LLM to decide if a table is relevant
 * to the user's query, without the full column detail.</p>
 * 
 * @param name table name (or token if tokenized)
 * @param description human-readable description of the table's purpose
 * @param type table type (e.g., "fact", "dimension", "bridge")
 * @param columnCount number of columns in the table
 * @param synonyms alternative names that refer to this table
 */
public record TableSummary(
		String name,
		String description,
		String type,
		int columnCount,
		List<String> synonyms
) {
	/**
	 * Creates a TableSummary from an SqlTable.
	 * 
	 * @param displayName the name to display (may be tokenized)
	 * @param table the source table
	 * @return a TableSummary
	 */
	public static TableSummary from(String displayName, SqlCatalog.SqlTable table) {
		return new TableSummary(
				displayName,
				table.description(),
				table.tableType(),
				table.columnCount(),
				table.synonymsOrEmpty()
		);
	}
}
