package org.javai.springai.actions.plan;

/**
 * Rich result for a plan formulation invocation, including the prompt preview.
 */
import org.javai.springai.actions.bind.ActionRegistry;

public record PlanFormulationResult(
		String llmResponse,
		Plan plan,
		PromptPreview promptPreview,
		boolean dryRun,
		ActionRegistry actionRegistry
) {
}

