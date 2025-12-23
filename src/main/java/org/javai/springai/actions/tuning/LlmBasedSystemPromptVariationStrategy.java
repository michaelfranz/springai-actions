package org.javai.springai.actions.tuning;

import java.util.ArrayList;
import java.util.List;
import org.javai.springai.actions.execution.ExecutablePlan;
import org.javai.springai.actions.planning.PlanningChatClient;

/**
 * Uses an LLM (via {@link PlanningChatClient}) to automatically generate system prompt variants.
 * For each variation type (e.g., "safety-focused", "conciseness-focused"), asks the LLM
 * to rewrite the baseline prompt while respecting the scenario's context.
 */
public class LlmBasedSystemPromptVariationStrategy implements SystemPromptVariationStrategy {
	
	private final PlanningChatClient chatClient;
	private final TuningLogger logger = new TuningLogger(DefaultTuningExecutor.class);
	
	/**
	 * Variation types to explore. Each type instructs the LLM to rewrite the prompt 
	 * with a specific focus.
	 */
	private static final List<String> VARIATION_TYPES = List.of(
			"safety-focused",
			"conciseness-focused",
			"accuracy-focused"
	);
	
	public LlmBasedSystemPromptVariationStrategy(PlanningChatClient chatClient) {
		this.chatClient = chatClient;
	}
	
	@Override
	public List<SystemPromptVariant> generateVariants(String baselinePrompt, SystemPromptVariationContext context) {
		List<SystemPromptVariant> variants = new ArrayList<>();
		
		logger.debug("Generating {} system prompt variants for model={}", VARIATION_TYPES.size(), context.model());
		
		for (String variationType : VARIATION_TYPES) {
			SystemPromptVariant variant = generateVariant(baselinePrompt, variationType, context);
			if (variant != null) {
				variants.add(variant);
			}
		}
		
		logger.debug("Generated {} successful system prompt variants", variants.size());
		return variants;
	}
	
	/**
	 * Generate a single system prompt variant using the LLM.
	 */
	private SystemPromptVariant generateVariant(String baselinePrompt, String variationType, SystemPromptVariationContext context) {
		try {
			String prompt = buildVariationPrompt(baselinePrompt, variationType, context);
			
			ExecutablePlan plan = chatClient
					.prompt()
					.system("You are an expert prompt engineer. Your job is to rewrite system prompts " +
							"to optimize for specific properties while preserving the original intent.")
					.user(prompt)
					.plan();
			
			// Extract the rewritten prompt from the plan (typically the first/only action's output)
			// For now, we'll use a simplified approach: capture the plan as-is
			String variantPrompt = extractPromptFromPlan(plan);
			
			if (variantPrompt != null && !variantPrompt.isBlank()) {
				logger.debug("Successfully generated {} system prompt variant", variationType);
				return new SystemPromptVariant(variantPrompt, getVariationRationale(variationType));
			}
		} catch (Exception e) {
			logger.debug("Failed to generate {} system prompt variant: {}", variationType, e.getMessage());
		}
		
		return null;
	}
	
	/**
	 * Build the user prompt to send to the LLM for system prompt variant generation.
	 */
	private String buildVariationPrompt(String baselinePrompt, String variationType, SystemPromptVariationContext context) {
		return String.format(
				"""
				Rewrite the following system prompt to be %s while preserving its core intent and functionality.
				
				Context:
				- Scenario: %s
				- Target LLM: %s
				
				Original system prompt:
				%s
				
				Provide ONLY the rewritten system prompt, without any explanation or preamble.
				""",
				variationType,
				context.scenarioDescription(),
				context.model(),
				baselinePrompt
		);
	}
	
	/**
	 * Extract the rewritten system prompt from the LLM's llmResponse.
	 * This is a simplified extraction; in production, you might want more sophisticated parsing.
	 */
	private String extractPromptFromPlan(ExecutablePlan plan) {
		// For now, return a placeholder indicating variant generation succeeded
		// In a real implementation, this would parse the plan's actions and extract the generated text
		if (plan != null && !plan.executables().isEmpty()) {
			return ""; // Simplified; would extract actual prompt from plan
		}
		return null;
	}
	
	/**
	 * Get a human-readable rationale for each variation type.
	 */
	private String getVariationRationale(String variationType) {
		return switch (variationType) {
			case "safety-focused" -> "Emphasizes safety guardrails and risk mitigation";
			case "conciseness-focused" -> "Optimized for brevity and clarity";
			case "accuracy-focused" -> "Prioritizes correctness and precision";
			default -> "Custom variation: " + variationType;
		};
	}
}

