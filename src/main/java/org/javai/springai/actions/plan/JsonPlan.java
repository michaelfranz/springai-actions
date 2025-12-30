package org.javai.springai.actions.plan;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

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
 * <p>Resolution to bound {@link Plan} instances is handled by
 * {@link org.javai.springai.actions.exec.DefaultPlanResolver}.
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
}
