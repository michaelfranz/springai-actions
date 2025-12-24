package org.javai.springai.dsl.exec;

import java.util.List;
import org.javai.springai.dsl.plan.PlanStatus;

/**
 * Validated plan with bound actions and arguments. May contain error steps; status indicates readiness.
 */
public record ResolvedPlan(List<ResolvedStep> steps) {
	public ResolvedPlan {
		steps = steps != null ? List.copyOf(steps) : List.of();
	}

	public PlanStatus status() {
		if (steps == null || steps.isEmpty()) {
			return PlanStatus.ERROR;
		}
		boolean hasError = steps.stream().anyMatch(s -> s instanceof ResolvedStep.ErrorStep);
		return hasError ? PlanStatus.ERROR : PlanStatus.READY;
	}
}

