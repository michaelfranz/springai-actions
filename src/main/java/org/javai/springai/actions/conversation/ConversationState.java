package org.javai.springai.actions.conversation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.javai.springai.actions.PlanStep.PendingParam;

/**
 * Captures compact conversational state needed to continue planning across turns.
 * Immutable, with convenience helpers to add provided parameters or update pendings.
 */
public record ConversationState(
		String originalInstruction,
		List<PendingParam> pendingParams,
		Map<String, Object> providedParams,
		String latestUserMessage
) {

	public static ConversationState initial(String originalInstruction) {
		return new ConversationState(originalInstruction, List.of(), Map.of(), null);
	}

	public ConversationState {
		pendingParams = pendingParams != null ? List.copyOf(pendingParams) : List.of();
		providedParams = providedParams != null ? Map.copyOf(providedParams) : Map.of();
	}

	public ConversationState withProvidedParam(String name, String value) {
		if (name == null || name.isBlank()) {
			return this;
		}
		Map<String, Object> updated = new HashMap<>(this.providedParams);
		updated.put(name, value);
		return new ConversationState(this.originalInstruction, this.pendingParams, Map.copyOf(updated), this.latestUserMessage);
	}

	public ConversationState withLatestUserMessage(String message) {
		return new ConversationState(this.originalInstruction, this.pendingParams, this.providedParams, message);
	}

	public ConversationState withPendingParams(List<PendingParam> pending) {
		return new ConversationState(this.originalInstruction, pending, this.providedParams, this.latestUserMessage);
	}

	public ConversationState withProvidedParams(Map<String, Object> provided) {
		Map<String, Object> updated = new HashMap<>(this.providedParams);
		if (provided != null) {
			for (Map.Entry<String, Object> entry : provided.entrySet()) {
				if (entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null) {
					updated.put(entry.getKey(), entry.getValue().toString());
				}
			}
		}
		return new ConversationState(this.originalInstruction, this.pendingParams, Map.copyOf(updated), this.latestUserMessage);
	}
}

