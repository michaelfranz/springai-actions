package org.javai.springai.actions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.internal.plan.PlanFormulationResult;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Integration tests for the model fallback mechanism.
 * 
 * <p>All tests use mocked ChatClients - no real LLM calls are made.</p>
 */
class ModelFallbackTest {

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

    private static final String UNKNOWN_ACTION_JSON = """
            {
                "message": "Executing unknown action",
                "steps": [
                    {
                        "actionId": "unknownAction",
                        "description": "This action doesn't exist",
                        "parameters": {}
                    }
                ]
            }
            """;

    @Test
    void singleTierSingleAttemptSuccess() {
        ChatClient mockClient = createMockClient(VALID_JSON_PLAN);

        Planner planner = Planner.builder()
                .defaultChatClient(mockClient, 1, "gpt-4.1-mini")
                .actions(new DemoActions())
                .build();

        PlanFormulationResult result = planner.formulatePlan("do something");

        assertThat(result.plan().status()).isEqualTo(PlanStatus.READY);
        assertThat(result.planningMetrics()).isNotNull();
        assertThat(result.planningMetrics().succeeded()).isTrue();
        assertThat(result.planningMetrics().totalAttempts()).isEqualTo(1);
        assertThat(result.planningMetrics().successfulModelId()).isEqualTo("gpt-4.1-mini");
    }

    @Test
    void singleTierMultipleAttemptsWithRecovery() {
        ChatClient mockClient = mock(ChatClient.class, Mockito.RETURNS_DEEP_STUBS);
        // First call fails, second succeeds
        when(mockClient.prompt().call().content())
                .thenReturn(MALFORMED_JSON)
                .thenReturn(VALID_JSON_PLAN);

        Planner planner = Planner.builder()
                .defaultChatClient(mockClient, 3, "gpt-4.1-mini")
                .actions(new DemoActions())
                .build();

        PlanFormulationResult result = planner.formulatePlan("do something");

        assertThat(result.plan().status()).isEqualTo(PlanStatus.READY);
        assertThat(result.planningMetrics().succeeded()).isTrue();
        assertThat(result.planningMetrics().totalAttempts()).isEqualTo(2);
        assertThat(result.planningMetrics().successfulModelId()).isEqualTo("gpt-4.1-mini");

        // Verify first attempt failed, second succeeded
        assertThat(result.planningMetrics().attempts()).hasSize(2);
        assertThat(result.planningMetrics().attempts().get(0).outcome())
                .isEqualTo(AttemptOutcome.PARSE_FAILED);
        assertThat(result.planningMetrics().attempts().get(1).outcome())
                .isEqualTo(AttemptOutcome.SUCCESS);
    }

    @Test
    void singleTierExhaustedReturnsError() {
        ChatClient mockClient = createMockClient(MALFORMED_JSON);

        Planner planner = Planner.builder()
                .defaultChatClient(mockClient, 2, "gpt-4.1-mini")
                .actions(new DemoActions())
                .build();

        PlanFormulationResult result = planner.formulatePlan("do something");

        assertThat(result.plan().status()).isEqualTo(PlanStatus.ERROR);
        assertThat(result.planningMetrics().succeeded()).isFalse();
        assertThat(result.planningMetrics().totalAttempts()).isEqualTo(2);
        assertThat(result.planningMetrics().successfulModelId()).isNull();

        // Both attempts failed
        assertThat(result.planningMetrics().attempts()).hasSize(2);
        assertThat(result.planningMetrics().attempts())
                .allMatch(a -> a.outcome() == AttemptOutcome.PARSE_FAILED);
    }

    @Test
    void fallbackToSecondTierOnFirstTierFailure() {
        ChatClient tier1Mock = createMockClient(MALFORMED_JSON);
        ChatClient tier2Mock = createMockClient(VALID_JSON_PLAN);

        Planner planner = Planner.builder()
                .defaultChatClient(tier1Mock, 2, "gpt-4.1-mini")
                .fallbackChatClient(tier2Mock, 1, "gpt-4.1")
                .actions(new DemoActions())
                .build();

        PlanFormulationResult result = planner.formulatePlan("do something");

        assertThat(result.plan().status()).isEqualTo(PlanStatus.READY);
        assertThat(result.planningMetrics().succeeded()).isTrue();
        assertThat(result.planningMetrics().totalAttempts()).isEqualTo(3);  // 2 on tier1 + 1 on tier2
        assertThat(result.planningMetrics().successfulModelId()).isEqualTo("gpt-4.1");
        assertThat(result.planningMetrics().tiersAttempted()).isEqualTo(2);

        // Verify attempt distribution via metrics (more reliable than verify() with deep stubs)
        long tier1Attempts = result.planningMetrics().attempts().stream()
                .filter(a -> a.tierIndex() == 0)
                .count();
        long tier2Attempts = result.planningMetrics().attempts().stream()
                .filter(a -> a.tierIndex() == 1)
                .count();
        assertThat(tier1Attempts).isEqualTo(2);
        assertThat(tier2Attempts).isEqualTo(1);
    }

