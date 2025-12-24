package org.javai.springai.dsl.conversation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.javai.springai.dsl.exec.PlanResolver;
import org.javai.springai.dsl.plan.Plan;
import org.javai.springai.dsl.plan.PlanFormulationResult;
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

		PlanFormulationResult planningResult = planner.formulatePlan(userMessage, state);
		Plan plan = planningResult.plan();
		if (plan == null) {
			plan = new Plan("", List.of());
		}

		ConversationState nextState = new ConversationState(
				state.originalInstruction(),
				plan.pendingParams(), // Zero-length list if there are no pending params
				state.providedParams(),
				userMessage
		);
		stateStore.save(sessionId, nextState);

		return new ConversationTurnResult(plan, nextState, planningResult.actionRegistry());
	}

}

