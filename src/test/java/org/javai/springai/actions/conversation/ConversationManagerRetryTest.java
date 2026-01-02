package org.javai.springai.actions.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.javai.springai.actions.AttemptOutcome;
import org.javai.springai.actions.PlanStatus;
import org.javai.springai.actions.Planner;
import org.javai.springai.actions.api.Action;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Tests for ConversationManager integration with the retry/fallback mechanism.
 */
class ConversationManagerRetryTest {

    private static final String VALID_JSON_PLAN = """
            {
                "message": "Executing demo action",
                "steps": [
                    {
                        "actionId": "demo",
                        "description": "Run the demo",
                        "parameters": { "input": "test value" }
                    }
                ]
            }
            """;

    private static final String MALFORMED_JSON = "{{{";

    @Test
    void conversationTurnResultIncludesPlanningMetrics() {
        ChatClient mockClient = createMockClient(VALID_JSON_PLAN);

        Planner planner = Planner.builder()
                .defaultChatClient(mockClient, 1, "gpt-4.1-mini")
                .actions(new DemoActions())
                .build();

        ConversationManager manager = new ConversationManager(
                planner,
                new InMemoryConversationStateStore()
        );

        ConversationTurnResult result = manager.converse("do something", "session-1");

        assertThat(result.planningMetrics()).isNotNull();
        assertThat(result.planningMetrics().succeeded()).isTrue();
        assertThat(result.planningMetrics().totalAttempts()).isEqualTo(1);
        assertThat(result.planningMetrics().successfulModelId()).isEqualTo("gpt-4.1-mini");
    }

    @Test
    void metricsShowRetryOnParseFailure() {
        ChatClient mockClient = mock(ChatClient.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockClient.prompt().call().content())
                .thenReturn(MALFORMED_JSON)
                .thenReturn(VALID_JSON_PLAN);

        Planner planner = Planner.builder()
                .defaultChatClient(mockClient, 3, "test-model")
                .actions(new DemoActions())
                .build();

        ConversationManager manager = new ConversationManager(
                planner,
                new InMemoryConversationStateStore()
        );

        ConversationTurnResult result = manager.converse("do something", "session-1");

        assertThat(result.status()).isEqualTo(PlanStatus.READY);
        assertThat(result.planningMetrics().totalAttempts()).isEqualTo(2);
        assertThat(result.planningMetrics().attempts().get(0).outcome())
                .isEqualTo(AttemptOutcome.PARSE_FAILED);
        assertThat(result.planningMetrics().attempts().get(1).outcome())
                .isEqualTo(AttemptOutcome.SUCCESS);
    }

    @Test
    void metricsShowFallbackToSecondTier() {
        ChatClient tier1Mock = createMockClient(MALFORMED_JSON);
        ChatClient tier2Mock = createMockClient(VALID_JSON_PLAN);

        Planner planner = Planner.builder()
                .defaultChatClient(tier1Mock, 2, "cheap-model")
                .fallbackChatClient(tier2Mock, 1, "expensive-model")
                .actions(new DemoActions())
                .build();

        ConversationManager manager = new ConversationManager(
                planner,
                new InMemoryConversationStateStore()
        );

        ConversationTurnResult result = manager.converse("do something", "session-1");

        assertThat(result.status()).isEqualTo(PlanStatus.READY);
        assertThat(result.planningMetrics().successfulModelId()).isEqualTo("expensive-model");
        assertThat(result.planningMetrics().totalAttempts()).isEqualTo(3);
        assertThat(result.planningMetrics().tiersAttempted()).isEqualTo(2);
    }

    @Test
    void metricsPreservedAcrossMultipleTurns() {
        ChatClient mockClient = createMockClient(VALID_JSON_PLAN);

        Planner planner = Planner.builder()
                .defaultChatClient(mockClient, 1, "gpt-4.1-mini")
                .actions(new DemoActions())
                .build();

        ConversationManager manager = new ConversationManager(
                planner,
                new InMemoryConversationStateStore()
        );

        // First turn
        ConversationTurnResult turn1 = manager.converse("first message", "session-1");
        assertThat(turn1.planningMetrics()).isNotNull();
        assertThat(turn1.planningMetrics().totalAttempts()).isEqualTo(1);

        // Second turn
        ConversationTurnResult turn2 = manager.converse("second message", "session-1");
        assertThat(turn2.planningMetrics()).isNotNull();
        assertThat(turn2.planningMetrics().totalAttempts()).isEqualTo(1);
    }

    @Test
    void errorPlanStillIncludesMetrics() {
        ChatClient mockClient = createMockClient(MALFORMED_JSON);

        Planner planner = Planner.builder()
                .defaultChatClient(mockClient, 2, "test-model")
                .actions(new DemoActions())
                .build();

        ConversationManager manager = new ConversationManager(
                planner,
                new InMemoryConversationStateStore()
        );

        ConversationTurnResult result = manager.converse("do something", "session-1");

        assertThat(result.status()).isEqualTo(PlanStatus.ERROR);
        assertThat(result.planningMetrics()).isNotNull();
        assertThat(result.planningMetrics().succeeded()).isFalse();
        assertThat(result.planningMetrics().totalAttempts()).isEqualTo(2);
    }

    @Test
    void dryRunReturnsEmptyMetrics() {
        // No chat client configured - will be dry run
        Planner planner = Planner.builder()
                .actions(new DemoActions())
                .build();

        ConversationManager manager = new ConversationManager(
                planner,
                new InMemoryConversationStateStore()
        );

        ConversationTurnResult result = manager.converse("do something", "session-1");

        assertThat(result.planningMetrics()).isNotNull();
        assertThat(result.planningMetrics().totalAttempts()).isEqualTo(0);
        assertThat(result.planningMetrics().attempts()).isEmpty();
    }

    // Helper methods

    private ChatClient createMockClient(String response) {
        ChatClient mockClient = mock(ChatClient.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockClient.prompt().call().content()).thenReturn(response);
        return mockClient;
    }

    static class DemoActions {
        @Action(description = "Demo action")
        public void demo(String input) {
            // no-op
        }
    }
}

