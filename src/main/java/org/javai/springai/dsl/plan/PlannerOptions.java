package org.javai.springai.dsl.plan;

/**
 * Options for a single planning invocation.
 */
public record PlannerOptions(
		boolean dryRun,
		boolean capturePrompt
) {
	public static PlannerOptions defaults() {
		return new PlannerOptions(false, false);
	}

	public static PlannerOptions dryRunOptions() {
		return new PlannerOptions(true, true);
	}

	public static PlannerOptions capturePromptOptions() {
		return new PlannerOptions(false, true);
	}
}

