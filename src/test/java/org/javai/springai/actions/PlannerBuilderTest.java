package org.javai.springai.actions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.javai.springai.actions.api.Action;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Unit tests for Planner.Builder, focusing on the tiered chat client API.
 */
class PlannerBuilderTest {

    @Test
    void defaultChatClientSetsClient() {
        ChatClient mockClient = Mockito.mock(ChatClient.class);

        Planner planner = Planner.builder()
                .defaultChatClient(mockClient)
                .actions(new DemoActions())
                .build();

        assertThat(planner).isNotNull();
    }

    @Test
    void defaultChatClientWithMaxAttemptsSetsClient() {
        ChatClient mockClient = Mockito.mock(ChatClient.class);

        Planner planner = Planner.builder()
                .defaultChatClient(mockClient, 3)
                .actions(new DemoActions())
                .build();

        assertThat(planner).isNotNull();
    }

    @Test
    void defaultChatClientWithModelIdSetsClient() {
        ChatClient mockClient = Mockito.mock(ChatClient.class);

        Planner planner = Planner.builder()
                .defaultChatClient(mockClient, 2, "gpt-4.1-mini")
                .actions(new DemoActions())
                .build();

        assertThat(planner).isNotNull();
    }

    @Test
    void defaultChatClientCalledTwiceThrows() {
        ChatClient mockClient1 = Mockito.mock(ChatClient.class);
        ChatClient mockClient2 = Mockito.mock(ChatClient.class);

        assertThatThrownBy(() -> Planner.builder()
                .defaultChatClient(mockClient1)
                .defaultChatClient(mockClient2)
                .actions(new DemoActions())
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("defaultChatClient() can only be called once");
    }

    @Test
    void fallbackChatClientWithoutDefaultThrows() {
        ChatClient mockClient = Mockito.mock(ChatClient.class);

        assertThatThrownBy(() -> Planner.builder()
                .fallbackChatClient(mockClient)
                .actions(new DemoActions())
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Must call defaultChatClient() before fallbackChatClient()");
    }

    @Test
    void fallbackChatClientAfterDefaultSucceeds() {
        ChatClient defaultClient = Mockito.mock(ChatClient.class);
        ChatClient fallbackClient = Mockito.mock(ChatClient.class);

        Planner planner = Planner.builder()
                .defaultChatClient(defaultClient, 2)
                .fallbackChatClient(fallbackClient)
                .actions(new DemoActions())
                .build();

        assertThat(planner).isNotNull();
    }

    @Test
    void multipleFallbackClientsSucceeds() {
        ChatClient defaultClient = Mockito.mock(ChatClient.class);
        ChatClient fallback1 = Mockito.mock(ChatClient.class);
        ChatClient fallback2 = Mockito.mock(ChatClient.class);

        Planner planner = Planner.builder()
                .defaultChatClient(defaultClient, 2, "gpt-4.1-mini")
                .fallbackChatClient(fallback1, 2, "gpt-4.1")
                .fallbackChatClient(fallback2, 1, "o3")
                .actions(new DemoActions())
                .build();

        assertThat(planner).isNotNull();
    }

    @Test
    void sameClientInstanceAsDefaultAndFallbackThrows() {
        ChatClient sameClient = Mockito.mock(ChatClient.class);

        assertThatThrownBy(() -> Planner.builder()
                .defaultChatClient(sameClient)
                .fallbackChatClient(sameClient)
                .actions(new DemoActions())
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("same ChatClient instance cannot be added to multiple tiers");
    }

    @Test
    void sameClientInstanceInMultipleFallbacksThrows() {
        ChatClient defaultClient = Mockito.mock(ChatClient.class);
        ChatClient fallbackClient = Mockito.mock(ChatClient.class);

        assertThatThrownBy(() -> Planner.builder()
                .defaultChatClient(defaultClient)
                .fallbackChatClient(fallbackClient)
                .fallbackChatClient(fallbackClient)  // Same instance again
                .actions(new DemoActions())
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("same ChatClient instance cannot be added to multiple tiers");
    }

    @Test
    void nullDefaultClientThrows() {
        assertThatThrownBy(() -> Planner.builder()
                .defaultChatClient(null)
                .actions(new DemoActions())
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("client must not be null");
    }

    @Test
    void nullFallbackClientThrows() {
        ChatClient defaultClient = Mockito.mock(ChatClient.class);

        assertThatThrownBy(() -> Planner.builder()
                .defaultChatClient(defaultClient)
                .fallbackChatClient(null)
                .actions(new DemoActions())
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("client must not be null");
    }

    @Test
    void zeroMaxAttemptsThrows() {
        ChatClient mockClient = Mockito.mock(ChatClient.class);

        assertThatThrownBy(() -> Planner.builder()
                .defaultChatClient(mockClient, 0)
                .actions(new DemoActions())
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxAttempts must be >= 1");
    }

    @Test
    void negativeMaxAttemptsThrows() {
        ChatClient mockClient = Mockito.mock(ChatClient.class);

        assertThatThrownBy(() -> Planner.builder()
                .defaultChatClient(mockClient, -1)
                .actions(new DemoActions())
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxAttempts must be >= 1");
    }

    static class DemoActions {
        @Action(description = "Demo action")
        public void demo(String input) {
            // no-op
        }
    }
}

