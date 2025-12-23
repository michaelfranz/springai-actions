package org.javai.springai.dsl.plan;

import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.dsl.exec.PlanResolutionResult;

/**
 * Aggregate result for plan + resolve (+ optional execute) flows.
 */
public record PlanRunResult(
		PlanExecutionResult planning,
		PlanResolutionResult resolution,
		ActionContext executionContext
) {

	public static PlanRunResult success(PlanExecutionResult planning, PlanResolutionResult resolution,
			ActionContext executionContext) {
		return new PlanRunResult(planning, resolution, executionContext);
	}

	public static PlanRunResult failure(PlanExecutionResult planning, PlanResolutionResult resolution) {
		return new PlanRunResult(planning, resolution, null);
	}

	public boolean resolvedSuccessfully() {
		return resolution != null && resolution.isSuccess();
	}

	public boolean executed() {
		return resolvedSuccessfully() && executionContext != null;
	}
}

