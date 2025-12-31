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
 * 
 * <p>Manages conversation state, calls the planner, and tracks pending parameters
 * across multiple conversation turns.</p>
 * 
 * <h2>Two Usage Modes</h2>
 * 
 * <h3>1. Store-Based (Legacy)</h3>
 * <p>Framework manages persistence via {@link ConversationStateStore}:</p>
 * <pre>{@code
 * ConversationManager manager = new ConversationManager(planner, stateStore);
 * ConversationTurnResult result = manager.converse(userMessage, sessionId);
 * }</pre>
 * 
 * <h3>2. Blob-Based (Recommended)</h3>
 * <p>Application manages persistence; framework provides opaque blobs:</p>
 * <pre>{@code
 * ConversationManager manager = new ConversationManager(planner, serializer, typeRegistry, config);
 * ConversationTurnResult result = manager.converse(userMessage, priorBlob);
 * byte[] blobToStore = result.blob();  // Application stores this
 * }</pre>
 */
public class ConversationManager {

	private final Planner planner;
	private final ConversationStateStore stateStore;
	private final ConversationStateSerializer serializer;
	private final PayloadTypeRegistry typeRegistry;
	private final ConversationStateConfig config;

	/**
	 * Creates a ConversationManager with store-based persistence (legacy mode).
	 * 
	 * @param planner the planner for formulating plans
	 * @param stateStore the store for persisting conversation state
	 */
	public ConversationManager(Planner planner, ConversationStateStore stateStore) {
		this.planner = Objects.requireNonNull(planner, "planner must not be null");
		this.stateStore = Objects.requireNonNull(stateStore, "stateStore must not be null");
		this.serializer = null;
		this.typeRegistry = null;
		this.config = ConversationStateConfig.defaults();
	}

	/**
	 * Creates a ConversationManager with blob-based persistence (recommended).
	 * 
	 * <p>In this mode, the application is responsible for storing and retrieving
	 * the blob. The framework provides versioning, integrity checking, and migration.</p>
	 * 
	 * @param planner the planner for formulating plans
	 * @param serializer the serializer for blob creation
	 * @param typeRegistry registry for payload type resolution
	 * @param config configuration for state management
	 */
	public ConversationManager(
			Planner planner,
			ConversationStateSerializer serializer,
			PayloadTypeRegistry typeRegistry,
			ConversationStateConfig config) {
		this.planner = Objects.requireNonNull(planner, "planner must not be null");
		this.serializer = Objects.requireNonNull(serializer, "serializer must not be null");
		this.typeRegistry = Objects.requireNonNull(typeRegistry, "typeRegistry must not be null");
		this.config = config != null ? config : ConversationStateConfig.defaults();
		this.stateStore = null;
	}

	/**
	 * Start or continue a conversation for the given session (store-based mode).
	 * 
	 * <p>If no prior state exists, initializes a new conversation state;
	 * otherwise continues with the stored state.</p>
	 *
	 * @param userMessage the user's message for this turn
	 * @param sessionId unique identifier for this conversation session
	 * @return the result of this conversation turn
	 * @throws IllegalStateException if not in store-based mode
	 */
	public ConversationTurnResult converse(String userMessage, String sessionId) {
		Objects.requireNonNull(userMessage, "userMessage must not be null");
		if (stateStore == null) {
			throw new IllegalStateException(
					"Session-based converse requires a ConversationStateStore. " +
					"Use converse(userMessage, priorBlob) for blob-based mode.");
		}
		
		// Load prior state or initialize a new conversation
		Optional<ConversationState> prior = stateStore.load(sessionId);
		ConversationState state = prior
				.map(p -> p.withLatestUserMessage(userMessage))
				.orElse(ConversationState.initial(userMessage));

		ConversationTurnResult result = processConversation(userMessage, state);
		
		// Save to store
		stateStore.save(sessionId, result.state());

		return result;
	}

