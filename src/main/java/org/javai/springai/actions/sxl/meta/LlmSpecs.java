package org.javai.springai.actions.sxl.meta;

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
}

