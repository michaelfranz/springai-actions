package org.javai.springai.dsl.conversation;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory store for conversation state, intended for tests and local use.
 */
public class InMemoryConversationStateStore implements ConversationStateStore {

	private final Map<String, ConversationState> store = new ConcurrentHashMap<>();

	@Override
	public Optional<ConversationState> load(String sessionId) {
		return Optional.ofNullable(store.get(sessionId));
	}

	@Override
	public void save(String sessionId, ConversationState state) {
		store.put(sessionId, state);
	}
}

