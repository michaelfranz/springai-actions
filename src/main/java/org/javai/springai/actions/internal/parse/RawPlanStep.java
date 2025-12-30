package org.javai.springai.actions.internal.parse;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Raw JSON representation of a plan step for Jackson deserialization.
 * This record maps directly to the JSON structure that the LLM produces.
 *
 * <p>This is an internal parsing DTO. Applications should work with
 * {@link org.javai.springai.actions.plan.PlanStep} which is the fully-resolved
 * step type.
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
 * @param actionId the identifier of the action to execute
 * @param description LLM-generated description of why this step is being executed
 * @param parameters map of parameter names to values (can be primitives, objects, or embedded queries)
 */
public record RawPlanStep(
		@JsonProperty("actionId") String actionId,
		@JsonProperty("description") String description,
		@JsonProperty("parameters") Map<String, Object> parameters
) {
}

