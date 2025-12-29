package org.javai.springai.actions.conversation;

import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import org.javai.springai.actions.plan.PlanStep;

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
	public static Optional<String> buildRetryAddendum(ConversationState state) {
		if (state == null || state.pendingParams().isEmpty()) {
			return Optional.empty();
		}

		StringBuilder sb = new StringBuilder();
		sb.append("Retrying planning.\n");

		if (state.originalInstruction() != null && !state.originalInstruction().isBlank()) {
			sb.append("Original instruction: ").append(state.originalInstruction()).append("\n");
		}

		if (!state.providedParams().isEmpty()) {
			StringJoiner provided = new StringJoiner(", ");
			for (Map.Entry<String, Object> entry : state.providedParams().entrySet()) {
				provided.add(entry.getKey() + "=" + entry.getValue());
			}
			sb.append("Already provided: ").append(provided).append("\n");
		}

		if (!state.pendingParams().isEmpty()) {
			StringJoiner pending = new StringJoiner("; ");
			for (PlanStep.PendingParam p : state.pendingParams()) {
				String label = (p.name() != null ? p.name() : "param");
				String msg = (p.message() != null ? p.message() : "");
				pending.add(label + (msg.isBlank() ? "" : " (" + msg + ")"));
			}
			sb.append("Pending: ").append(pending).append("\n");
		}

		if (state.latestUserMessage() != null && !state.latestUserMessage().isBlank()) {
			sb.append("Latest user reply: \"").append(state.latestUserMessage()).append("\"\n");
		}

		sb.append("Use the latest reply only to satisfy the pending items listed above; otherwise emit PENDING. Do not guess.\n");
		sb.append("Do not add new actions or parameters beyond those already provided or pending. All prior system instructions still apply (Plan DSL only, no prose, use only defined actions/params). Output must be a single Plan S-expression in the PLAN DSL shape: (P \"...\" (PS <action-id> (PA name \"value\") ...)) with P/PS/PA/PENDING/ERROR symbols only; never JSON or free-form or colon/dash syntax. Action ids must match the catalog exactly (e.g., exportControlChartToExcel).\n");
		return Optional.of(sb.toString());
	}
}

