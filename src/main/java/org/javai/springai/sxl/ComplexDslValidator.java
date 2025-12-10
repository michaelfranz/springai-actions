package org.javai.springai.sxl;

import java.util.List;
import java.util.Map;
import org.javai.springai.sxl.grammar.SxlGrammar;
import org.javai.springai.sxl.grammar.Cardinality;
import org.javai.springai.sxl.grammar.DslMetadata;
import org.javai.springai.sxl.grammar.IdentifierRule;
import org.javai.springai.sxl.grammar.LiteralDefinitions;
import org.javai.springai.sxl.grammar.ParameterDefinition;
import org.javai.springai.sxl.grammar.SymbolDefinition;
import org.javai.springai.sxl.grammar.SymbolKind;

/**
 * High-level validator for complex DSL expressions that may contain embedded DSLs.
 * 
 * This validator:
 * - Parses tokens universally (no grammar needed for parsing)
 * - Expects root expression to be wrapped in an EMBED node
 * - Validates using the DSL grammar specified in the root EMBED node
 * - Supports nested embedded DSLs via EMBED nodes
 * - Is thread-safe (stateless validators)
 * - Provides uniform handling of all DSL nodes (root and embedded)
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
 * // Parse and validate - root must be wrapped in EMBED
 * SxlTokenizer tokenizer = new SxlTokenizer("(EMBED sxl-workflow (WORKFLOW (STEP ...)))");
 * List&lt;SxlToken&gt; tokens = tokenizer.tokenize();
 * List&lt;SxlNode&gt; ast = validator.parseAndValidate(tokens);
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
	 * Parses tokens universally, then validates using the DSL grammar specified in the root EMBED node.
	 * The root expression must be wrapped in an EMBED node: (EMBED dsl-id ...)
	 * Supports nested embedded DSLs via EMBED nodes.
	 * 
	 * @param tokens the tokens to parse (must start with EMBED node)
	 * @return validated AST nodes (root will be an EMBED node)
	 * @throws SxlParseException if parsing or validation fails
	 * @throws IllegalArgumentException if tokens is null
	 */
	public List<SxlNode> parseAndValidate(List<SxlToken> tokens) {
		if (tokens == null) {
			throw new IllegalArgumentException("Tokens cannot be null");
		}
		
		// Use a "universal" grammar that accepts EMBED nodes at root
		// DslParsingStrategy will handle validation of the EMBED node and its payload
		SxlGrammar universalGrammar = createUniversalGrammar();
		DslParsingStrategy validator = new DslParsingStrategy(universalGrammar, registry);
		return validator.parse(tokens);
	}
	
	/**
	 * Creates a universal grammar that accepts EMBED nodes at the root level.
	 * This allows uniform validation of all DSL nodes through EMBED.
	 */
	private SxlGrammar createUniversalGrammar() {
		ParameterDefinition embedParam = new ParameterDefinition(
			"root", "Root DSL", "node", List.of("EMBED"), 
			Cardinality.oneOrMore, true, null);
		
		SymbolDefinition rootSymbol = new SymbolDefinition(
			"Root", SymbolKind.node,
			List.of(embedParam), List.of(), List.of());
		
		return new SxlGrammar(
			"1.2",
			new DslMetadata("universal", "Universal DSL", "1.0"),
			Map.of("EMBED", rootSymbol),
			new LiteralDefinitions(null, null, null, null),
			new IdentifierRule("Identifier", ".*"),
			List.of(),
			null,
			List.of(),
			null
		);
	}

}

