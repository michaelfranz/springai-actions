package org.javai.springai.dsl.plan;

/**
 * Rich result for a planning invocation, including the prompt preview.
 */
public record PlanExecutionResult(
		Plan plan,
		PromptPreview promptPreview,
		boolean dryRun
) {
}

