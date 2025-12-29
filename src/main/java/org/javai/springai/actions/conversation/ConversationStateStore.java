package org.javai.springai.actions.conversation;

import java.util.Optional;

/**
 * Persistence contract for conversation state, keyed by session id.
 */
public interface ConversationStateStore {
	Optional<ConversationState> load(String sessionId);
	void save(String sessionId, ConversationState state);
}

