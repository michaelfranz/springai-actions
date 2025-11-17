package org.javai.springai.actions.planning;

import org.springframework.ai.chat.client.ChatClient;

public class PlanningChatClient {

	private final ChatClient delegate;

	public PlanningChatClient(ChatClient delegate) {
		this.delegate = delegate;
	}

	public PlanningPromptSpec prompt() {
		return new PlanningPromptSpec(delegate.prompt());
	}
}