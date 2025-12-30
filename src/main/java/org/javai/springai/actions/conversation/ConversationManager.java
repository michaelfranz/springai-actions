package org.javai.springai.actions.conversation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.javai.springai.actions.Plan;
import org.javai.springai.actions.PlanStep;
import org.javai.springai.actions.Planner;
import org.javai.springai.actions.internal.plan.PlanFormulationResult;

/**
 * Orchestrates conversation-aware planning across turns.
 * <p>
 * Manages conversation state, calls the planner, and tracks pending parameters
 * across multiple conversation turns.
 */
public class ConversationManager {

	private final Planner planner;
	private final ConversationStateStore stateStore;

	public ConversationManager(Planner planner, ConversationStateStore stateStore) {
		this.planner = Objects.requireNonNull(planner, "planner must not be null");
		this.stateStore = Objects.requireNonNull(stateStore, "stateStore must not be null");
	}

	/**
	 * Start or continue a conversation for the given session.
	 * <p>
	 * If no prior state exists, initializes a new conversation state;
	 * otherwise continues with the stored state.
	 *
	 * @param userMessage the user's message for this turn
	 * @param sessionId unique identifier for this conversation session
	 * @return the result of this conversation turn
	 */
	public ConversationTurnResult converse(String userMessage, String sessionId) {
		Objects.requireNonNull(userMessage, "userMessage must not be null");
		
		// Load prior state or initialize a new conversation
		Optional<ConversationState> prior = stateStore.load(sessionId);
		ConversationState state = prior
				.map(p -> p.withLatestUserMessage(userMessage))
				.orElse(ConversationState.initial(userMessage));

		// Formulate the plan (now returns a fully bound Plan)
		PlanFormulationResult planningResult = planner.formulatePlan(userMessage, state);
		Plan plan = planningResult.plan();
		if (plan == null) {
			plan = new Plan("", List.of());
		}

		// Extract pending parameters and provided params
		List<PlanStep.PendingParam> pending = plan.pendingParams();
		Map<String, Object> newlyProvided = Map.of();
		if (!plan.planSteps().isEmpty() && plan.planSteps().getFirst() instanceof PlanStep.PendingActionStep pendingStep) {
			newlyProvided = pendingStep.providedParams();
		}

		// Merge provided params with prior state
		Map<String, Object> mergedProvided = new HashMap<>(state.providedParams());
		for (Map.Entry<String, Object> entry : newlyProvided.entrySet()) {
			if (entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null) {
				mergedProvided.put(entry.getKey(), entry.getValue());
			}
		}

		// Update and save conversation state
		ConversationState nextState = new ConversationState(
				state.originalInstruction(),
				pending,
				Map.copyOf(mergedProvided),
				userMessage
		);
		stateStore.save(sessionId, nextState);

		return new ConversationTurnResult(plan, nextState, pending, newlyProvided);
	}
}
