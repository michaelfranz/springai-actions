package org.javai.springai.actions.conversation;

import java.util.List;
import java.util.Map;
import org.javai.springai.actions.exec.ResolvedPlan;
import org.javai.springai.actions.plan.PlanStatus;
import org.javai.springai.actions.plan.PlanStep;

/**
 * Result of processing a single conversation turn: the resolved plan (if any), pending items, and updated state.
 */
public record ConversationTurnResult(ResolvedPlan resolvedPlan,
		ConversationState state,
		List<PlanStep.PendingParam> pendingParams,
		Map<String, Object> providedParams) {

	public ConversationTurnResult {
		pendingParams = pendingParams != null ? List.copyOf(pendingParams) : List.of();
		providedParams = providedParams != null ? Map.copyOf(providedParams) : Map.of();
	}

	public PlanStatus status() {
		return resolvedPlan != null ? resolvedPlan.status() : PlanStatus.ERROR;
	}
}

