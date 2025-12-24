package org.javai.springai.dsl.conversation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.javai.springai.dsl.exec.PlanResolver;
import org.javai.springai.dsl.plan.Plan;
import org.javai.springai.dsl.plan.PlanFormulationResult;
import org.javai.springai.dsl.plan.PlanStep;
import org.javai.springai.dsl.plan.Planner;

/**
 * Orchestrates conversation-aware planning across turns.
 *
 * This is a skeleton to be filled out: it should load/save ConversationState,
 * build retry addenda, call the planner, derive next state from the returned plan,
 * and branch based on READY/PENDING/ERROR status.
 */
public class ConversationManager {

	private final Planner planner;
	@SuppressWarnings("unused")
	private final PlanResolver resolver;
	private final ConversationStateStore stateStore;

	public ConversationManager(Planner planner, PlanResolver resolver, ConversationStateStore stateStore) {
		this.planner = Objects.requireNonNull(planner, "planner must not be null");
		this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
		this.stateStore = Objects.requireNonNull(stateStore, "stateStore must not be null");
	}

	/**
	 * Start or continue a conversation for the given session. If no prior state exists,
	 * initialize a new conversation state; otherwise continue with the stored state.
	 */
	public ConversationTurnResult converse(String userMessage, String sessionId) {
		// Load prior state or initialize a new conversation
		Optional<ConversationState> prior = stateStore.load(sessionId);
		ConversationState state = prior
				.map(p -> p.withLatestUserMessage(userMessage))
				.orElse(ConversationState.initial(userMessage));

		// Call the planner with the conversation state (planner may ignore state until fully wired)
		PlanFormulationResult planResult = planner.formulatePlan(userMessage, state);
		Plan plan = planResult.plan();

		// Derive pending snapshots from the returned plan
		List<PendingParamSnapshot> pendings = extractPendings(plan);

		ConversationState nextState = new ConversationState(
				state.originalInstruction(),
				pendings,
				state.providedParams(),
				userMessage
		);
		stateStore.save(sessionId, nextState);

		return new ConversationTurnResult(plan, nextState);
	}

	private List<PendingParamSnapshot> extractPendings(Plan plan) {
		if (plan == null || plan.planSteps() == null) {
			return List.of();
		}
		List<PendingParamSnapshot> out = new ArrayList<>();
		for (PlanStep step : plan.planSteps()) {
			if (step instanceof PlanStep.PendingActionStep pending) {
				String actionId = pending.actionId();
				if (pending.pendingParams() != null) {
					for (PlanStep.PendingParam p : pending.pendingParams()) {
						out.add(new PendingParamSnapshot(actionId, p.name(), p.message()));
					}
				}
			}
		}
		return out;
	}
}

