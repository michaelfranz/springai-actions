package org.javai.springai.actions.plan;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * JSON-friendly representation of a plan step for Jackson deserialization.
 * This record maps directly to the JSON structure that the LLM produces.
 *
 * <p>Example JSON:
 * <pre>
 * {
 *   "actionId": "searchProducts",
 *   "description": "Search for products matching user query",
 *   "parameters": {
 *     "query": "red shoes",
 *     "maxResults": 10
 *   }
 * }
 * </pre>
 *
 * <p>Resolution to bound {@link PlanStep.ActionStep} instances is handled by
 * {@link org.javai.springai.actions.exec.DefaultPlanResolver}.
 *
 * @param actionId the identifier of the action to execute
 * @param description LLM-generated description of why this step is being executed
 * @param parameters map of parameter names to values (can be primitives, objects, or embedded queries)
 */
public record JsonPlanStep(
		@JsonProperty("actionId") String actionId,
		@JsonProperty("description") String description,
		@JsonProperty("parameters") Map<String, Object> parameters
) {
}
