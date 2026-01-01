package org.javai.springai.actions.sql;

import java.util.Optional;
import org.javai.springai.actions.PromptContributor;
import org.javai.springai.actions.internal.prompt.SystemPromptContext;

/**
 * Contributes SQL query context to the system prompt for multi-turn refinement.
 * 
 * <p>When a conversation has a prior SQL query, this contributor renders it
 * into the prompt so the LLM can understand references like "filter that by...",
 * "add a column to those results", etc.</p>
 * 
 * <h2>Example Prompt Contribution</h2>
 * <pre>
 * CURRENT QUERY:
 * SELECT order_value, customer_name FROM orders JOIN customers ON orders.customer_id = customers.id
 * 
 * Tables: orders, customers
 * Columns: order_value, customer_name
 * Filter: (none)
 * </pre>
 * 
 * <h2>Usage</h2>
 * <pre>{@code
 * Planner.builder()
 *     .addPromptContext("workingContext", workingContext)
 *     .promptContributor(new SqlWorkingContextContributor())
 *     .build();
 * }</pre>
 */
public class SqlWorkingContextContributor implements PromptContributor {

	private static final String SECTION_HEADER = "CURRENT QUERY CONTEXT:";

	@Override
	public Optional<String> contribute(SystemPromptContext promptContext) {
		// Get working context from DSL context
		Optional<Object> workingContextOpt = promptContext.contextFor("workingContext");
		
		if (workingContextOpt.isEmpty()) {
			return Optional.empty();
		}

		Object workingContext = workingContextOpt.get();
		
		// Handle SqlQueryPayload directly or wrapped in WorkingContext
		SqlQueryPayload sqlPayload = extractPayload(workingContext);
		if (sqlPayload == null) {
			return Optional.empty();
		}

		StringBuilder sb = new StringBuilder();
		sb.append(SECTION_HEADER).append("\n");
		sb.append("🔴 CRITICAL: The user is refining this existing query. Your response MUST be based on this query:\n\n");
		sb.append("```sql\n");
		sb.append(sqlPayload.modelSql()).append("\n");
		sb.append("```\n\n");

		// Add metadata summary
		sb.append("Query details:\n");
		if (!sqlPayload.tables().isEmpty()) {
			sb.append("- Tables: ").append(String.join(", ", sqlPayload.tables())).append("\n");
		}
		if (!sqlPayload.selectedColumns().isEmpty()) {
			sb.append("- Selected columns: ").append(String.join(", ", sqlPayload.selectedColumns())).append("\n");
		}
		if (sqlPayload.whereClause() != null && !sqlPayload.whereClause().isBlank()) {
			sb.append("- Current filter: ").append(sqlPayload.whereClause()).append("\n");
		}

		sb.append("\n🔴 MODIFY the query above. Keep all existing SELECT columns, JOINs, and conditions. Only ADD or CHANGE what the user asks for.\n");

		return Optional.of(sb.toString());
	}

	private SqlQueryPayload extractPayload(Object obj) {
		if (obj instanceof SqlQueryPayload payload) {
			return payload;
		}
		if (obj instanceof org.javai.springai.actions.conversation.WorkingContext<?> wc) {
			if (SqlQueryPayload.CONTEXT_TYPE.equals(wc.contextType()) && wc.payload() instanceof SqlQueryPayload p) {
				return p;
			}
		}
		return null;
	}
}

