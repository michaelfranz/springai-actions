package org.javai.springai.actions.conversation;

import java.util.List;
import java.util.Map;
import org.javai.springai.actions.Plan;
import org.javai.springai.actions.PlanStatus;
import org.javai.springai.actions.PlanStep;

/**
 * Result of processing a single conversation turn.
 * 
 * <p>Contains the bound plan (if any), pending parameters, updated conversation state,
 * and an opaque blob for persistence.</p>
 * 
 * <h2>Blob Persistence</h2>
 * <p>The {@code blob} field contains a serialized, versioned, and integrity-checked
 * representation of the conversation state. Applications are responsible for storing
 * this blob and passing it back on the next turn via 
 * {@link ConversationManager#converse(String, byte[])}.</p>
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * ConversationTurnResult result = manager.converse(userMessage, priorBlob);
 * 
 * // Store the blob for next turn
 * sessionRepository.save(sessionId, result.blob());
 * 
 * // Execute the plan if ready
 * if (result.status() == PlanStatus.READY) {
 *     executor.execute(result.plan());
 * }
 * }</pre>
 *
 * @param plan the bound plan for this turn
 * @param state the updated conversation state
 * @param blob opaque, serialized state for persistence (application stores this)
 * @param pendingParams parameters that still need to be provided
 * @param providedParams parameters that were provided in this turn
 */
public record ConversationTurnResult(
		Plan plan,
		ConversationState state,
		byte[] blob,
		List<PlanStep.PendingParam> pendingParams,
		Map<String, Object> providedParams
) {

	public ConversationTurnResult {
		pendingParams = pendingParams != null ? List.copyOf(pendingParams) : List.of();
		providedParams = providedParams != null ? Map.copyOf(providedParams) : Map.of();
		// Defensive copy of blob
		blob = blob != null ? blob.clone() : null;
	}

	/**
	 * Get the status of the plan.
	 *
	 * @return the plan status, or ERROR if no plan
	 */
	public PlanStatus status() {
		return plan != null ? plan.status() : PlanStatus.ERROR;
	}

	/**
	 * Returns a defensive copy of the blob.
	 * 
	 * @return copy of the blob, or null if not set
	 */
	@Override
	public byte[] blob() {
		return blob != null ? blob.clone() : null;
	}

	/**
	 * Checks if this result has a persistable blob.
	 * 
	 * @return true if blob is present
	 */
	public boolean hasBlob() {
		return blob != null && blob.length > 0;
	}

	/**
	 * Gets the current working context from the state, if present.
	 * 
	 * @return the working context, or null
	 */
	public WorkingContext<?> workingContext() {
		return state != null ? state.workingContext() : null;
	}
}
