package org.javai.springai.actions.tuning;

import java.util.List;

/**
 * Strategy for generating system prompt variants from a baseline prompt.
 * Implementations can be LLM-powered (using an AI to rewrite prompts) or rule-based.
 */
public interface SystemPromptVariationStrategy {
	
	/**
	 * Generate system prompt variants from a baseline prompt.
	 * 
	 * @param baselinePrompt the system prompt to vary
	 * @param context additional context (model, scenario description, etc.) to inform variant generation
	 * @return list of system prompt variants (could include the baseline itself)
	 */
	List<SystemPromptVariant> generateVariants(String baselinePrompt, SystemPromptVariationContext context);
}

