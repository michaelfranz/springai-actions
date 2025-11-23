package org.javai.springai.actions.tuning;

/**
 * Context information provided to a {@link SystemPromptVariationStrategy} to help generate 
 * informed system prompt variants.
 * 
 * @param model the LLM model being used (e.g., "gpt-4", "gpt-4-turbo")
 * @param scenarioDescription human-readable description of what the scenario is trying 
 *                            to accomplish (from {@link ScenarioPlanSupplier#description()})
 */
public record SystemPromptVariationContext(
		String model,
		String scenarioDescription
) {}