	/**
	 * Process a conversation turn with blob-based state (recommended mode).
	 * 
	 * <p>The application provides the prior blob (from previous turn) and receives
	 * a new blob to store for the next turn.</p>
	 * 
	 * @param userMessage the user's message for this turn
	 * @param priorBlob the blob from the previous turn (null for new conversation)
	 * @return the result including a new blob for persistence
	 * @throws IllegalStateException if not in blob-based mode
	 * @throws ConversationStateSerializer.IntegrityException if blob is tampered
	 */
	public ConversationTurnResult converse(String userMessage, byte[] priorBlob) {
		Objects.requireNonNull(userMessage, "userMessage must not be null");
		if (serializer == null) {
			throw new IllegalStateException(
					"Blob-based converse requires a ConversationStateSerializer. " +
					"Use converse(userMessage, sessionId) for store-based mode.");
		}

		// Deserialize prior state or start fresh
		ConversationState state;
		if (priorBlob != null && priorBlob.length > 0) {
			state = serializer.deserialize(priorBlob, typeRegistry)
					.withLatestUserMessage(userMessage);
		} else {
			state = ConversationState.initial(userMessage);
		}

		ConversationTurnResult result = processConversation(userMessage, state);

		// Serialize to blob
		byte[] blob = serializer.serialize(result.state(), typeRegistry);

		return new ConversationTurnResult(
				result.plan(),
				result.state(),
				blob,
				result.pendingParams(),
				result.providedParams());
	}

	/**
	 * Creates an expired/empty state result.
	 * 
	 * <p>Use this when the user cancels, logs out, or wants to start over.
	 * The returned result contains an empty state and a corresponding blob.</p>
	 * 
	 * @return a result with empty state
	 */
	public ConversationTurnResult expire() {
		ConversationState empty = ConversationState.empty();
		byte[] blob = null;
		if (serializer != null) {
			blob = serializer.serialize(empty, typeRegistry);
		}
		return new ConversationTurnResult(
				new Plan("Session expired", List.of()),
				empty,
				blob,
				List.of(),
				Map.of());
	}

	/**
	 * Restores conversation state from a blob (for inspection/debugging).
	 * 
	 * @param blob the serialized blob
	 * @return the conversation state
	 * @throws IllegalStateException if not in blob-based mode
	 */
	public ConversationState fromBlob(byte[] blob) {
		if (serializer == null) {
			throw new IllegalStateException("fromBlob requires a ConversationStateSerializer");
		}
		if (blob == null || blob.length == 0) {
			return ConversationState.empty();
		}
		return serializer.deserialize(blob, typeRegistry);
	}

	/**
	 * Converts a blob to human-readable JSON for debugging.
	 * 
	 * @param blob the serialized blob
	 * @return pretty-printed JSON
	 * @throws IllegalStateException if not in blob-based mode
	 */
	public String toReadableJson(byte[] blob) {
		if (serializer == null) {
			throw new IllegalStateException("toReadableJson requires a ConversationStateSerializer");
		}
		if (blob == null || blob.length == 0) {
			return "{}";
		}
		return serializer.toReadableJson(blob);
	}

	/**
	 * Common conversation processing logic.
	 */
	private ConversationTurnResult processConversation(String userMessage, ConversationState state) {
		// Formulate the plan
		PlanFormulationResult planningResult = planner.formulatePlan(userMessage, state);
		Plan plan = planningResult.plan();
		if (plan == null) {
			plan = new Plan("", List.of());
		}

		// Extract pending parameters and provided params
		List<PlanStep.PendingParam> pending = plan.pendingParams();
		Map<String, Object> newlyProvided = Map.of();
		if (!plan.planSteps().isEmpty() && 
				plan.planSteps().getFirst() instanceof PlanStep.PendingActionStep pendingStep) {
			newlyProvided = pendingStep.providedParams();
		}

		// Merge provided params with prior state
		Map<String, Object> mergedProvided = new HashMap<>(state.providedParams());
		for (Map.Entry<String, Object> entry : newlyProvided.entrySet()) {
			if (entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null) {
				mergedProvided.put(entry.getKey(), entry.getValue());
			}
		}

		// Update conversation state (preserve workingContext and history)
		ConversationState nextState = new ConversationState(
				state.originalInstruction(),
				pending,
				Map.copyOf(mergedProvided),
				userMessage,
				state.workingContext(),
				state.turnHistory()
		);

		return new ConversationTurnResult(plan, nextState, null, pending, newlyProvided);
	}

	/**
	 * Gets the configuration.
	 */
	public ConversationStateConfig config() {
		return config;
	}
}
