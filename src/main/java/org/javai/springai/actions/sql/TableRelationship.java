package org.javai.springai.actions.sql;

/**
 * Describes a foreign key relationship between tables, suitable for LLM tool responses.
 * 
 * <p>Used to help the LLM understand how to JOIN tables correctly.</p>
 * 
 * @param fromTable the table containing the foreign key
 * @param fromColumn the foreign key column
 * @param toTable the referenced table
 * @param toColumn the referenced column (typically the primary key)
 * @param description human-readable description of the relationship
 */
public record TableRelationship(
		String fromTable,
		String fromColumn,
		String toTable,
		String toColumn,
		String description
) {
	/**
	 * Creates a join clause suggestion for SQL.
	 * 
	 * @return a string like "JOIN toTable ON fromTable.fromColumn = toTable.toColumn"
	 */
	public String joinHint() {
		return "JOIN %s ON %s.%s = %s.%s".formatted(
				toTable, fromTable, fromColumn, toTable, toColumn);
	}
}

