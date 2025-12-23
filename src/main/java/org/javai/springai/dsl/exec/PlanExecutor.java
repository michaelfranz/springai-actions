package org.javai.springai.dsl.exec;

/**
 * Executes a fully resolved plan.
 */
public interface PlanExecutor {

	/**
	 * Execute a resolved plan with a fresh, empty execution context.
	 * @param plan the resolved plan to execute
	 * @return execution result
	 */
	PlanExecutionResult execute(ResolvedPlan plan);

}

