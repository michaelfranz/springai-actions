package org.javai.springai.actions;

import org.javai.springai.actions.api.ActionContext;

/**
 * Handler invoked when a plan contains no executable actions.
 *
 * <p>This occurs when:</p>
 * <ul>
 *   <li>The plan contains a {@link PlanStep.NoActionStep} - the assistant explicitly
 *       determined that no action matches the user's request</li>
 *   <li>The plan has an empty steps list - the LLM returned no actions</li>
 * </ul>
 *
 * <p>Unlike ERROR (which indicates a problem) or PENDING (which requires more input),
 * NO_ACTION represents a deliberate decision by the assistant that the request is
 * outside its capabilities or scope.</p>
 *
 * <p>Implementations should typically:</p>
 * <ol>
 *   <li>Present the message to the user explaining what the assistant CAN help with</li>
 *   <li>Log the situation for analytics (to identify capability gaps)</li>
 *   <li>Return a {@link PlanExecutionResult#notExecuted} result</li>
 * </ol>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * DefaultPlanExecutor executor = DefaultPlanExecutor.builder()
 *     .onNoAction((plan, context, message) -> {
 *         respondToUser(message);
 *         logger.info("No action for request - assistant said: {}", message);
 *         return PlanExecutionResult.notExecuted(plan, context, "No action identified");
 *     })
 *     .build();
 * }</pre>
 *
 * @see PendingPlanHandler
 * @see ErrorPlanHandler
 * @see DefaultPlanExecutor.Builder#onNoAction(NoActionPlanHandler)
 */
@FunctionalInterface
public interface NoActionPlanHandler {

	/**
	 * Handle a plan where no action could be identified.
	 *
	 * @param plan the plan (may contain {@link PlanStep.NoActionStep} or be empty)
	 * @param context the action context (may contain pre-populated request-scoped data)
	 * @param message the explanation message from {@link PlanStep.NoActionStep},
	 *                or a default message if the plan was simply empty
	 * @return a {@link PlanExecutionResult} to return from execute(),
	 *         or {@code null} to fall back to throwing {@link IllegalStateException}
	 */
	PlanExecutionResult handle(Plan plan, ActionContext context, String message);
}

