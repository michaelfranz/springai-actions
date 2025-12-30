package org.javai.springai.actions.conversation;

import java.util.List;
import java.util.Map;
import org.javai.springai.actions.plan.Plan;
import org.javai.springai.actions.plan.PlanStatus;
import org.javai.springai.actions.plan.PlanStep;

/**
 * Result of processing a single conversation turn.
 * <p>
 * Contains the bound plan (if any), pending parameters, and updated conversation state.
 *
 * @param plan the bound plan for this turn
 * @param state the updated conversation state
 * @param pendingParams parameters that still need to be provided
 * @param providedParams parameters that were provided in this turn
 */
public record ConversationTurnResult(
		Plan plan,
		ConversationState state,
		List<PlanStep.PendingParam> pendingParams,
		Map<String, Object> providedParams
) {

	public ConversationTurnResult {
		pendingParams = pendingParams != null ? List.copyOf(pendingParams) : List.of();
		providedParams = providedParams != null ? Map.copyOf(providedParams) : Map.of();
	}

	/**
	 * Get the status of the plan.
	 *
	 * @return the plan status, or ERROR if no plan
	 */
	public PlanStatus status() {
		return plan != null ? plan.status() : PlanStatus.ERROR;
	}
}
