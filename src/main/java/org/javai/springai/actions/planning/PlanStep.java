package org.javai.springai.actions.planning;

import com.fasterxml.jackson.databind.JsonNode;

public record PlanStep(
		String action,
		JsonNode arguments
) {
	/**
	 * Returns a developer-friendly string representation of this plan step for debugging purposes.
	 * This method provides more useful information than the default toString() implementation.
	 *
	 * @return a formatted string describing the plan step
	 */
	public String describe() {
		StringBuilder sb = new StringBuilder();
		sb.append("PlanStep[")
		.append("action='")
		.append(action)
		.append("'");
		if (arguments != null && !arguments.isEmpty()) {
			sb.append(", arguments=").append(arguments.toString());
		} else {
			sb.append(", arguments={}");
		}
		sb.append("]");
		return sb.toString();
	}
}