    @Test
    void metricsRecordAllAttempts() {
        ChatClient tier1Mock = mock(ChatClient.class, Mockito.RETURNS_DEEP_STUBS);
        ChatClient tier2Mock = mock(ChatClient.class, Mockito.RETURNS_DEEP_STUBS);

        // Tier 1: fails twice
        when(tier1Mock.prompt().call().content())
                .thenReturn(MALFORMED_JSON)
                .thenReturn(MALFORMED_JSON);

        // Tier 2: fails once, then succeeds
        when(tier2Mock.prompt().call().content())
                .thenReturn(MALFORMED_JSON)
                .thenReturn(VALID_JSON_PLAN);

        Planner planner = Planner.builder()
                .defaultChatClient(tier1Mock, 2, "cheap-model")
                .fallbackChatClient(tier2Mock, 2, "expensive-model")
                .actions(new DemoActions())
                .build();

        PlanFormulationResult result = planner.formulatePlan("do something");

        assertThat(result.planningMetrics().totalAttempts()).isEqualTo(4);
        assertThat(result.planningMetrics().attempts()).hasSize(4);

        // Verify attempt details
        assertThat(result.planningMetrics().attempts().get(0))
                .satisfies(a -> {
                    assertThat(a.modelId()).isEqualTo("cheap-model");
                    assertThat(a.tierIndex()).isEqualTo(0);
                    assertThat(a.attemptWithinTier()).isEqualTo(1);
                    assertThat(a.outcome()).isEqualTo(AttemptOutcome.PARSE_FAILED);
                });

        assertThat(result.planningMetrics().attempts().get(2))
                .satisfies(a -> {
                    assertThat(a.modelId()).isEqualTo("expensive-model");
                    assertThat(a.tierIndex()).isEqualTo(1);
                    assertThat(a.attemptWithinTier()).isEqualTo(1);
                    assertThat(a.outcome()).isEqualTo(AttemptOutcome.PARSE_FAILED);
                });

        assertThat(result.planningMetrics().attempts().get(3))
                .satisfies(a -> {
                    assertThat(a.modelId()).isEqualTo("expensive-model");
                    assertThat(a.tierIndex()).isEqualTo(1);
                    assertThat(a.attemptWithinTier()).isEqualTo(2);
                    assertThat(a.outcome()).isEqualTo(AttemptOutcome.SUCCESS);
                });
    }

    @Test
    void allTiersExhaustedReturnsErrorWithFullMetrics() {
        ChatClient tier1Mock = createMockClient(MALFORMED_JSON);
        ChatClient tier2Mock = createMockClient(MALFORMED_JSON);
        ChatClient tier3Mock = createMockClient(MALFORMED_JSON);

        Planner planner = Planner.builder()
                .defaultChatClient(tier1Mock, 2, "cheap")
                .fallbackChatClient(tier2Mock, 2, "medium")
                .fallbackChatClient(tier3Mock, 1, "expensive")
                .actions(new DemoActions())
                .build();

        PlanFormulationResult result = planner.formulatePlan("do something");

        assertThat(result.plan().status()).isEqualTo(PlanStatus.ERROR);
        assertThat(result.planningMetrics().succeeded()).isFalse();
        assertThat(result.planningMetrics().totalAttempts()).isEqualTo(5);  // 2 + 2 + 1
        assertThat(result.planningMetrics().tiersAttempted()).isEqualTo(3);

        // All attempts recorded
        assertThat(result.planningMetrics().attempts()).hasSize(5);
    }

    @Test
    void parseFailureTriggersRetry() {
        ChatClient mockClient = mock(ChatClient.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockClient.prompt().call().content())
                .thenReturn("Not JSON at all")
                .thenReturn(VALID_JSON_PLAN);

        Planner planner = Planner.builder()
                .defaultChatClient(mockClient, 2, "test-model")
                .actions(new DemoActions())
                .build();

        PlanFormulationResult result = planner.formulatePlan("do something");

        assertThat(result.plan().status()).isEqualTo(PlanStatus.READY);
        assertThat(result.planningMetrics().attempts().get(0).outcome())
                .isEqualTo(AttemptOutcome.PARSE_FAILED);
        assertThat(result.planningMetrics().attempts().get(1).outcome())
                .isEqualTo(AttemptOutcome.SUCCESS);
    }

