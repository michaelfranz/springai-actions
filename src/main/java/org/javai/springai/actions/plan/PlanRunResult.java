package org.javai.springai.actions.plan;

import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.exec.ResolvedPlan;

/**
 * Aggregate result for plan + resolve (+ optional execute) flows.
 */
public record PlanRunResult(
		PlanFormulationResult planning,
		ResolvedPlan resolvedPlan,
		ActionContext executionContext
) {

	public static PlanRunResult success(PlanFormulationResult planning, ResolvedPlan resolvedPlan,
			ActionContext executionContext) {
		return new PlanRunResult(planning, resolvedPlan, executionContext);
	}

	public boolean resolvedSuccessfully() {
		return resolvedPlan != null && resolvedPlan.status() == PlanStatus.READY;
	}

	public boolean executed() {
		return resolvedSuccessfully() && executionContext != null;
	}
}

