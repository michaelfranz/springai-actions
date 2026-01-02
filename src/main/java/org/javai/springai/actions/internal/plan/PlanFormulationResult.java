package org.javai.springai.actions.internal.plan;

import org.javai.springai.actions.Plan;
import org.javai.springai.actions.PlanningMetrics;
import org.javai.springai.actions.internal.bind.ActionRegistry;
import org.springframework.lang.Nullable;

/**
 * Rich result for a plan formulation invocation, including the prompt preview
 * and planning metrics.
 *
 * @param llmResponse the raw LLM response string
 * @param plan the parsed and resolved plan
 * @param promptPreview the system and user prompts sent to the LLM
 * @param dryRun whether this was a dry-run (no LLM call made)
 * @param actionRegistry the action registry used for resolution
 * @param planningMetrics metrics from the retry/fallback mechanism (nullable for backward compat)
 */
public record PlanFormulationResult(
		String llmResponse,
		Plan plan,
		PromptPreview promptPreview,
		boolean dryRun,
		ActionRegistry actionRegistry,
		@Nullable PlanningMetrics planningMetrics
) {
	/**
	 * Backward-compatible constructor without planning metrics.
	 *
	 * <p>Used by existing code that doesn't yet support the retry mechanism.</p>
	 */
	public PlanFormulationResult(
			String llmResponse,
			Plan plan,
			PromptPreview promptPreview,
			boolean dryRun,
			ActionRegistry actionRegistry
	) {
		this(llmResponse, plan, promptPreview, dryRun, actionRegistry, null);
	}
}
