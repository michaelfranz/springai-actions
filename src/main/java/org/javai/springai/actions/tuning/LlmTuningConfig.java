package org.javai.springai.actions.tuning;

public record LlmTuningConfig(
			String systemPrompt,
			Double temperature,
			Double topP
	) {
}
