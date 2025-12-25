package org.javai.springai.dsl.prompt;

import java.util.Optional;

/**
 * Supplies dynamic, DSL-specific prompt additions (e.g., action catalog for plan DSL,
 * SQL catalog for sxl-sql) to be appended immediately after the static guidance for
 * a given DSL id.
 */
public interface DslContextContributor {

	/**
	 * @return the DSL id this contributor applies to (e.g., "sxl-plan", "sxl-sql")
	 */
	String dslId();

	/**
	 * Render an optional prompt addition for the given DSL.
	 * @param context prompt context containing selected actions and per-DSL metadata
	 * @return text to append (already formatted), or empty if nothing to add
	 */
	Optional<String> contribute(SystemPromptContext context);
}

