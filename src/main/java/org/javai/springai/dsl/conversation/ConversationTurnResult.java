package org.javai.springai.dsl.conversation;

import org.javai.springai.dsl.plan.Plan;

/**
 * Result of processing a single conversation turn: the plan produced and the updated state.
 */
public record ConversationTurnResult(Plan plan, ConversationState state) {
}

