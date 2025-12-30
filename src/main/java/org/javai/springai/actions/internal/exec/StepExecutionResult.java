package org.javai.springai.actions.internal.exec;

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

