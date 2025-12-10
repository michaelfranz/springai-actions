package org.javai.springai.actions.sxl.grammar;

import java.util.Map;

/**
 * LLM specification configuration.
 */
public record LlmSpecs(
	LlmDefaults defaults,
	Map<String, LlmProviderDefaults> providerDefaults,
	Map<String, Map<String, LlmModelOverrides>> models,
	Map<String, LlmProfile> profiles
) {
	public <R> R accept(SxlGrammarVisitor<R> visitor) {
		return visitor.visitLlmSpecs(this);
	}
}

