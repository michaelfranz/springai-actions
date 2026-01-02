package org.javai.springai.actions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Unit tests for the model fallback mechanism types.
 */
class ModelFallbackTypesTest {

    @Nested
    class AttemptOutcomeTest {

        @Test
        void allOutcomesAreDefined() {
            assertThat(AttemptOutcome.values())
                    .containsExactlyInAnyOrder(
                            AttemptOutcome.SUCCESS,
                            AttemptOutcome.VALIDATION_FAILED,
                            AttemptOutcome.PARSE_FAILED,
                            AttemptOutcome.NETWORK_ERROR
                    );
        }
    }

    @Nested
    class AttemptRecordTest {

        @Test
        void constructsValidRecord() {
            AttemptRecord record = new AttemptRecord(
                    "gpt-4.1-mini",
                    0,
                    1,
                    AttemptOutcome.SUCCESS,
                    150L,
                    null
            );

            assertThat(record.modelId()).isEqualTo("gpt-4.1-mini");
            assertThat(record.tierIndex()).isEqualTo(0);
            assertThat(record.attemptWithinTier()).isEqualTo(1);
            assertThat(record.outcome()).isEqualTo(AttemptOutcome.SUCCESS);
            assertThat(record.durationMillis()).isEqualTo(150L);
            assertThat(record.errorDetails()).isNull();
            assertThat(record.isSuccess()).isTrue();
        }

        @Test
        void constructsFailedRecord() {
            AttemptRecord record = new AttemptRecord(
                    "gpt-4.1-mini",
                    0,
                    2,
                    AttemptOutcome.PARSE_FAILED,
                    200L,
                    "Invalid JSON"
            );

            assertThat(record.isSuccess()).isFalse();
            assertThat(record.errorDetails()).isEqualTo("Invalid JSON");
        }

