package org.javai.springai.dsl.plan;

/**
 * Rich result for a plan formulation invocation, including the prompt preview.
 */
import org.javai.springai.dsl.act.ActionRegistry;

public record PlanFormulationResult(
		String llmResponse,
		Plan plan,
		PromptPreview promptPreview,
		boolean dryRun,
		ActionRegistry actionRegistry
) {
}

