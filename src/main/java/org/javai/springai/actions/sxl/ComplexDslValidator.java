package org.javai.springai.actions.sxl;

import java.util.List;
import org.javai.springai.actions.sxl.meta.SxlGrammar;

/**
 * High-level validator for complex DSL expressions that may contain embedded DSLs.
 * 
 * This validator:
 * - Parses tokens universally (no grammar needed for parsing)
 * - Validates using a root DSL grammar
 * - Supports embedded DSLs via EMBED nodes
 * - Is thread-safe (stateless validators)
 * 
 * Example usage:
 * 
 * <pre>
 * // Setup
 * ValidatorRegistry registry = new DefaultValidatorRegistry();
 * registry.addGrammar("sxl-workflow", workflowGrammar);
 * registry.addGrammar("sxl-sql", sqlGrammar);
 * 
 * ComplexDslValidator validator = new ComplexDslValidator(registry);
 * 
 * // Parse and validate
 * SxlTokenizer tokenizer = new SxlTokenizer("(WORKFLOW (STEP ...))");
 * List&lt;SxlToken&gt; tokens = tokenizer.tokenize();
 * List&lt;SxlNode&gt; ast = validator.parseAndValidate(tokens, "sxl-workflow");
 * </pre>
 */
public class ComplexDslValidator {

	private final ValidatorRegistry registry;

	/**
	 * Creates a validator with a registry for looking up embedded DSL grammars.
	 * 
	 * @param registry the registry containing all DSL grammars (must not be null)
	 * @throws IllegalArgumentException if registry is null
	 */
	public ComplexDslValidator(ValidatorRegistry registry) {
		if (registry == null) {
			throw new IllegalArgumentException("ValidatorRegistry cannot be null");
		}
		this.registry = registry;
	}

	/**
	 * Parses tokens universally, then validates using the specified root DSL grammar.
	 * Supports embedded DSLs via EMBED nodes.
	 * 
	 * @param tokens the tokens to parse
	 * @param rootDslId the DSL ID to use for top-level validation (must be registered)
	 * @return validated AST nodes
	 * @throws SxlParseException if parsing or validation fails
	 * @throws IllegalArgumentException if rootDslId is not registered
	 */
	public List<SxlNode> parseAndValidate(List<SxlToken> tokens, String rootDslId) {
		if (tokens == null) {
			throw new IllegalArgumentException("Tokens cannot be null");
		}
		if (rootDslId == null || rootDslId.isBlank()) {
			throw new IllegalArgumentException("Root DSL ID cannot be null or empty");
		}
		
		// Get root grammar
		SxlGrammar rootGrammar = registry.getGrammar(rootDslId);
		if (rootGrammar == null) {
			throw new SxlParseException(
				"Root DSL '" + rootDslId + "' is not registered in the validator registry. " +
				"Available DSLs: " + getAvailableDslIds());
		}
		
		// Parse and validate using the validator (it does universal parsing internally)
		DslParsingStrategy validator = new DslParsingStrategy(rootGrammar, registry);
		return validator.parse(tokens);
	}

	/**
	 * Gets a list of available DSL IDs in the registry.
	 * This is a helper method for error messages.
	 * 
	 * @return comma-separated list of DSL IDs
	 */
	private String getAvailableDslIds() {
		// Note: This would require exposing registry's keys
		// For now, return a generic message
		// In a full implementation, DefaultValidatorRegistry could expose getRegisteredIds()
		return "[registry contents not exposed]";
	}
}

