package org.javai.springai.actions.sql;

import java.util.ArrayList;
import java.util.List;

/**
 * Full detail about a database table including all columns, suitable for LLM tool responses.
 * 
 * <p>Provides everything the LLM needs to formulate SQL queries against this table.</p>
 * 
 * @param name table name (or token if tokenized)
 * @param description human-readable description of the table's purpose
 * @param type table type (e.g., "fact", "dimension", "bridge")
 * @param columns list of column details
 * @param synonyms alternative names that refer to this table
 */
public record TableDetail(
		String name,
		String description,
		String type,
		List<ColumnDetail> columns,
		List<String> synonyms
) {
	/**
	 * Creates a TableDetail from an SqlTable.
	 * 
	 * @param displayName the name to display (may be tokenized)
	 * @param table the source table
	 * @param catalog the catalog (for column tokenization)
	 * @param canonicalTableName the real table name (for column token lookup)
	 * @return a TableDetail
	 */
	public static TableDetail from(String displayName, SqlCatalog.SqlTable table, 
			SqlCatalog catalog, String canonicalTableName) {
		List<ColumnDetail> columns = new ArrayList<>();
		if (table.columns() != null) {
			for (var col : table.columns()) {
				String columnDisplayName = catalog.isTokenized() 
						? catalog.getColumnToken(canonicalTableName, col.name()).orElse(col.name())
						: col.name();
				columns.add(ColumnDetail.from(columnDisplayName, col));
			}
		}
		
		return new TableDetail(
				displayName,
				table.description(),
				table.tableType(),
				columns,
				table.synonymsOrEmpty()
		);
	}
}
