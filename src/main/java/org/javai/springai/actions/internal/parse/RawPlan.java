package org.javai.springai.actions.internal.parse;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

/**
 * Raw JSON representation of a plan for Jackson deserialization.
 * This record maps directly to the JSON structure that the LLM produces.
 *
 * <p>This is an internal parsing DTO. Applications should work with
 * {@link org.javai.springai.actions.plan.Plan} which is the fully-resolved
 * plan type.
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
public record RawPlan(
		@JsonProperty("message") String message,
		@JsonProperty("steps") List<RawPlanStep> steps
) {

	private static final ObjectMapper DEFAULT_MAPPER = new ObjectMapper();

	/**
	 * Parse a JSON string into a RawPlan.
	 *
	 * @param json the JSON string to parse
	 * @return the parsed RawPlan
	 * @throws JsonProcessingException if parsing fails
	 */
	public static RawPlan fromJson(String json) throws JsonProcessingException {
		return fromJson(json, DEFAULT_MAPPER);
	}

	/**
	 * Parse a JSON string into a RawPlan using a custom ObjectMapper.
	 *
	 * @param json the JSON string to parse
	 * @param mapper the ObjectMapper to use
	 * @return the parsed RawPlan
	 * @throws JsonProcessingException if parsing fails
	 */
	public static RawPlan fromJson(String json, ObjectMapper mapper) throws JsonProcessingException {
		return mapper.readValue(json, RawPlan.class);
	}
}

