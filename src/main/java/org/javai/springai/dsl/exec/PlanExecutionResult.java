package org.javai.springai.dsl.exec;

import java.util.List;

/**
 * Captures the outcome of executing a resolved plan.
 */
public record PlanExecutionResult(boolean success, List<StepExecutionResult> steps) {
	public PlanExecutionResult {
		steps = steps != null ? List.copyOf(steps) : List.of();
	}
}

