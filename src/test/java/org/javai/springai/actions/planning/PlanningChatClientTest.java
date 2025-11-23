package org.javai.springai.actions.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

class PlanningChatClientTest {

	@Test
	void promptWrapsDelegateRequestSpec() {
		ChatClient delegate = mock(ChatClient.class);
		ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
		when(delegate.prompt()).thenReturn(requestSpec);

		PlanningChatClient client = new PlanningChatClient(delegate);

		PlanningPromptSpec promptSpec = client.prompt();

		assertThat(promptSpec).isNotNull();
		verify(delegate).prompt();
	}
}

