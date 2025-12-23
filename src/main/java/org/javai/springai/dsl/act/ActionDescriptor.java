package org.javai.springai.dsl.act;

import java.util.List;

/**
 * LLM-facing description of an action: id, description, parameters, and an optional exemplar
 * showing canonical SXL usage (e.g., full (P ... (PS action-id (EMBED ...))) shape).
 */
public record ActionDescriptor(
		String id,
		String description,
		List<ActionParameterDescriptor> actionParameterSpecs,
		String example
) {

	public String toSxl() {
		StringBuilder sxlExpression = new StringBuilder()
				.append("(PS ")
				.append(id)
				.append(" ")
				.append("\"").append(description).append("\"");
		if (actionParameterSpecs.isEmpty()) {
			return sxlExpression.append(")").toString();
		}
		sxlExpression.append(" (");
		actionParameterSpecs.forEach(
				aps -> sxlExpression.append(aps.toSxl()).append(" ")
		);
		if (sxlExpression.charAt(sxlExpression.length() - 1) == ' ') {
			sxlExpression.setLength(sxlExpression.length() - 1);
		}
		sxlExpression.append("))");
		return sxlExpression.toString();
	}
}

