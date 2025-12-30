package org.javai.springai.actions.plan;

import java.util.Arrays;
import java.util.List;

/**
 * A validated plan ready for execution.
 * <p>
 * Plans are created by the {@link Planner} from natural language requests.
 * Check {@link #status()} to determine if the plan is ready for execution,
 * pending user input, or contains errors.
 *
 * @param assistantMessage LLM-generated message accompanying the plan
 * @param planSteps the steps of the plan
 */
public record Plan(String assistantMessage, List<PlanStep> planSteps) {

	/**
	 * Defensive copy constructor.
	 */
	public Plan {
		planSteps = planSteps != null ? List.copyOf(planSteps) : List.of();
	}

	/**
	 * Derive the overall plan status based on contained steps.
	 * <ul>
	 *   <li>{@link PlanStatus#READY} - All steps are action steps, ready to execute</li>
	 *   <li>{@link PlanStatus#PENDING} - One or more steps need additional parameters</li>
	 *   <li>{@link PlanStatus#ERROR} - Contains error steps or is empty</li>
	 * </ul>
	 */
	public PlanStatus status() {
		if (planSteps.isEmpty()) {
			return PlanStatus.ERROR;
		}
		if (hasError()) {
			return PlanStatus.ERROR;
		}
		if (hasPending()) {
			return PlanStatus.PENDING;
		}
		return PlanStatus.READY;
	}

	/**
	 * Check if the plan is ready for execution.
	 */
	public boolean isReady() {
		return status() == PlanStatus.READY;
	}

	/**
	 * Check if any steps are pending user input.
	 */
	public boolean hasPending() {
		return planSteps.stream().anyMatch(ps -> ps instanceof PlanStep.PendingActionStep);
	}

	/**
	 * Check if any steps are errors.
	 */
	public boolean hasError() {
		return planSteps.stream().anyMatch(ps -> ps instanceof PlanStep.ErrorStep);
	}

	/**
	 * Convenience to extract all pending parameters across all pending action steps.
	 * Never null; returns an empty list if none are pending.
	 */
	public List<PlanStep.PendingParam> pendingParams() {
		return planSteps.stream()
				.filter(ps -> ps instanceof PlanStep.PendingActionStep)
				.flatMap(ps -> {
					PlanStep.PendingActionStep pending = (PlanStep.PendingActionStep) ps;
					PlanStep.PendingParam[] params = pending.pendingParams();
					return params == null ? java.util.stream.Stream.empty() : Arrays.stream(params);
				})
				.toList();
	}

	/**
	 * Get the names of all pending parameters.
	 * Useful for quick checks without inspecting the full pending param objects.
	 */
	public List<String> pendingParameterNames() {
		return pendingParams().stream()
				.map(PlanStep.PendingParam::name)
				.toList();
	}
}
