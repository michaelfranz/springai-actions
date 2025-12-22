package org.javai.springai.dsl.exec;

import java.util.List;

/**
 * Validated, executable plan.
 */
public record ResolvedPlan(List<ResolvedStep> steps) {
	public ResolvedPlan {
		steps = steps != null ? List.copyOf(steps) : List.of();
	}
}

