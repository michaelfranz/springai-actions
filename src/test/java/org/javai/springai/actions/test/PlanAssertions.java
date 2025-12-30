package org.javai.springai.actions.test;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.javai.springai.actions.DefaultPlanExecutor;
import org.javai.springai.actions.Plan;
import org.javai.springai.actions.PlanExecutionResult;
import org.javai.springai.actions.PlanStatus;
import org.javai.springai.actions.PlanStep;

/**
 * Assertion utilities for plan-related tests.
 * 
 * <p>These assertions provide descriptive error messages that reveal
 * the underlying cause of failures, avoiding the need to hunt through logs.</p>
 */
public final class PlanAssertions {

	private PlanAssertions() {
	}

	/**
	 * Asserts that the plan is in READY status.
	 * If not, throws an AssertionError with details of any error steps.
	 */
	public static void assertPlanReady(Plan plan) {
		if (plan == null) {
			throw new AssertionError("Plan is null");
		}
		if (plan.status() != PlanStatus.READY) {
			String errors = extractErrors(plan);
			String pending = extractPending(plan);
			
			StringBuilder message = new StringBuilder()
					.append("Expected plan status READY but was ").append(plan.status());
			
			if (!errors.isEmpty()) {
				message.append("\nErrors:\n").append(errors);
			}
			if (!pending.isEmpty()) {
				message.append("\nPending:\n").append(pending);
			}
			
			throw new AssertionError(message.toString());
		}
	}

	/**
	 * Asserts that the plan execution succeeded.
	 * If not, throws an AssertionError with step failure details.
	 */
	public static void assertExecutionSuccess(PlanExecutionResult result) {
		if (result == null) {
			throw new AssertionError("PlanExecutionResult is null");
		}
		if (!result.success()) {
			String failures = result.steps().stream()
					.filter(s -> !s.success())
					.map(s -> {
						String errorMsg = s.error() != null ? s.error().getMessage() : s.message();
						return "  - " + s.actionId() + ": " + (errorMsg != null ? errorMsg : "unknown error");
					})
					.collect(Collectors.joining("\n"));
			throw new AssertionError("Plan execution failed:\n" + 
					(failures.isEmpty() ? "  (no step details available)" : failures));
		}
	}

	/**
	 * Asserts that the plan is ready AND execution succeeds.
	 * Combines both checks with descriptive error messages.
	 */
	public static PlanExecutionResult assertPlanExecutesSuccessfully(Plan plan, DefaultPlanExecutor executor) {
		assertPlanReady(plan);
		PlanExecutionResult result = executor.execute(plan);
		assertExecutionSuccess(result);
		return result;
	}

	private static String extractErrors(Plan plan) {
		return plan.planSteps().stream()
				.filter(step -> step instanceof PlanStep.ErrorStep)
				.map(step -> "  - " + ((PlanStep.ErrorStep) step).reason())
				.collect(Collectors.joining("\n"));
	}

	private static String extractPending(Plan plan) {
		return plan.planSteps().stream()
				.filter(step -> step instanceof PlanStep.PendingActionStep)
				.map(step -> {
					PlanStep.PendingActionStep pending = (PlanStep.PendingActionStep) step;
					String params = Arrays.stream(pending.pendingParams())
							.map(p -> p.name() + ": " + p.message())
							.collect(Collectors.joining(", "));
					return "  - " + pending.actionId() + " [" + params + "]";
				})
				.collect(Collectors.joining("\n"));
	}
}

