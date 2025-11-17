package org.javai.springai.actions.planning;

import com.fasterxml.jackson.databind.JsonNode;

public record ActionStep(
		String action,
		JsonNode arguments
) {}
