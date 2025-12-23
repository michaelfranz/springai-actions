package org.javai.springai.dsl.conversation;

import java.util.Map;
import java.util.StringJoiner;

/**
 * Builds compact prompt addenda for conversation-aware re-planning.
 */
public final class ConversationPromptBuilder {

	private ConversationPromptBuilder() {
	}

	/**
	 * Builds a retry addendum to append to the system prompt when re-planning.
	 * Includes original instruction (or summary), provided params, pending items,
	 * and the latest user reply.
	 */
	public static String buildRetryAddendum(ConversationState state) {
		if (state == null) {
			return "";
		}

		StringBuilder sb = new StringBuilder();
		sb.append("Retrying planning.\n");

		if (state.originalInstruction() != null && !state.originalInstruction().isBlank()) {
			sb.append("Original instruction: ").append(state.originalInstruction()).append("\n");
		}

		if (!state.providedParams().isEmpty()) {
			StringJoiner provided = new StringJoiner(", ");
			for (Map.Entry<String, String> entry : state.providedParams().entrySet()) {
				provided.add(entry.getKey() + "=" + entry.getValue());
			}
			sb.append("Already provided: ").append(provided).append("\n");
		}

		if (!state.pendingParams().isEmpty()) {
			StringJoiner pending = new StringJoiner("; ");
			for (PendingParamSnapshot p : state.pendingParams()) {
				String label = (p.paramName() != null ? p.paramName() : "param");
				String msg = (p.message() != null ? p.message() : "");
				pending.add(label + (msg.isBlank() ? "" : " (" + msg + ")"));
			}
			sb.append("Pending: ").append(pending).append("\n");
		}

		if (state.latestUserMessage() != null && !state.latestUserMessage().isBlank()) {
			sb.append("Latest user reply: \"").append(state.latestUserMessage()).append("\"\n");
		}

		sb.append("Use the new reply only if it truly satisfies the pending items; otherwise emit PENDING. Do not guess. Use user-friendly phrasing when asking for missing info.\n");
		return sb.toString();
	}
}

