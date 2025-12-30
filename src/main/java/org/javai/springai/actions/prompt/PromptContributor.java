package org.javai.springai.actions.prompt;

import java.util.Optional;
import org.javai.springai.actions.internal.prompt.SystemPromptContext;

/**
 * Contributes dynamic context to the system prompt.
 * 
 * <p>Implementations add specific types of context to help the LLM understand
 * the available capabilities and data structures, such as:
 * <ul>
 *   <li>SQL catalog metadata (tables, columns, constraints)</li>
 *   <li>Action catalog (available actions and parameters)</li>
 *   <li>Domain-specific guidance</li>
 * </ul>
 */
public interface PromptContributor {

	/**
	 * Render an optional prompt contribution.
	 * 
	 * @param context prompt context containing action registry and metadata
	 * @return text to include in system prompt, or empty if nothing to add
	 */
	Optional<String> contribute(SystemPromptContext context);
}

