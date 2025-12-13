package org.javai.springai.dsl.prompt;

import java.util.Optional;

/**
 * Supplies DSL guidance text for inclusion in system prompts.
 */
public interface DslGuidanceProvider {

	/**
	 * @param dslId the DSL identifier
	 * @return guidance text to include, if available
	 */
	Optional<String> guidanceFor(String dslId);

	/**
	 * A no-op provider that returns empty for all DSLs.
	 */
	DslGuidanceProvider NONE = dslId -> Optional.empty();
}
