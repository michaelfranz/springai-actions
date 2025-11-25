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
			the list is empty
			""")
		List<PlanStep> steps
) {
}
