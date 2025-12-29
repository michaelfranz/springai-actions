package org.javai.springai.actions.plan;

import java.util.List;
import org.springframework.lang.NonNull;

/**
 * Captures the fully rendered prompt that would be sent to the LLM.
 */
public record PromptPreview(
		@NonNull List<String> systemMessages,
		@NonNull List<String> userMessages,
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

