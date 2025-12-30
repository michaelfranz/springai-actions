package org.javai.springai.actions.sql;

/**
 * Factory for creating validated Query objects from raw SQL strings.
 * 
 * <p>This factory is used by the plan resolver to convert SQL strings from LLM output
 * into validated Query objects for action parameters.</p>
 * 
 * <p>Unlike the previous SXL-based approach, this factory expects standard ANSI SQL
 * strings directly from the LLM, which are then parsed and validated using JSqlParser.</p>
 */
public class QueryFactory {

	private final SqlCatalog catalog;

	/**
	 * Creates a QueryFactory with schema validation.
	 * 
	 * @param catalog the schema catalog for table/column validation
	 */
	public QueryFactory(SqlCatalog catalog) {
		this.catalog = catalog;
	}

	/**
	 * Creates a QueryFactory without schema validation.
	 */
	public QueryFactory() {
		this(null);
	}

	/**
	 * Creates a Query from a raw SQL string.
	 * 
	 * @param sql the SQL string to parse and validate
	 * @return a validated Query object
	 * @throws QueryValidationException if the SQL is invalid
	 */
	public Query create(String sql) {
		return Query.fromSql(sql, catalog);
	}

	/**
	 * Returns the target type this factory produces.
	 */
	public Class<Query> getType() {
		return Query.class;
	}
}

