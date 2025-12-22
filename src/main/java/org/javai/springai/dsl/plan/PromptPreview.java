package org.javai.springai.dsl.plan;

import java.util.List;

/**
 * Captures the fully rendered prompt that would be sent to the LLM.
 */
public record PromptPreview(
		List<String> systemMessages,
		List<String> userMessages,
		List<String> grammarIds,
		List<String> actionNames
) {

	public String renderedSystem() {
		return String.join("\n\n", systemMessages);
	}

	public String renderedUser() {
		return String.join("\n\n", userMessages);
	}
}

