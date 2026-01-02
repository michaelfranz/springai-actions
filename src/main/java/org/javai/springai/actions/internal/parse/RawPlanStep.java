package org.javai.springai.actions.internal.parse;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Raw JSON representation of a plan step for Jackson deserialization.
 * This record maps directly to the JSON structure that the LLM produces.
 *
 * <p>This is an internal parsing DTO. Applications should work with
 * {@link org.javai.springai.actions.plan.PlanStep} which is the fully-resolved
 * step type.
 *
 * <p>Example JSON for action step:
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
 * <p>Example JSON for pending step:
 * <pre>
 * {
 *   "actionId": "searchProducts",
 *   "description": "Search for products",
 *   "status": "pending",
 *   "pendingParams": [{"name": "query", "prompt": "What would you like to search for?"}],
 *   "providedParams": {}
 * }
 * </pre>
 *
 * @param actionId the identifier of the action to execute
 * @param description LLM-generated description of why this step is being executed
 * @param parameters map of parameter names to values (can be primitives, objects, or embedded queries)
 * @param status optional status, "pending" indicates missing parameters
 * @param pendingParams list of parameters that need user input (for pending steps)
 * @param providedParams parameters already provided (for pending steps)
 * @param noAction true if this is a no-action step
 * @param error true if this is an error step
 * @param reason reason for noAction or error steps
 */
public record RawPlanStep(
		@JsonProperty("actionId") String actionId,
		@JsonProperty("description") String description,
		@JsonProperty("parameters") Map<String, Object> parameters,
		@JsonProperty("status") String status,
		@JsonProperty("pendingParams") List<RawPendingParam> pendingParams,
		@JsonProperty("providedParams") Map<String, Object> providedParams,
		@JsonProperty("noAction") Boolean noAction,
		@JsonProperty("error") Boolean error,
		@JsonProperty("reason") String reason
) {
	/**
	 * Check if this is a pending action step.
	 */
	public boolean isPending() {
		return "pending".equalsIgnoreCase(status);
	}
	
	/**
	 * Check if this is a no-action step.
	 */
	public boolean isNoAction() {
		return Boolean.TRUE.equals(noAction);
	}
	
	/**
	 * Check if this is an error step.
	 */
	public boolean isError() {
		return Boolean.TRUE.equals(error);
	}
	
	/**
	 * Raw pending parameter from JSON.
	 */
	public record RawPendingParam(
			@JsonProperty("name") String name,
			@JsonProperty("prompt") String prompt
	) {}
	
	/**
	 * Convenience factory for creating a simple action step (for tests and programmatic use).
	 */
	public static RawPlanStep actionStep(String actionId, String description, Map<String, Object> parameters) {
		return new RawPlanStep(actionId, description, parameters, null, null, null, null, null, null);
	}
	
	/**
	 * Convenience factory for creating a pending step (for tests and programmatic use).
	 */
	public static RawPlanStep pendingStep(String actionId, String description, 
			List<RawPendingParam> pendingParams, Map<String, Object> providedParams) {
		return new RawPlanStep(actionId, description, null, "pending", pendingParams, providedParams, null, null, null);
	}
	
	/**
	 * Convenience factory for creating a no-action step (for tests and programmatic use).
	 */
	public static RawPlanStep noActionStep(String reason) {
		return new RawPlanStep(null, null, null, null, null, null, true, null, reason);
	}
	
	/**
	 * Convenience factory for creating an error step (for tests and programmatic use).
	 */
	public static RawPlanStep errorStep(String reason) {
		return new RawPlanStep(null, null, null, null, null, null, null, true, reason);
	}
}

