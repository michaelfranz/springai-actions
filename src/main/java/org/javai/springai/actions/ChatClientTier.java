package org.javai.springai.actions;

import org.springframework.ai.chat.client.ChatClient;

/**
 * A chat client with its maximum attempt configuration.
 *
 * <p>Represents a single tier in the retry/fallback chain. The framework
 * will make up to {@code maxAttempts} calls to this client before moving
 * to the next tier.</p>
 *
 * @param chatClient the Spring AI ChatClient
 * @param maxAttempts maximum attempts before moving to next tier (≥1)
 * @param modelId optional identifier for observability (e.g., "gpt-4.1-mini")
 */
public record ChatClientTier(
        ChatClient chatClient,
        int maxAttempts,
        String modelId
) {
    /**
     * Canonical constructor with validation.
     */
    public ChatClientTier {
        if (chatClient == null) {
            throw new IllegalArgumentException("chatClient must not be null");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
    }

    /**
     * Convenience constructor with default maxAttempts=1.
     *
     * @param chatClient the Spring AI ChatClient
     * @param modelId optional identifier for observability
     */
    public ChatClientTier(ChatClient chatClient, String modelId) {
        this(chatClient, 1, modelId);
    }

    /**
     * Convenience constructor with default maxAttempts=1 and no modelId.
     *
     * @param chatClient the Spring AI ChatClient
     */
    public ChatClientTier(ChatClient chatClient) {
        this(chatClient, 1, null);
    }
}

