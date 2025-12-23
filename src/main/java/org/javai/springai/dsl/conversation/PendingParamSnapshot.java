package org.javai.springai.dsl.conversation;

/**
 * Snapshot of a pending parameter that needs to be satisfied across turns.
 */
public record PendingParamSnapshot(
		String actionId,
		String paramName,
		String message
) {
}

