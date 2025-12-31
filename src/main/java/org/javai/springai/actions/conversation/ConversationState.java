package org.javai.springai.actions.conversation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.javai.springai.actions.PlanStep.PendingParam;

/**
 * Captures compact conversational state needed to continue planning across turns.
 * 
 * <p>Immutable record that maintains:</p>
 * <ul>
 *   <li>The original user instruction</li>
 *   <li>Pending parameters awaiting clarification</li>
 *   <li>Parameters provided across turns</li>
 *   <li>The current working context (e.g., a Query being refined)</li>
 *   <li>History of prior working contexts</li>
 * </ul>
 * 
 * <p>The working context enables multi-turn refinement where users can
 * reference prior results ("filter that by...", "add 2 more").</p>
 * 
 * @param originalInstruction the user's original request
 * @param pendingParams parameters still awaiting values
 * @param providedParams parameters provided so far
 * @param latestUserMessage the most recent user message
 * @param workingContext the current working object being refined (may be null)
 * @param turnHistory prior working contexts (capped by config)
 */
public record ConversationState(
		String originalInstruction,
		List<PendingParam> pendingParams,
		Map<String, Object> providedParams,
		String latestUserMessage,
		WorkingContext<?> workingContext,
		List<WorkingContext<?>> turnHistory
) {

	/**
	 * Creates an initial state for a new conversation.
	 * 
	 * @param originalInstruction the user's first message
	 * @return a new conversation state
	 */
	public static ConversationState initial(String originalInstruction) {
		return new ConversationState(
				originalInstruction, List.of(), Map.of(), null, null, List.of());
	}

	public ConversationState {
		pendingParams = pendingParams != null ? List.copyOf(pendingParams) : List.of();
		providedParams = providedParams != null ? Map.copyOf(providedParams) : Map.of();
		turnHistory = turnHistory != null ? List.copyOf(turnHistory) : List.of();
	}

	/**
	 * Creates a copy with an additional provided parameter.
	 */
	public ConversationState withProvidedParam(String name, String value) {
		if (name == null || name.isBlank()) {
			return this;
		}
		Map<String, Object> updated = new HashMap<>(this.providedParams);
		updated.put(name, value);
		return new ConversationState(
				this.originalInstruction, this.pendingParams, Map.copyOf(updated),
				this.latestUserMessage, this.workingContext, this.turnHistory);
	}

	/**
	 * Creates a copy with an updated latest user message.
	 */
	public ConversationState withLatestUserMessage(String message) {
		return new ConversationState(
				this.originalInstruction, this.pendingParams, this.providedParams,
				message, this.workingContext, this.turnHistory);
	}

	/**
	 * Creates a copy with updated pending parameters.
	 */
	public ConversationState withPendingParams(List<PendingParam> pending) {
		return new ConversationState(
				this.originalInstruction, pending, this.providedParams,
				this.latestUserMessage, this.workingContext, this.turnHistory);
	}

	/**
	 * Creates a copy with merged provided parameters.
	 */
	public ConversationState withProvidedParams(Map<String, Object> provided) {
		Map<String, Object> updated = new HashMap<>(this.providedParams);
		if (provided != null) {
			for (Map.Entry<String, Object> entry : provided.entrySet()) {
				if (entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null) {
					updated.put(entry.getKey(), entry.getValue().toString());
				}
			}
		}
		return new ConversationState(
				this.originalInstruction, this.pendingParams, Map.copyOf(updated),
				this.latestUserMessage, this.workingContext, this.turnHistory);
	}

	/**
	 * Creates a copy with a new working context.
	 * 
	 * <p>The current working context (if any) is pushed to history.</p>
	 * 
	 * @param newContext the new working context
	 * @param maxHistorySize maximum history size (older entries are removed)
	 * @return updated state with new working context
	 */
	public ConversationState withWorkingContext(WorkingContext<?> newContext, int maxHistorySize) {
		List<WorkingContext<?>> newHistory;
		if (this.workingContext != null) {
			// Push current to history
			newHistory = new ArrayList<>(this.turnHistory);
			newHistory.add(this.workingContext);
			// Cap history size
			while (newHistory.size() > maxHistorySize) {
				newHistory.remove(0);
			}
			newHistory = List.copyOf(newHistory);
		} else {
			newHistory = this.turnHistory;
		}
		return new ConversationState(
				this.originalInstruction, this.pendingParams, this.providedParams,
				this.latestUserMessage, newContext, newHistory);
	}

	/**
	 * Creates a copy with just the working context updated (no history push).
	 * 
	 * <p>Use this when updating the current context without starting a new turn.</p>
	 */
	public ConversationState withWorkingContext(WorkingContext<?> context) {
		return new ConversationState(
				this.originalInstruction, this.pendingParams, this.providedParams,
				this.latestUserMessage, context, this.turnHistory);
	}

	/**
	 * Creates an empty/expired state.
	 * 
	 * @return an empty state suitable for "start over" scenarios
	 */
	public static ConversationState empty() {
		return new ConversationState(null, List.of(), Map.of(), null, null, List.of());
	}

	/**
	 * Checks if this state has a working context.
	 */
	public boolean hasWorkingContext() {
		return workingContext != null;
	}

	/**
	 * Gets the most recent item from turn history.
	 * 
	 * @return the most recent prior context, or null if history is empty
	 */
	public WorkingContext<?> lastHistoryEntry() {
		return turnHistory.isEmpty() ? null : turnHistory.get(turnHistory.size() - 1);
	}
}
