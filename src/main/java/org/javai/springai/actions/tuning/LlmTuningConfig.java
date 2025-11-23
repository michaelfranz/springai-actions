package org.javai.springai.actions.tuning;

/**
 * Configuration for LLM tuning, capturing system prompt, sampling parameters, and model selection.
 * 
 * @param systemPrompt the system prompt text to use for plan generation
 * @param temperature sampling temperature (0.0 to 2.0); lower values are more deterministic
 * @param topP nucleus sampling parameter (0.0 to 1.0); controls diversity
 * @param model the model identifier (e.g., "gpt-4", "gpt-4-turbo", "claude-3-opus")
 * @param modelVersion optional specific version (e.g., "gpt-4-0613"); null uses default
 */
public record LlmTuningConfig(
		String systemPrompt,
		Double temperature,
		Double topP,
		String model,
		String modelVersion
) {
	
	/**
	 * Convenience constructor for backwards compatibility (uses defaults for model fields).
	 */
	public LlmTuningConfig(String systemPrompt, Double temperature, Double topP) {
		this(systemPrompt, temperature, topP, null, null);
	}
}
