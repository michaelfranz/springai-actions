package org.javai.springai.actions;

import org.javai.springai.actions.Plan;

/**
 * Executes a bound plan.
 * <p>
 * Plans must have {@link org.javai.springai.actions.plan.PlanStatus#READY} status
 * to be executed. Plans with pending or error steps will be rejected.
 */
public interface PlanExecutor {

	/**
	 * Execute a plan with a fresh, empty execution context.
	 *
	 * @param plan the bound plan to execute
	 * @return execution result containing success status and step results
	 * @throws IllegalStateException if the plan is not READY
	 */
	PlanExecutionResult execute(Plan plan);
}
