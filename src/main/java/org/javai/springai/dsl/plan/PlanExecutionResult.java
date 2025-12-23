package org.javai.springai.dsl.plan;

/**
 * Rich result for a planning invocation, including the prompt preview.
 */
import org.javai.springai.dsl.act.ActionRegistry;

public record PlanExecutionResult(
		String llmResponse,
		Plan plan,
		PromptPreview promptPreview,
		boolean dryRun,
		ActionRegistry actionRegistry
) {
}

