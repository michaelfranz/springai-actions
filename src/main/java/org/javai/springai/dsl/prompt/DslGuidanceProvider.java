package org.javai.springai.dsl.prompt;

import java.util.Optional;

/**
 * Supplies DSL guidance text for inclusion in system prompts.
 */
public interface DslGuidanceProvider {

	/**
	 * @param dslId the DSL identifier
	 * @param providerId optional provider id (e.g., openai, anthropic)
	 * @param modelId optional model id (e.g., gpt-4.1)
	 * @return guidance text to include, if available
	 */
	Optional<String> guidanceFor(String dslId, String providerId, String modelId);

	default Optional<String> guidanceFor(String dslId) {
		return guidanceFor(dslId, null, null);
	}

	/**
	 * A no-op provider that returns empty for all DSLs.
	 */
	DslGuidanceProvider NONE = (dslId, providerId, modelId) -> Optional.empty();
}
