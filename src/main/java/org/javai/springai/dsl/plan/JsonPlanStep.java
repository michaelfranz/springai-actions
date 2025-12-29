package org.javai.springai.dsl.plan;

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
 * <p>For DSL-embedded parameters (e.g., SQL queries), the parameter value
 * may be a string containing an S-expression:
 * <pre>
 * {
 *   "actionId": "runSqlQuery",
 *   "description": "Execute SQL query",
 *   "parameters": {
 *     "query": "(Q (F customers c) (S (= c.name \"Mike\")) (C c.*))"
 *   }
 * }
 * </pre>
 *
 * @param actionId the identifier of the action to execute
 * @param description LLM-generated description of why this step is being executed
 * @param parameters map of parameter names to values (can be primitives, objects, or S-expression strings)
 */
public record JsonPlanStep(
		@JsonProperty("actionId") String actionId,
		@JsonProperty("description") String description,
		@JsonProperty("parameters") Map<String, Object> parameters
) {

	/**
	 * Converts this JSON plan step to the internal {@link PlanStep.ActionStep} representation.
	 *
	 * @param orderedParamNames the parameter names in the order expected by the action
	 * @return an ActionStep with arguments ordered according to the action's signature
	 */
	public PlanStep.ActionStep toActionStep(String[] orderedParamNames) {
		Object[] args = new Object[orderedParamNames.length];
		Map<String, Object> params = parameters != null ? parameters : Map.of();
		for (int i = 0; i < orderedParamNames.length; i++) {
			args[i] = params.get(orderedParamNames[i]);
		}
		return new PlanStep.ActionStep(description, actionId, args);
	}

	/**
	 * Converts this JSON plan step to the internal {@link PlanStep.ActionStep} representation
	 * using all parameters in iteration order.
	 *
	 * @return an ActionStep with arguments in map iteration order
	 */
	public PlanStep.ActionStep toActionStep() {
		Map<String, Object> params = parameters != null ? parameters : Map.of();
		Object[] args = params.values().toArray();
		return new PlanStep.ActionStep(description, actionId, args);
	}
}

