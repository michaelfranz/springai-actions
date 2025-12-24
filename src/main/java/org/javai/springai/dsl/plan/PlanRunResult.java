package org.javai.springai.dsl.plan;

import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.dsl.exec.PlanResolutionResult;
import org.javai.springai.dsl.plan.PlanFormulationResult;

/**
 * Aggregate result for plan + resolve (+ optional execute) flows.
 */
public record PlanRunResult(
		PlanFormulationResult planning,
		PlanResolutionResult resolution,
		ActionContext executionContext
) {

	public static PlanRunResult success(PlanFormulationResult planning, PlanResolutionResult resolution,
			ActionContext executionContext) {
		return new PlanRunResult(planning, resolution, executionContext);
	}

	public static PlanRunResult failure(PlanFormulationResult planning, PlanResolutionResult resolution) {
		return new PlanRunResult(planning, resolution, null);
	}

	public boolean resolvedSuccessfully() {
		return resolution != null && resolution.isSuccess();
	}

	public boolean executed() {
		return resolvedSuccessfully() && executionContext != null;
	}
}