        @Test
        void rejectNegativeTierIndex() {
            assertThatThrownBy(() -> new AttemptRecord(
                    "model",
                    -1,
                    1,
                    AttemptOutcome.SUCCESS,
                    100L,
                    null
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tierIndex must be >= 0");
        }

        @Test
        void rejectZeroAttemptWithinTier() {
            assertThatThrownBy(() -> new AttemptRecord(
                    "model",
                    0,
                    0,
                    AttemptOutcome.SUCCESS,
                    100L,
                    null
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("attemptWithinTier must be >= 1");
        }

        @Test
        void rejectNullOutcome() {
            assertThatThrownBy(() -> new AttemptRecord(
                    "model",
                    0,
                    1,
                    null,
                    100L,
                    null
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("outcome must not be null");
        }

        @Test
        void rejectNegativeDuration() {
            assertThatThrownBy(() -> new AttemptRecord(
                    "model",
                    0,
                    1,
                    AttemptOutcome.SUCCESS,
                    -1L,
                    null
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("durationMillis must be >= 0");
        }

        @Test
        void allowsNullModelId() {
            AttemptRecord record = new AttemptRecord(
                    null,
                    0,
                    1,
                    AttemptOutcome.SUCCESS,
                    100L,
                    null
            );
            assertThat(record.modelId()).isNull();
        }
    }

    @Nested
    class PlanningMetricsTest {

        @Test
        void singleSuccessCreatesCorrectMetrics() {
            PlanningMetrics metrics = PlanningMetrics.singleSuccess("gpt-4.1", 250L);

            assertThat(metrics.successfulModelId()).isEqualTo("gpt-4.1");
            assertThat(metrics.totalAttempts()).isEqualTo(1);
            assertThat(metrics.attempts()).hasSize(1);
            assertThat(metrics.succeeded()).isTrue();

            AttemptRecord record = metrics.attempts().getFirst();
            assertThat(record.modelId()).isEqualTo("gpt-4.1");
            assertThat(record.tierIndex()).isEqualTo(0);
            assertThat(record.attemptWithinTier()).isEqualTo(1);
            assertThat(record.outcome()).isEqualTo(AttemptOutcome.SUCCESS);
            assertThat(record.durationMillis()).isEqualTo(250L);
        }

        @Test
        void emptyCreatesNoAttemptMetrics() {
            PlanningMetrics metrics = PlanningMetrics.empty();

            assertThat(metrics.successfulModelId()).isNull();
            assertThat(metrics.totalAttempts()).isEqualTo(0);
            assertThat(metrics.attempts()).isEmpty();
            assertThat(metrics.succeeded()).isFalse();
        }

        @Test
        void succeededReturnsFalseWhenNoSuccessfulModel() {
            PlanningMetrics metrics = new PlanningMetrics(
                    null,
                    2,
                    List.of(
                            new AttemptRecord("model1", 0, 1, AttemptOutcome.PARSE_FAILED, 100L, "error1"),
                            new AttemptRecord("model1", 0, 2, AttemptOutcome.PARSE_FAILED, 100L, "error2")
                    )
            );

            assertThat(metrics.succeeded()).isFalse();
        }

        @Test
        void tiersAttemptedCountsDistinctTiers() {
            PlanningMetrics metrics = new PlanningMetrics(
                    "gpt-4.1",
                    4,
                    List.of(
                            new AttemptRecord("gpt-4.1-mini", 0, 1, AttemptOutcome.PARSE_FAILED, 100L, "error"),
                            new AttemptRecord("gpt-4.1-mini", 0, 2, AttemptOutcome.PARSE_FAILED, 100L, "error"),
                            new AttemptRecord("gpt-4.1", 1, 1, AttemptOutcome.PARSE_FAILED, 100L, "error"),
                            new AttemptRecord("gpt-4.1", 1, 2, AttemptOutcome.SUCCESS, 100L, null)
                    )
            );

            assertThat(metrics.tiersAttempted()).isEqualTo(2);
        }

        @Test
        void finalAttemptReturnsLastRecord() {
            AttemptRecord first = new AttemptRecord("model", 0, 1, AttemptOutcome.PARSE_FAILED, 100L, "error");
            AttemptRecord last = new AttemptRecord("model", 0, 2, AttemptOutcome.SUCCESS, 150L, null);

            PlanningMetrics metrics = new PlanningMetrics("model", 2, List.of(first, last));

            assertThat(metrics.finalAttempt()).isEqualTo(last);
        }

        @Test
        void finalAttemptReturnsNullWhenEmpty() {
            PlanningMetrics metrics = PlanningMetrics.empty();
            assertThat(metrics.finalAttempt()).isNull();
        }

        @Test
        void defensivelyCopiesAttemptsList() {
            List<AttemptRecord> mutableList = new java.util.ArrayList<>();
            mutableList.add(new AttemptRecord("model", 0, 1, AttemptOutcome.SUCCESS, 100L, null));

            PlanningMetrics metrics = new PlanningMetrics("model", 1, mutableList);
            mutableList.clear();

            assertThat(metrics.attempts()).hasSize(1);
        }

        @Test
        void rejectNegativeTotalAttempts() {
            assertThatThrownBy(() -> new PlanningMetrics("model", -1, List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("totalAttempts must be >= 0");
        }

        @Test
        void handlesNullAttemptsList() {
            PlanningMetrics metrics = new PlanningMetrics("model", 0, null);
            assertThat(metrics.attempts()).isEmpty();
        }
    }

    @Nested
    class ChatClientTierTest {

        @Test
        void constructsWithAllParameters() {
            ChatClient mockClient = Mockito.mock(ChatClient.class);

            ChatClientTier tier = new ChatClientTier(mockClient, 3, "gpt-4.1");

            assertThat(tier.chatClient()).isSameAs(mockClient);
            assertThat(tier.maxAttempts()).isEqualTo(3);
            assertThat(tier.modelId()).isEqualTo("gpt-4.1");
        }

        @Test
        void constructsWithDefaultMaxAttempts() {
            ChatClient mockClient = Mockito.mock(ChatClient.class);

            ChatClientTier tier = new ChatClientTier(mockClient, "gpt-4.1");

            assertThat(tier.chatClient()).isSameAs(mockClient);
            assertThat(tier.maxAttempts()).isEqualTo(1);
            assertThat(tier.modelId()).isEqualTo("gpt-4.1");
        }

        @Test
        void constructsWithMinimalParameters() {
            ChatClient mockClient = Mockito.mock(ChatClient.class);

            ChatClientTier tier = new ChatClientTier(mockClient);

            assertThat(tier.chatClient()).isSameAs(mockClient);
            assertThat(tier.maxAttempts()).isEqualTo(1);
            assertThat(tier.modelId()).isNull();
        }

        @Test
        void rejectNullChatClient() {
            assertThatThrownBy(() -> new ChatClientTier(null, 1, "model"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("chatClient must not be null");
        }

        @Test
        void rejectZeroMaxAttempts() {
            ChatClient mockClient = Mockito.mock(ChatClient.class);

            assertThatThrownBy(() -> new ChatClientTier(mockClient, 0, "model"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxAttempts must be >= 1");
        }

        @Test
        void rejectNegativeMaxAttempts() {
            ChatClient mockClient = Mockito.mock(ChatClient.class);

            assertThatThrownBy(() -> new ChatClientTier(mockClient, -1, "model"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxAttempts must be >= 1");
        }
    }
}