    @Test
    void validationFailureTriggersRetry() {
        ChatClient mockClient = mock(ChatClient.class, Mockito.RETURNS_DEEP_STUBS);
        // First response has unknown action (validation failure)
        // Second response is valid
        when(mockClient.prompt().call().content())
                .thenReturn(UNKNOWN_ACTION_JSON)
                .thenReturn(VALID_JSON_PLAN);

        Planner planner = Planner.builder()
                .defaultChatClient(mockClient, 2, "test-model")
                .actions(new DemoActions())
                .build();

        PlanFormulationResult result = planner.formulatePlan("do something");

        assertThat(result.plan().status()).isEqualTo(PlanStatus.READY);
        assertThat(result.planningMetrics().attempts().get(0).outcome())
                .isEqualTo(AttemptOutcome.VALIDATION_FAILED);
        assertThat(result.planningMetrics().attempts().get(1).outcome())
                .isEqualTo(AttemptOutcome.SUCCESS);
    }

    @Test
    void networkErrorTriggersRetry() {
        ChatClient mockClient = mock(ChatClient.class, Mockito.RETURNS_DEEP_STUBS);
        // First call throws, second succeeds
        when(mockClient.prompt().call().content())
                .thenThrow(new RuntimeException("Connection refused"))
                .thenReturn(VALID_JSON_PLAN);

        Planner planner = Planner.builder()
                .defaultChatClient(mockClient, 2, "test-model")
                .actions(new DemoActions())
                .build();

        PlanFormulationResult result = planner.formulatePlan("do something");

        assertThat(result.plan().status()).isEqualTo(PlanStatus.READY);
        assertThat(result.planningMetrics().attempts().get(0).outcome())
                .isEqualTo(AttemptOutcome.NETWORK_ERROR);
        assertThat(result.planningMetrics().attempts().get(0).errorDetails())
                .contains("Connection refused");
        assertThat(result.planningMetrics().attempts().get(1).outcome())
                .isEqualTo(AttemptOutcome.SUCCESS);
    }

    @Test
    void defaultChatClientWithNoFallbacksWorks() {
        ChatClient mockClient = createMockClient(VALID_JSON_PLAN);

        // Single tier with no fallbacks
        Planner planner = Planner.builder()
                .defaultChatClient(mockClient)
                .actions(new DemoActions())
                .build();

        PlanFormulationResult result = planner.formulatePlan("do something");

        assertThat(result.plan().status()).isEqualTo(PlanStatus.READY);
        assertThat(result.planningMetrics()).isNotNull();
        assertThat(result.planningMetrics().succeeded()).isTrue();
        assertThat(result.planningMetrics().totalAttempts()).isEqualTo(1);
    }

    @Test
    void dryRunReturnsEmptyMetrics() {
        Planner planner = Planner.builder()
                .actions(new DemoActions())
                // No chat client - will be dry run
                .build();

        PlanFormulationResult result = planner.formulatePlan("do something");

        assertThat(result.dryRun()).isTrue();
        assertThat(result.planningMetrics()).isNotNull();
        assertThat(result.planningMetrics().totalAttempts()).isEqualTo(0);
        assertThat(result.planningMetrics().attempts()).isEmpty();
    }

    @Test
    void attemptDurationIsRecorded() {
        ChatClient mockClient = createMockClient(VALID_JSON_PLAN);

        Planner planner = Planner.builder()
                .defaultChatClient(mockClient, 1, "test-model")
                .actions(new DemoActions())
                .build();

        PlanFormulationResult result = planner.formulatePlan("do something");

        assertThat(result.planningMetrics().attempts().get(0).durationMillis())
                .isGreaterThanOrEqualTo(0);
    }

    @Test
    void finalAttemptReturnsLastAttempt() {
        ChatClient mockClient = mock(ChatClient.class, Mockito.RETURNS_DEEP_STUBS);
        when(mockClient.prompt().call().content())
                .thenReturn(MALFORMED_JSON)
                .thenReturn(VALID_JSON_PLAN);

        Planner planner = Planner.builder()
                .defaultChatClient(mockClient, 2, "test-model")
                .actions(new DemoActions())
                .build();

        PlanFormulationResult result = planner.formulatePlan("do something");

        AttemptRecord finalAttempt = result.planningMetrics().finalAttempt();
        assertThat(finalAttempt).isNotNull();
        assertThat(finalAttempt.outcome()).isEqualTo(AttemptOutcome.SUCCESS);
        assertThat(finalAttempt.attemptWithinTier()).isEqualTo(2);
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

