package org.javai.springai.dsl.conversation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.javai.springai.dsl.exec.PlanResolver;
import org.javai.springai.dsl.exec.ResolvedPlan;
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
		Objects.requireNonNull(userMessage, "userMessage must not be null");
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
		List<PlanStep.PendingParam> pending = plan.pendingParams();
		Map<String, Object> newlyProvided = Map.of();
		if (!plan.planSteps().isEmpty() && plan.planSteps().getFirst() instanceof PlanStep.PendingActionStep pendingStep) {
			newlyProvided = pendingStep.providedParams();
		}

		Map<String, Object> mergedProvided = new HashMap<>(state.providedParams());
		for (Map.Entry<String, Object> entry : newlyProvided.entrySet()) {
			if (entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null) {
				mergedProvided.put(entry.getKey(), entry.getValue());
			}
		}

		ResolvedPlan resolvedPlan = resolver.resolve(plan, planningResult.actionRegistry());

		ConversationState nextState = new ConversationState(
				state.originalInstruction(),
				pending, // Zero-length list if there are no pending params
				Map.copyOf(mergedProvided),
				userMessage
		);
		stateStore.save(sessionId, nextState);

		return new ConversationTurnResult(resolvedPlan, nextState, pending, newlyProvided);
	}

}

