package org.javai.springai.actions.plan;

/**
 * A typed argument for an action step, ready for invocation.
 *
 * @param name the parameter name
 * @param value the coerced value
 * @param targetType the expected type for invocation
 */
public record PlanArgument(
		String name,
		Object value,
		Class<?> targetType
) {
}

