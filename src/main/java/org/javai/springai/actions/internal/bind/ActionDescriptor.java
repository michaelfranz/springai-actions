package org.javai.springai.actions.internal.bind;

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

	public String renderConstraints() {
		StringBuilder sb = new StringBuilder();
		for (ActionParameterDescriptor param : actionParameterSpecs) {
			boolean hasAllowed = param.allowedValues() != null && param.allowedValues().length > 0;
			boolean hasRegex = param.allowedRegex() != null && !param.allowedRegex().isBlank();
			if (!hasAllowed && !hasRegex) {
				continue;
			}
			sb.append("Param ").append(param.name()).append(": ");
			if (hasAllowed) {
				sb.append("allowed values ").append(String.join(", ", param.allowedValues()));
				if (param.caseInsensitive()) {
					sb.append(" (case-insensitive)");
				}
				if (hasRegex) {
					sb.append("; ");
				}
			}
			if (hasRegex) {
				sb.append("regex ").append(param.allowedRegex());
			}
			sb.append("\n");
		}
		return sb.toString().trim();
	}
}

