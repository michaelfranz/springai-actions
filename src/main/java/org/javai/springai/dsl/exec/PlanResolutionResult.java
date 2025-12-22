package org.javai.springai.dsl.exec;

import java.util.List;
import java.util.Objects;

/**
 * Outcome of resolving a plan against registered actions.
 */
public record PlanResolutionResult(
		ResolvedPlan resolvedPlan,
		List<PlanResolutionError> errors
) {

	public PlanResolutionResult {
		errors = errors != null ? List.copyOf(errors) : List.of();
	}

	public static PlanResolutionResult success(ResolvedPlan plan) {
		return new PlanResolutionResult(Objects.requireNonNull(plan, "resolvedPlan must not be null"), List.of());
	}

	public static PlanResolutionResult failure(List<PlanResolutionError> errors) {
		return new PlanResolutionResult(null, errors);
	}

	public boolean isSuccess() {
		return resolvedPlan != null && errors.isEmpty();
	}
}

