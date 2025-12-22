package org.javai.springai.dsl.exec;

/**
 * Structured error encountered while resolving a plan into executable steps.
 */
public record PlanResolutionError(
		String actionId,
		String paramName,
		String reason,
		Object rawValue
) {
}

