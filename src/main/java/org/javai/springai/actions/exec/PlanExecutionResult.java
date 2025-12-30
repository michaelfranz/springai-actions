package org.javai.springai.actions.exec;

import java.util.List;
import org.javai.springai.actions.internal.exec.StepExecutionResult;

/**
 * Captures the outcome of executing a resolved plan.
 */
public record PlanExecutionResult(boolean success, List<StepExecutionResult> steps, org.javai.springai.actions.api.ActionContext context) {
	public PlanExecutionResult {
		steps = steps != null ? List.copyOf(steps) : List.of();
	}
}

