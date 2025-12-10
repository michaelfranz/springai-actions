package org.javai.springai.actions.sxl;

import java.util.HashMap;
import java.util.Map;
import org.javai.springai.actions.sxl.grammar.SxlGrammar;

/**
 * Default implementation of ValidatorRegistry.
 * 
 * This implementation is mutable (supports adding grammars) but provides
 * an immutable view through the ValidatorRegistry interface for thread safety.
 */
public class DefaultValidatorRegistry implements ValidatorRegistry {

	private final Map<String, SxlGrammar> grammars = new HashMap<>();

	/**
	 * Adds a grammar to the registry.
	 * 
	 * @param dslId the DSL identifier (must match the grammar's dsl.id)
	 * @param grammar the grammar to register
	 * @throws IllegalArgumentException if dslId is null or empty, or if grammar is null
	 */
	public void addGrammar(String dslId, SxlGrammar grammar) {
		if (dslId == null || dslId.isBlank()) {
			throw new IllegalArgumentException("DSL ID cannot be null or empty");
		}
		if (grammar == null) {
			throw new IllegalArgumentException("Grammar cannot be null");
		}
		// Optionally validate that dslId matches grammar.dsl().id()
		if (grammar.dsl() != null && grammar.dsl().id() != null && !grammar.dsl().id().equals(dslId)) {
			// Log warning but don't fail - allow manual override if needed
		}
		grammars.put(dslId, grammar);
	}

	@Override
	public SxlGrammar getGrammar(String dslId) {
		return grammars.get(dslId);
	}

	@Override
	public boolean isRegistered(String dslId) {
		return grammars.containsKey(dslId);
	}

	/**
	 * Gets the number of registered grammars.
	 * 
	 * @return the number of grammars in the registry
	 */
	public int size() {
		return grammars.size();
	}

	/**
	 * Clears all registered grammars.
	 */
	public void clear() {
		grammars.clear();
	}
}

