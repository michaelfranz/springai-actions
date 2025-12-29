package org.javai.springai.actions.plan;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * JSON-friendly representation of a plan for Jackson deserialization.
 * This record maps directly to the JSON structure that the LLM produces.
 *
 * <p>Example JSON:
 * <pre>
 * {
 *   "message": "I'll help you search for products and add them to your cart.",
 *   "steps": [
 *     {
 *       "actionId": "searchProducts",
 *       "description": "Search for red shoes",
 *       "parameters": { "query": "red shoes" }
 *     },
 *     {
 *       "actionId": "addToCart",
 *       "description": "Add the selected product to cart",
 *       "parameters": { "productId": "PROD-123", "quantity": 1 }
 *     }
 *   ]
 * }
 * </pre>
 *
 * @param message LLM-generated message accompanying the plan
 * @param steps the list of steps to execute
 */
public record JsonPlan(
		@JsonProperty("message") String message,
		@JsonProperty("steps") List<JsonPlanStep> steps
) {

	private static final ObjectMapper DEFAULT_MAPPER = new ObjectMapper();

	/**
	 * Parse a JSON string into a JsonPlan.
	 *
	 * @param json the JSON string to parse
	 * @return the parsed JsonPlan
	 * @throws JsonProcessingException if parsing fails
	 */
	public static JsonPlan fromJson(String json) throws JsonProcessingException {
		return fromJson(json, DEFAULT_MAPPER);
	}

	/**
	 * Parse a JSON string into a JsonPlan using a custom ObjectMapper.
	 *
	 * @param json the JSON string to parse
	 * @param mapper the ObjectMapper to use
	 * @return the parsed JsonPlan
	 * @throws JsonProcessingException if parsing fails
	 */
	public static JsonPlan fromJson(String json, ObjectMapper mapper) throws JsonProcessingException {
		return mapper.readValue(json, JsonPlan.class);
	}

	/**
	 * Converts this JSON plan to the internal {@link Plan} representation.
	 *
	 * @param parameterOrderResolver function that returns ordered parameter names for each action
	 * @return the internal Plan representation
	 */
	public Plan toPlan(Function<String, String[]> parameterOrderResolver) {
		if (steps == null || steps.isEmpty()) {
			return new Plan(message, List.of());
		}
		List<PlanStep> planSteps = steps.stream()
				.map(step -> {
					String[] orderedParams = parameterOrderResolver.apply(step.actionId());
					if (orderedParams != null) {
						return step.toActionStep(orderedParams);
					}
					return step.toActionStep();
				})
				.map(PlanStep.class::cast)
				.toList();
		return new Plan(message, planSteps);
	}

	/**
	 * Converts this JSON plan to the internal {@link Plan} representation
	 * using a pre-built map of action IDs to parameter orders.
	 *
	 * @param actionParameterOrders map of action IDs to ordered parameter name arrays
	 * @return the internal Plan representation
	 */
	public Plan toPlan(Map<String, String[]> actionParameterOrders) {
		return toPlan(actionId -> actionParameterOrders.get(actionId));
	}

	/**
	 * Converts this JSON plan to the internal {@link Plan} representation
	 * using iteration order for parameters (when parameter order is not critical).
	 *
	 * @return the internal Plan representation
	 */
	public Plan toPlan() {
		return toPlan(actionId -> null);
	}
}

