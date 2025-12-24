package org.javai.springai.dsl.plan;

import java.util.List;
import org.javai.springai.sxl.SxlNode;

/**
 * A typed version of plan that an application can work with
 * @param assistantMessage a model-generated message to accompany the plan
 * @param planSteps are the steps of the plan
 */
public record Plan(String assistantMessage, List<PlanStep> planSteps) {

	public static Plan of(SxlNode planNode) {
		return PlanNodeVisitor.generate(planNode);
	}

	/**
	 * Convenience to extract all pending parameters across all pending action steps.
	 * Never null; returns an empty list if none are pending.
	 */
	public List<PlanStep.PendingParam> pendingParams() {
		if (planSteps == null || planSteps.isEmpty()) {
			return List.of();
		}
		return planSteps.stream()
				.filter(ps -> ps instanceof PlanStep.PendingActionStep)
				.flatMap(ps -> {
					PlanStep.PendingActionStep pending = (PlanStep.PendingActionStep) ps;
					PlanStep.PendingParam[] params = pending.pendingParams();
					return params == null ? java.util.stream.Stream.empty() : java.util.Arrays.stream(params);
				})
				.toList();
	}

	public boolean hasPending() {
		return planSteps != null && planSteps.stream().anyMatch(ps -> ps instanceof PlanStep.PendingActionStep);
	}

	public boolean hasError() {
		return planSteps != null && planSteps.stream().anyMatch(ps -> ps instanceof PlanStep.ErrorStep);
	}

	/**
	 * Derive the overall plan status based on contained steps.
	 * If there are no steps, treat as ERROR.
	 */
	public PlanStatus status() {
		if (planSteps == null || planSteps.isEmpty()) {
			return PlanStatus.ERROR;
		}
		if (hasPending()) {
			return PlanStatus.PENDING;
		}
		if (hasError()) {
			return PlanStatus.ERROR;
		}
		return PlanStatus.READY;
	}
}
