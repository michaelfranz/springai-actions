package org.javai.springai.actions.internal.bind;

import java.util.List;

/**
 * LLM-facing description of an action: id, description, parameters, and an optional exemplar
 */
public record ActionDescriptor(
		String id,
		String description,
		List<ActionParameterDescriptor> actionParameterSpecs,
		String example
) {
}

