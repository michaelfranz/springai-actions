package org.javai.springai.dsl.conversation;

import java.util.List;
import java.util.Map;

/**
 * Captures compact conversational state needed to continue planning across turns.
 * Immutable, with convenience helpers to add provided parameters or update pendings.
 */
public record ConversationState(
		String originalInstruction,
		List<PendingParamSnapshot> pendingParams,
		Map<String, String> providedParams,
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
		Map<String, String> updated = new java.util.HashMap<>(this.providedParams);
		updated.put(name, value);
		return new ConversationState(this.originalInstruction, this.pendingParams, Map.copyOf(updated), this.latestUserMessage);
	}

	public ConversationState withLatestUserMessage(String message) {
		return new ConversationState(this.originalInstruction, this.pendingParams, this.providedParams, message);
	}

	public ConversationState withPendingParams(List<PendingParamSnapshot> pending) {
		return new ConversationState(this.originalInstruction, pending, this.providedParams, this.latestUserMessage);
	}
}

