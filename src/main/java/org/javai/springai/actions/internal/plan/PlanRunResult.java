package org.javai.springai.actions.internal.plan;

import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.Plan;
import org.javai.springai.actions.PlanStatus;

/**
 * Aggregate result for plan + execute flows.
 *
 * @param planning the plan formulation result
 * @param plan the bound plan (same as planning.plan())
 * @param executionContext the execution context after running the plan
 */
public record PlanRunResult(
		PlanFormulationResult planning,
		Plan plan,
		ActionContext executionContext
) {

	public static PlanRunResult success(PlanFormulationResult planning, Plan plan,
			ActionContext executionContext) {
		return new PlanRunResult(planning, plan, executionContext);
	}

	/**
	 * Check if the plan was successfully created and is ready.
	 */
	public boolean isReady() {
		return plan != null && plan.status() == PlanStatus.READY;
	}

	/**
	 * Check if the plan was executed.
	 */
	public boolean executed() {
		return isReady() && executionContext != null;
	}
}
