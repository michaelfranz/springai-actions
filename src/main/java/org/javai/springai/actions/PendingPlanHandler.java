package org.javai.springai.actions;

import org.javai.springai.actions.api.ActionContext;

/**
 * Handler invoked when a plan is in {@link PlanStatus#PENDING} state.
 *
 * <p>PENDING plans contain one or more {@link PlanStep.PendingActionStep} steps
 * that require additional user input before execution can proceed.</p>
 *
 * <p>Implementations should typically:</p>
 * <ol>
 *   <li>Extract pending parameters via {@link Plan#pendingParams()}</li>
 *   <li>Prompt the user for the missing information</li>
 *   <li>Return a {@link PlanExecutionResult#notExecuted} result</li>
 * </ol>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * DefaultPlanExecutor executor = DefaultPlanExecutor.builder()
 *     .onPending((plan, context) -> {
 *         String missing = String.join(", ", plan.pendingParameterNames());
 *         respondToUser("I need more information: " + missing);
 *         return PlanExecutionResult.notExecuted(plan, context, "Awaiting user input");
 *     })
 *     .build();
 * }</pre>
 *
 * @see ErrorPlanHandler
 * @see DefaultPlanExecutor.Builder#onPending(PendingPlanHandler)
 */
@FunctionalInterface
public interface PendingPlanHandler {

	/**
	 * Handle a plan that has pending parameters requiring user input.
	 *
	 * @param plan the plan with {@link PlanStatus#PENDING} status
	 * @param context the action context (may contain pre-populated request-scoped data)
	 * @return a {@link PlanExecutionResult} to return from execute(), 
	 *         or {@code null} to fall back to throwing {@link IllegalStateException}
	 */
	PlanExecutionResult handle(Plan plan, ActionContext context);
}

