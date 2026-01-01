package org.javai.springai.actions;

import java.util.List;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.internal.exec.StepExecutionResult;

/**
 * Captures the outcome of executing (or not executing) a resolved plan.
 *
 * <p>There are two categories of results:</p>
 * <ul>
 *   <li><b>Executed plans</b>: The plan was in {@link PlanStatus#READY} state and
 *       action steps were invoked. Check {@link #success()} for overall outcome.</li>
 *   <li><b>Non-executed plans</b>: The plan was in {@link PlanStatus#PENDING} or
 *       {@link PlanStatus#ERROR} state and a handler short-circuited execution.
 *       Use {@link #wasExecuted()} to distinguish this case.</li>
 * </ul>
 *
 * @param success whether all steps completed successfully (false if not executed)
 * @param steps results from each executed step (empty if not executed)
 * @param context the action context after execution (or the pre-populated context if not executed)
 * @param terminatedWith the plan status that prevented execution, or null if executed
 * @param terminationReason human-readable explanation for non-execution, or null if executed
 */
public record PlanExecutionResult(
		boolean success,
		List<StepExecutionResult> steps,
		ActionContext context,
		PlanStatus terminatedWith,
		String terminationReason
) {

	/**
	 * Canonical constructor with defensive copying.
	 */
	public PlanExecutionResult {
		steps = steps != null ? List.copyOf(steps) : List.of();
	}

	/**
	 * Backward-compatible constructor for executed plans.
	 *
	 * @param success whether execution succeeded
	 * @param steps step execution results
	 * @param context the action context after execution
	 */
	public PlanExecutionResult(boolean success, List<StepExecutionResult> steps, ActionContext context) {
		this(success, steps, context, null, null);
	}

	/**
	 * Create a result for a plan that was not executed due to its state.
	 *
	 * <p>Use this factory method in {@link PendingPlanHandler} and {@link ErrorPlanHandler}
	 * implementations to return a graceful result instead of throwing an exception.</p>
	 *
	 * @param plan the plan that was not executed
	 * @param context the action context (preserved from the execute call)
	 * @param reason human-readable explanation for why execution did not proceed
	 * @return a result indicating the plan was not executed
	 */
	public static PlanExecutionResult notExecuted(Plan plan, ActionContext context, String reason) {
		return new PlanExecutionResult(
				false,
				List.of(),
				context != null ? context : new ActionContext(),
				plan != null ? plan.status() : PlanStatus.ERROR,
				reason
		);
	}

	/**
	 * Check if the plan was actually executed.
	 *
	 * <p>Returns {@code false} if a handler short-circuited execution due to
	 * {@link PlanStatus#PENDING} or {@link PlanStatus#ERROR} state.</p>
	 *
	 * @return true if steps were executed, false if handlers intercepted
	 */
	public boolean wasExecuted() {
		return terminatedWith == null;
	}
}
