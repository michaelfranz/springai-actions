package org.javai.springai.actions.planning;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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

		assertNotNull(promptSpec);
		verify(delegate).prompt();
	}
}

