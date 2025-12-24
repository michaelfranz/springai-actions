package org.javai.springai.dsl.conversation;

import java.util.Objects;
import org.javai.springai.dsl.act.ActionRegistry;
import org.javai.springai.dsl.exec.PlanResolutionResult;
import org.javai.springai.dsl.exec.PlanResolver;
import org.javai.springai.dsl.plan.Plan;
import org.javai.springai.dsl.plan.PlanStatus;

/**
 * Result of processing a single conversation turn: the plan produced and the updated state.
 * Status is derived from the plan when needed, and a convenience resolver is provided when READY.
 */
public record ConversationTurnResult(Plan plan, ConversationState state, ActionRegistry actionRegistry) {

	public PlanStatus status() {
		return plan != null ? plan.status() : PlanStatus.ERROR;
	}

	/**
	 * Resolve the plan using the provided resolver. Precondition: status() must be READY,
	 * otherwise an IllegalStateException is thrown.
	 */
	public PlanResolutionResult resolve(PlanResolver resolver) {
		Objects.requireNonNull(resolver, "resolver must not be null");
		if (status() != PlanStatus.READY) {
			throw new IllegalStateException("Plan is not READY; status=" + status());
		}
		return resolver.resolve(plan, actionRegistry);
	}
}

