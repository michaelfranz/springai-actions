package org.javai.springai.actions.planning;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

public record Plan(
		@JsonProperty(required = true)
		@JsonPropertyDescription("""
			A succinct description of the intention of the plan. If plan steps could not
			be determined, then the planIntent is instead used to say why no steps
			could be defined. Such a message might be: "Insufficient information
			provided to create a plan", or "This this application unfortunately
			does not address this kind of question"
			""")
		String planIntent,
		@JsonProperty(required = true)
		@JsonPropertyDescription("""
			The steps which make up the plan. If no plan steps could be determined then
			the list is empty and not null.
			""")
		List<PlanStep> steps
) {

	/**
	 * Returns a developer-friendly description of this plan for diagnostics.
	 * Includes the intent and a brief summary of the contained steps.
	 * @return formatted description string
	 */
	public String describe() {
		StringBuilder sb = new StringBuilder()
				.append("Plan[")
				.append("intent='").append(planIntent != null ? planIntent : "unspecified").append("'");

		if (steps == null || steps.isEmpty()) {
			sb.append(", steps=0");
		}
		else {
			sb.append(", steps=").append(steps.size());
			sb.append(", stepSummaries=").append(
					steps.stream()
							.limit(3)
							.map(step -> step != null ? step.describe() : "PlanStep[null]")
							.toList());
			if (steps.size() > 3) {
				sb.append(" (+").append(steps.size() - 3).append(" more)");
			}
		}

		return sb.append("]").toString();
	}
}
