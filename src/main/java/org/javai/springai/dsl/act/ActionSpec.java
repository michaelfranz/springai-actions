package org.javai.springai.dsl.act;

import java.util.List;

/**
 * Represents a view of an action from the perspective of the LLM model.
 * It's what we tell the LLM is available in terms of actions and their parameters.
 * @param id of the action - what the LLM uses to identify the action
 * @param description of the action so the LLM can work out if this action is suitable
 * @param actionParameterSpecs spec for each parameter of the action
 */
public record ActionSpec(
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