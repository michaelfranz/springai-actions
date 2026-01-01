package org.javai.springai.actions;

import org.javai.springai.actions.api.ActionContext;

/**
 * Handler invoked when a plan is in {@link PlanStatus#ERROR} state.
 *
 * <p>ERROR plans contain one or more {@link PlanStep.ErrorStep} steps indicating
 * that plan formulation or parsing failed. This typically occurs when:</p>
 * <ul>
 *   <li>The LLM produces unparseable output</li>
 *   <li>The LLM references non-existent actions</li>
 *   <li>Parameter validation fails during resolution</li>
 * </ul>
 *
 * <p>Implementations should typically:</p>
 * <ol>
 *   <li>Log the error for debugging</li>
 *   <li>Provide a graceful user-facing message</li>
 *   <li>Return a {@link PlanExecutionResult#notExecuted} result</li>
 * </ol>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * DefaultPlanExecutor executor = DefaultPlanExecutor.builder()
 *     .onError((plan, context) -> {
 *         logger.warn("Plan error: {}", extractErrorReason(plan));
 *         respondToUser("Sorry, I couldn't understand that. Could you rephrase?");
 *         return PlanExecutionResult.notExecuted(plan, context, "Plan parsing failed");
 *     })
 *     .build();
 * }</pre>
 *
 * @see PendingPlanHandler
 * @see DefaultPlanExecutor.Builder#onError(ErrorPlanHandler)
 */
@FunctionalInterface
public interface ErrorPlanHandler {

	/**
	 * Handle a plan that contains errors preventing execution.
	 *
	 * @param plan the plan with {@link PlanStatus#ERROR} status
	 * @param context the action context (may contain pre-populated request-scoped data)
	 * @return a {@link PlanExecutionResult} to return from execute(),
	 *         or {@code null} to fall back to throwing {@link IllegalStateException}
	 */
	PlanExecutionResult handle(Plan plan, ActionContext context);
}

