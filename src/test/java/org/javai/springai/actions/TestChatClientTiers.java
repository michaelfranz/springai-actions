package org.javai.springai.actions;

import org.springframework.ai.chat.client.ChatClient;

/**
 * Test helper for creating ChatClientTier instances.
 */
public class TestChatClientTiers {

	public static ChatClientTier tier(ChatClient client, int maxAttempts, String modelId) {
		return new ChatClientTier(client, maxAttempts, modelId);
	}

	public static ChatClientTier tier(ChatClient client) {
		return new ChatClientTier(client, 1, null);
	}
}

