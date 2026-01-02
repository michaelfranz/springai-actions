package org.javai.springai.actions.sql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.javai.springai.actions.api.TypeSpecProvider;

/**
 * Provides JSON schema specification for the {@link Query} type.
 * 
 * <p>The Query type is specified as a simple object with an "sql" field containing
 * an ANSI SQL SELECT statement. This shields the LLM from the internal Query
 * implementation details and focuses it on producing valid SQL.</p>
 * 
 * <p>Expected LLM output format:</p>
 * <pre>{@code
 * {
 *   "query": {
 *     "sql": "SELECT c.name, o.value FROM fct_orders o JOIN dim_customer c ON o.customer_id = c.id"
 *   }
 * }
 * }</pre>
 */
public final class QuerySpecProvider implements TypeSpecProvider {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Override
	public Class<?> supportedType() {
		return Query.class;
	}

	@Override
	public ObjectNode schema() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");
		
		ObjectNode properties = schema.putObject("properties");
		ObjectNode sqlField = properties.putObject("sql");
		sqlField.put("type", "string");
		sqlField.put("description", "ANSI SQL SELECT statement");
		
		schema.putArray("required").add("sql");
		schema.put("additionalProperties", false);
		
		return schema;
	}

	@Override
	public String guidance() {
		return """
			QUERY PARAMETER FORMAT:
			YOU must generate the SQL query. Use: { "sql": "<SELECT statement>" }
			- Generate a valid ANSI SQL SELECT statement based on the user's request
			- If CURRENT QUERY CONTEXT exists, modify that query to fulfill the request
			- Use ONLY the exact table and column names from the SQL CATALOG above
			- For data across multiple tables, use JOINs based on FK relationships
			- NEVER use PENDING to ask for the query - YOU generate it
			""";
	}
}

