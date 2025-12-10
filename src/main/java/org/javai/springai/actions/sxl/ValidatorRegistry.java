package org.javai.springai.actions.sxl;

import org.javai.springai.actions.sxl.meta.SxlGrammar;

/**
 * Registry for DSL grammars used during validation of embedded DSLs.
 * 
 * When a validator encounters an EMBED node, it looks up the inner DSL's
 * grammar from this registry to delegate validation.
 */
public interface ValidatorRegistry {

	/**
	 * Gets the grammar for a given DSL ID.
	 * 
	 * @param dslId the DSL identifier (e.g., "sxl-sql", "sxl-math")
	 * @return the grammar for the DSL, or null if not registered
	 */
	SxlGrammar getGrammar(String dslId);

	/**
	 * Checks if a DSL ID is registered in this registry.
	 * 
	 * @param dslId the DSL identifier to check
	 * @return true if the DSL is registered, false otherwise
	 */
	boolean isRegistered(String dslId);
}

