package org.javai.springai.actions.internal.plan;

import org.javai.springai.actions.Plan;
import org.javai.springai.actions.internal.bind.ActionRegistry;

/**
 * Rich result for a plan formulation invocation, including the prompt preview.
 */

public record PlanFormulationResult(
		String llmResponse,
		Plan plan,
		PromptPreview promptPreview,
		boolean dryRun,
		ActionRegistry actionRegistry
) {
}

