package org.javai.springai.dsl.exec;

/**
 * Per-step execution outcome.
 */
public record StepExecutionResult(
		String actionId,
		boolean success,
		Object returnValue,
		Throwable error,
		String message
) {
}

