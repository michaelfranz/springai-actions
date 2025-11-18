package org.javai.springai.actions.planning;

import com.fasterxml.jackson.databind.JsonNode;

public record PlanStep(
		String action,
		JsonNode arguments
) {}
