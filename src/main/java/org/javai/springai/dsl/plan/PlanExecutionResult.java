package org.javai.springai.dsl.plan;

import org.javai.springai.actions.execution.ExecutablePlan;

/**
 * Rich result for a planning invocation, including the prompt preview.
 */
public record PlanExecutionResult(
		ExecutablePlan plan,
		PromptPreview promptPreview,
		boolean dryRun
) {
}

