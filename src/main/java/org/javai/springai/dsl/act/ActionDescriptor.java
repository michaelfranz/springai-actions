package org.javai.springai.dsl.act;

import java.util.List;

/**
 * LLM-facing description of an action: id, description, and parameters.
 */
public record ActionDescriptor(
		String id,
		String description,
		List<ActionParameterSpec> actionParameterSpecs
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

