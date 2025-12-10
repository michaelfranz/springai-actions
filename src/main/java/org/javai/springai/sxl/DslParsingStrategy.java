package org.javai.springai.sxl;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.javai.springai.sxl.grammar.Cardinality;
import org.javai.springai.sxl.grammar.ParameterDefinition;
import org.javai.springai.sxl.grammar.SxlGrammar;
import org.javai.springai.sxl.grammar.SymbolDefinition;

/**
 * DSL-aware parsing strategy that validates token sequences against a meta-grammar.
 * 
 * This strategy:
 * - Uses universal parsing for basic syntax with position tracking
 * - Validates symbols exist in the grammar
 * - Validates parameter counts match cardinality rules
 * - Validates parameter types (allowed symbols, literal types, identifier patterns)
 * - Enforces DSL-specific constraints
 * - Supports embedded DSLs via EMBED nodes
 * - Provides detailed error messages with token positions and context chains
 * 
 * This class is stateless - all validation state is passed explicitly to methods.
 */
public class DslParsingStrategy implements ParsingStrategy {

	private static final String EMBED_SYMBOL = "EMBED";

	private final SxlGrammar grammar;
	private final ValidatorRegistry registry;

	/**
	 * Creates a validator with a grammar but no registry (no embedding support).
	 * 
	 * @param grammar the DSL grammar to validate against
	 */
	public DslParsingStrategy(SxlGrammar grammar) {
		this(grammar, null);
	}

	/**
	 * Creates a validator with a grammar and registry (embedding support).
	 * 
	 * @param grammar the DSL grammar to validate against
	 * @param registry the registry for looking up embedded DSL grammars (can be null)
	 */
	public DslParsingStrategy(SxlGrammar grammar, ValidatorRegistry registry) {
		if (grammar == null) {
			throw new IllegalArgumentException("Grammar cannot be null");
		}
		this.grammar = grammar;
		this.registry = registry;
	}

	/**
	 * Creates a validator with a grammar, registry, and shared position map.
	 * Used when validating embedded subtrees to preserve position information.
	 * 
	 * @param grammar the DSL grammar to validate against
	 * @param registry the registry for looking up embedded DSL grammars (can be null)
	 * @param positionMap the shared position map to use (can be null)
	 */
	public DslParsingStrategy(SxlGrammar grammar, ValidatorRegistry registry, Map<SxlNode, Integer> positionMap) {
		this(grammar, registry);
		// Note: positionMap is passed via ValidationState, not stored here
	}

	@Override
	public List<SxlNode> parse(List<SxlToken> tokens) throws SxlParseException {
		// Parse with position tracking
		Map<SxlNode, Integer> nodePositions = new IdentityHashMap<>();
		PositionAwareParser parser = new PositionAwareParser(tokens, nodePositions);
		List<SxlNode> nodes = parser.parse();
		
		// Create validation state
		ValidationState state = new ValidationState(nodePositions, new ArrayList<>());
		
		// Validate against grammar rules
		for (SxlNode node : nodes) {
			validateNode(node, null, 0, state);
		}
		
		// Validate global constraints
		validateGlobalConstraints(nodes, state);
		
		return nodes;
	}

	/**
	 * Validates already-parsed nodes (used when parsing was done separately).
	 * 
	 * @param nodes the nodes to validate
	 * @param positionMap the position map for these nodes
	 * @return the validated nodes (same as input)
	 * @throws SxlParseException if validation fails
	 */
	public List<SxlNode> validateNodes(List<SxlNode> nodes, Map<SxlNode, Integer> positionMap) {
		ValidationState state = new ValidationState(positionMap, new ArrayList<>());
		
		for (SxlNode node : nodes) {
			validateNode(node, null, 0, state);
		}
		
		validateGlobalConstraints(nodes, state);
		return nodes;
	}

	/**
	 * Validates a pre-parsed subtree (used for embedded DSLs).
	 * 
	 * @param nodes the nodes to validate (typically a single root node)
	 * @param positionMap the position map for these nodes
	 * @throws SxlParseException if validation fails
	 */
	public void validateSubtree(List<SxlNode> nodes, Map<SxlNode, Integer> positionMap) {
		validateSubtree(nodes, positionMap, new ValidationState(positionMap, new ArrayList<>()));
	}

	/**
	 * Validates a pre-parsed subtree with context (used for embedded DSLs).
	 * 
	 * @param nodes the nodes to validate (typically a single root node)
	 * @param positionMap the position map for these nodes
	 * @param state the validation state with context
	 * @throws SxlParseException if validation fails
	 */
	private void validateSubtree(List<SxlNode> nodes, Map<SxlNode, Integer> positionMap, ValidationState state) {
		for (SxlNode node : nodes) {
			validateNode(node, null, 0, state);
		}
		validateGlobalConstraints(nodes, state);
	}

	/**
	 * Validation state passed to all validation methods.
	 * Holds position information and context chain for error messages.
	 */
	private static class ValidationState {
		final Map<SxlNode, Integer> nodePositions;
		final List<String> contextChain;

		ValidationState(Map<SxlNode, Integer> nodePositions, List<String> contextChain) {
			this.nodePositions = nodePositions;
			this.contextChain = contextChain;
		}

		ValidationState withContext(String context) {
			List<String> newChain = new ArrayList<>(contextChain);
			newChain.add(context);
			return new ValidationState(nodePositions, newChain);
		}
	}

	/**
	 * Position-aware parser that tracks which token position created each node.
	 */
	private static class PositionAwareParser extends UniversalParsingStrategy.ParserState {
		private final Map<SxlNode, Integer> nodePositions;

		PositionAwareParser(List<SxlToken> tokens, Map<SxlNode, Integer> nodePositions) {
			super(tokens);
			this.nodePositions = nodePositions;
		}

		List<SxlNode> parse() {
			List<SxlNode> nodes = new ArrayList<>();
			
			while (!isAtEnd()) {
				skipWhitespace();
				if (isAtEnd()) break;
				
				SxlNode node = parseExpression();
				if (node != null) {
					int pos = getCurrentPosition();
					nodePositions.put(node, pos);
					nodes.add(node);
				}
			}
			
			return nodes;
		}

		private SxlNode parseExpression() {
			SxlToken token = peek();
			int startPos = token.position();
			
			return switch (token.type()) {
				case LPAREN -> parseParenthesizedExpression(startPos);
				case IDENTIFIER -> {
					advance();
					SxlNode node = SxlNode.symbol(token.value());
					nodePositions.put(node, startPos);
					yield node;
				}
				case STRING -> {
					advance();
					SxlNode node = SxlNode.literal(token.value());
					nodePositions.put(node, startPos);
					yield node;
				}
				case NUMBER -> {
					advance();
					SxlNode node = SxlNode.literal(token.value());
					nodePositions.put(node, startPos);
					yield node;
				}
				case RPAREN -> throw new SxlParseException(
					"Unexpected ')' at position " + token.position() + ": no matching opening parenthesis");
				case COMMA -> {
					advance();
					yield parseExpression();
				}
				case EOF -> null;
			};
		}

		private SxlNode parseParenthesizedExpression(int startPos) {
			advance(); // consume '('
			
			skipWhitespace();
			
			if (check(SxlToken.TokenType.RPAREN)) {
				advance(); // consume ')'
				throw new SxlParseException(
					"Empty expression at position " + startPos + ": parentheses must contain at least one element");
			}
			
			if (isAtEnd()) {
				throw new SxlParseException(
					"Unmatched '(' at position " + startPos + ": reached end of input");
			}
			
			// First token is the symbol
			SxlToken symbolToken = peek();
			if (symbolToken.type() != SxlToken.TokenType.IDENTIFIER) {
				throw new SxlParseException(
					"Expected identifier after '(' at position " + startPos + ", found: " + symbolToken.type());
			}
			advance();
			String symbol = symbolToken.value();
			
			// Parse arguments
			List<SxlNode> args = new ArrayList<>();
			while (!check(SxlToken.TokenType.RPAREN) && !isAtEnd()) {
				skipWhitespace();
				if (check(SxlToken.TokenType.RPAREN)) break;
				
				// Skip commas
				if (check(SxlToken.TokenType.COMMA)) {
					advance();
					skipWhitespace();
					continue;
				}
				
				SxlNode arg = parseExpression();
				if (arg != null) {
					args.add(arg);
				}
			}
			
			if (isAtEnd()) {
				throw new SxlParseException(
					"Unmatched '(' at position " + startPos + ": reached end of input");
			}
			
			if (!check(SxlToken.TokenType.RPAREN)) {
				SxlToken unexpected = peek();
				throw new SxlParseException(
					"Mismatched parentheses: expected ')' to close '(' at position " + startPos + 
					", but found " + unexpected.type() + " at position " + unexpected.position());
			}
			
			advance(); // consume ')'
			SxlNode node = SxlNode.symbol(symbol, args);
			nodePositions.put(node, startPos);
			return node;
		}
	}

	/**
	 * Validates a node against the grammar rules.
	 */
	private void validateNode(SxlNode node, String parentSymbol, int argIndex, ValidationState state) {
		if (node.isLiteral()) {
			// Literal validation happens at parameter level
			return;
		}

		String symbol = node.symbol();
		
		// Check for EMBED node first - EMBED should always be validated, even with empty args
		if (EMBED_SYMBOL.equals(symbol)) {
			validateEmbedNode(node, parentSymbol, argIndex, state);
			return; // Don't validate children - inner validator handles them
		}
		
		// Check if this is a function call (has args) or a bare identifier
		// Bare identifiers (no args) used as arguments are validated at parameter type level, not here
		// But function calls (nodes that exist in the grammar) should always be validated, even with no args,
		// because they might have required parameters that need to be checked
		// Top-level nodes (parentSymbol == null) should always be validated, even with no args
		
		// This is a function call - validate as a symbol
		SymbolDefinition symbolDef = grammar.symbols().get(symbol);
		
		// If this symbol is not in the grammar and has no args and has a parent, it's a bare identifier
		// Skip validation here - it will be validated at parameter type level
		if (symbolDef == null && node.args().isEmpty() && parentSymbol != null) {
			// This is a bare identifier used as an argument - it will be validated when checking parameter types
			// But first check if it's a reserved symbol
			if (grammar.reservedSymbols() != null && grammar.reservedSymbols().contains(symbol)) {
				throw new SxlParseException(
					buildContextMessage(state, parentSymbol, argIndex) +
					"Reserved symbol '" + symbol + "' cannot be used as a regular symbol at position " +
					findNodePosition(node, state) + ". Reserved symbols: " + grammar.reservedSymbols());
			}
			// Unknown bare identifier - will be validated at parameter type level
			return;
		}
		
		// If symbol is not in grammar at all, it's an unknown symbol
		if (symbolDef == null) {
			// Check if it's a reserved symbol
			if (grammar.reservedSymbols() != null && grammar.reservedSymbols().contains(symbol)) {
				throw new SxlParseException(
					buildContextMessage(state, parentSymbol, argIndex) +
					"Reserved symbol '" + symbol + "' cannot be used as a regular symbol at position " +
					findNodePosition(node, state) + ". Reserved symbols: " + grammar.reservedSymbols());
			}
			throw new SxlParseException(
				buildContextMessage(state, parentSymbol, argIndex) +
				"Unknown symbol '" + symbol + "' at position " + findNodePosition(node, state) +
				". Expected one of: " + getKnownSymbols());
		}

		// Validate parameters
		validateParameters(symbol, symbolDef, node.args(), node, state);

		// Recursively validate children (they might be function calls or identifiers)
		ValidationState childState = state.withContext(symbol);
		int index = 0;
		for (SxlNode arg : node.args()) {
			validateNode(arg, symbol, index++, childState);
		}
	}

	/**
	 * Validates an EMBED node and delegates to the inner DSL validator.
	 */
	private void validateEmbedNode(SxlNode embedNode, String parentSymbol, int argIndex, ValidationState state) {
		List<SxlNode> args = embedNode.args();
		
		if (args.isEmpty()) {
			throw new SxlParseException(
				buildContextMessage(state, parentSymbol, argIndex) +
				"EMBED requires at least one argument (dsl-id) at position " + findNodePosition(embedNode, state));
		}
		
		// First argument is the dsl-id
		SxlNode dslIdNode = args.getFirst();
		if (dslIdNode.isLiteral() || !dslIdNode.args().isEmpty()) {
			throw new SxlParseException(
				buildContextMessage(state, parentSymbol, argIndex) +
				"EMBED first argument must be a DSL identifier (dsl-id), but found " +
				(dslIdNode.isLiteral() ? "literal" : "node") + " at position " + findNodePosition(dslIdNode, state));
		}
		
		String dslId = dslIdNode.symbol();
		
		// Validate dsl-id is registered
		if (registry == null || !registry.isRegistered(dslId)) {
			String available = registry != null 
				? " Available DSLs: " + String.join(", ", getAvailableDslIds())
				: " No validator registry configured.";
			throw new SxlParseException(
				buildContextMessage(state, parentSymbol, argIndex) +
				"EMBED references unknown DSL '" + dslId + "' at position " + findNodePosition(dslIdNode, state) +
				available);
		}
		
		// Extract payload nodes (all arguments after dsl-id)
		List<SxlNode> payloadNodes = args.subList(1, args.size());
		
		if (payloadNodes.isEmpty()) {
			throw new SxlParseException(
				buildContextMessage(state, parentSymbol, argIndex) +
				"EMBED requires at least one payload node after dsl-id at position " + findNodePosition(embedNode, state));
		}
		
		// Get inner grammar and validate payload
		SxlGrammar innerGrammar = registry.getGrammar(dslId);
		
		// Add EMBED context to the state for better error messages
		ValidationState embedState = state;
		if (parentSymbol != null) {
			embedState = embedState.withContext(parentSymbol);
		}
		embedState = embedState.withContext("EMBED");
		
		DslParsingStrategy innerValidator = new DslParsingStrategy(innerGrammar, registry, embedState.nodePositions);
		
		// Validate payload with context
		innerValidator.validateSubtree(payloadNodes, embedState.nodePositions, embedState);
	}

	private List<String> getAvailableDslIds() {
		// This would require exposing registry's keys, but for now return empty
		// In practice, this would be implemented if needed
		return List.of();
	}

	private void validateParameters(String symbol, SymbolDefinition symbolDef, 
	                                  List<SxlNode> actualArgs, SxlNode node, ValidationState state) {
		List<ParameterDefinition> paramDefs = symbolDef.params();
		
		if (paramDefs == null || paramDefs.isEmpty()) {
			if (!actualArgs.isEmpty()) {
				throw new SxlParseException(
					buildContextMessage(state, symbol, -1) +
					"Symbol '" + symbol + "' does not accept parameters, but found " + actualArgs.size() +
					" argument(s) at position " + findNodePosition(node, state));
			}
			return;
		}

		// Group arguments by parameter definition (handling cardinality)
		int paramIndex = 0;
		int argIndex = 0;

		// If there are no arguments, check if any required parameters are missing
		if (actualArgs.isEmpty()) {
			for (ParameterDefinition paramDef : paramDefs) {
				if (paramDef.cardinality() == Cardinality.required) {
					throw new SxlParseException(
						buildContextMessage(state, symbol, -1) +
						"Symbol '" + symbol + "' requires parameter '" + paramDef.name() +
						"' (type: " + paramDef.type() + ") at position " + findNodePosition(node, state) +
						", but it was not provided");
				}
				if (paramDef.cardinality() == Cardinality.oneOrMore) {
					throw new SxlParseException(
						buildContextMessage(state, symbol, -1) +
						"Symbol '" + symbol + "' requires at least one occurrence of parameter '" +
						paramDef.name() + "' (type: " + paramDef.type() + ") at position " +
						findNodePosition(node, state) + ", but it was not provided");
				}
			}
			return; // No arguments and no required parameters - validation complete
		}

		while (paramIndex < paramDefs.size() && argIndex < actualArgs.size()) {
			ParameterDefinition paramDef = paramDefs.get(paramIndex);
			Cardinality cardinality = paramDef.cardinality();

			int argCount = countMatchingArgs(actualArgs, argIndex, paramDef, symbol, node, state);

			// If no match and parameter is optional or zeroOrMore, handle based on whether it's ordered
			if (argCount == 0 && argIndex < actualArgs.size() && 
			    (cardinality == Cardinality.optional || cardinality == Cardinality.zeroOrMore)) {
				// For ordered parameters, skip without validating - the argument might match a later parameter
				// Ordered defaults to true, so we check for explicit false
				Boolean isOrdered = paramDef.ordered();
				if (isOrdered == null || isOrdered) {
					// Ordered optional/zeroOrMore parameter with no match - skip it, argument might match later parameter
					paramIndex++;
					continue;
				}
				
				// For non-ordered optional parameters, we can check category to give better error messages
				SxlNode firstArg = actualArgs.get(argIndex);
				String paramType = paramDef.type();
				// EMBED is always a node type, never an identifier, even with empty args
				if (EMBED_SYMBOL.equals(firstArg.symbol()) && "identifier".equals(paramType)) {
					// Skip this optional identifier parameter - EMBED should match a node parameter
					paramIndex++;
					continue;
				}
				// If argument category matches parameter category (both literals, both identifiers, etc.), validate to give proper error
				boolean categoryMatch = (firstArg.isLiteral() && paramType != null && paramType.startsWith("literal(")) ||
				                        (!firstArg.isLiteral() && !firstArg.args().isEmpty() && "node".equals(paramType)) ||
				                        (!firstArg.isLiteral() && firstArg.args().isEmpty() && !EMBED_SYMBOL.equals(firstArg.symbol()) && "identifier".equals(paramType));
				if (categoryMatch) {
					// Right category but wrong type - validate to give proper error
					validateParameterType(symbol, paramDef, firstArg, argIndex, node, state);
					// If validation doesn't throw, skip to next param
					paramIndex++;
					continue;
				}
				// Wrong category - skip this optional parameter
				paramIndex++;
				continue;
			}

			// If no match but we have arguments, check if it's a type mismatch
			// This applies to required/oneOrMore - we want to give proper error messages
			if (argCount == 0 && argIndex < actualArgs.size() && 
			    (cardinality == Cardinality.required || cardinality == Cardinality.oneOrMore)) {
				SxlNode firstArg = actualArgs.get(argIndex);
				// Check if it's a type mismatch (e.g., not in allowed_symbols, wrong literal type)
				validateParameterType(symbol, paramDef, firstArg, argIndex, node, state);
				// If we get here, type was OK but something else is wrong - fall through to cardinality check
			}

			// Validate cardinality
			validateCardinality(symbol, paramDef, argCount, argIndex, node, state);

			// Validate each matching argument
			for (int i = 0; i < argCount; i++) {
				SxlNode arg = actualArgs.get(argIndex + i);
				// Skip validation for embedded parameters - defer to inner validator
				if (!"embedded".equals(paramDef.type())) {
					validateParameterType(symbol, paramDef, arg, argIndex + i, node, state);
				}
			}

			argIndex += argCount;

			// Move to next parameter (unless it's zeroOrMore/oneOrMore, then check if more match)
			if (cardinality == Cardinality.zeroOrMore || cardinality == Cardinality.oneOrMore) {
				// Check if next argument also matches this parameter
				if (argIndex < actualArgs.size()) {
					SxlNode nextArg = actualArgs.get(argIndex);
					if (!matchesParameter(nextArg, paramDef, state)) {
						paramIndex++; // Move to next parameter
					}
					// Otherwise, continue with same parameter
				} else {
					paramIndex++;
				}
			} else {
				paramIndex++;
			}
		}

		// Check for remaining required parameters
		while (paramIndex < paramDefs.size()) {
			ParameterDefinition paramDef = paramDefs.get(paramIndex);
			if (paramDef.cardinality() == Cardinality.required) {
				throw new SxlParseException(
					buildContextMessage(state, symbol, -1) +
					"Symbol '" + symbol + "' requires parameter '" + paramDef.name() +
					"' (type: " + paramDef.type() + ") at position " + findNodePosition(node, state) +
					", but it was not provided");
			}
			if (paramDef.cardinality() == Cardinality.oneOrMore) {
				throw new SxlParseException(
					buildContextMessage(state, symbol, -1) +
					"Symbol '" + symbol + "' requires at least one occurrence of parameter '" +
					paramDef.name() + "' (type: " + paramDef.type() + ") at position " +
					findNodePosition(node, state) + ", but it was not provided");
			}
			paramIndex++;
		}

		// Check for extra arguments
		if (argIndex < actualArgs.size()) {
			throw new SxlParseException(
				buildContextMessage(state, symbol, -1) +
				"Symbol '" + symbol + "' has too many arguments. Expected parameters: " +
				formatParameterList(paramDefs) + " at position " + findNodePosition(node, state));
		}
	}

	private int countMatchingArgs(List<SxlNode> args, int startIndex, 
	                                ParameterDefinition paramDef, String parentSymbol, SxlNode parentNode, ValidationState state) {
		if (paramDef.cardinality() == Cardinality.required || paramDef.cardinality() == Cardinality.optional) {
			if (startIndex < args.size() && matchesParameter(args.get(startIndex), paramDef, state)) {
				return 1;
			}
			return 0;
		}

		// For zeroOrMore and oneOrMore, count consecutive matching arguments
		int count = 0;
		for (int i = startIndex; i < args.size(); i++) {
			if (matchesParameter(args.get(i), paramDef, state)) {
				count++;
			} else {
				break;
			}
		}
		return count;
	}

	private boolean matchesParameter(SxlNode arg, ParameterDefinition paramDef, ValidationState state) {
		String type = paramDef.type();
		
		if (type == null || "any".equals(type)) {
			return true;
		}

		if (arg.isLiteral()) {
			return matchesLiteralType(arg.literalValue(), type);
		}

		// Argument is a symbol node
		if (type.startsWith("literal(")) {
			return false; // Expected literal but got symbol
		}

		switch (type) {
			case "identifier" -> {
				// EMBED is always a node type, not an identifier, even with empty args
				if (EMBED_SYMBOL.equals(arg.symbol())) {
					return false; // EMBED is a node, not an identifier
				}
				// Identifier type must be a bare identifier (no args), not a function call
				if (!arg.args().isEmpty()) {
					return false; // This is a function call, not an identifier
				}
				// Check identifier pattern if specified
				if (paramDef.identifierRules() != null && paramDef.identifierRules().pattern() != null) {
					Pattern pattern = Pattern.compile(paramDef.identifierRules().pattern());
					return pattern.matcher(arg.symbol()).matches();
				}
				// Check global identifier pattern
				if (grammar.identifier() != null && grammar.identifier().pattern() != null) {
					Pattern pattern = Pattern.compile(grammar.identifier().pattern());
					return pattern.matcher(arg.symbol()).matches();
				}
				return true; // No pattern specified, accept any identifier
			}
			case "node" -> {
				// Check if symbol is in allowed_symbols first
				// If allowed_symbols is specified, use it as the definitive check
				List<String> allowed = paramDef.allowedSymbols();
				if (allowed != null && !allowed.isEmpty()) {
					// If symbol is in allowed list, match regardless of whether it has arguments
					// This allows symbols like (D) to match even though they have no args
					return allowed.contains(arg.symbol());
				}
				// If no allowed_symbols specified, node type must be a function call (has args), not a bare identifier
				// Exception: EMBED is always treated as a node type, even with empty args
				if (arg.args().isEmpty() && !EMBED_SYMBOL.equals(arg.symbol())) {
					return false; // This is a bare identifier, not a function call
				}
				// If no allowed_symbols specified, accept any symbol (but validate it exists in grammar)
				return grammar.symbols().containsKey(arg.symbol()) ||
						(grammar.reservedSymbols() != null && grammar.reservedSymbols().contains(arg.symbol())) ||
						EMBED_SYMBOL.equals(arg.symbol()); // EMBED is always allowed
			}
			case "dsl-id", "embedded" -> {
				// These are special types that we can't validate at parse time
				return true;
			}
		}

		return true; // Default: accept
	}

	private boolean matchesLiteralType(String literalValue, String typeSpec) {
		if (typeSpec == null || !typeSpec.startsWith("literal(")) {
			return false;
		}

		// Extract type from literal(X) or literal(A|B)
		String typePart = typeSpec.substring(8, typeSpec.length() - 1); // Remove "literal(" and ")"
		String[] types = typePart.split("\\|");

		for (String type : types) {
			type = type.trim();

			switch (type) {
				case "string" -> {
					if (grammar.literals() != null && grammar.literals().string() != null) {
						Pattern pattern = Pattern.compile(grammar.literals().string().regex());
						// Try matching both with and without quotes (tokenizer strips quotes)
						if (pattern.matcher(literalValue).matches() ||
								pattern.matcher("\"" + literalValue + "\"").matches() ||
								pattern.matcher("'" + literalValue + "'").matches()) {
							return true;
						}
					}
					// Default: accept any value (tokenizer already validated it as a string)
					return true;
					// Default: accept any value (tokenizer already validated it as a string)
				}
				case "number" -> {
					if (grammar.literals() != null && grammar.literals().number() != null) {
						Pattern pattern = Pattern.compile(grammar.literals().number().regex());
						if (pattern.matcher(literalValue).matches()) {
							return true;
						}
					}
					// Default: try to parse as number
					try {
						Double.parseDouble(literalValue);
						return true;
					} catch (NumberFormatException e) {
						return false;
					}
					// Default: try to parse as number
				}
				case "boolean" -> {
					if (grammar.literals() != null && grammar.literals().boolean_() != null) {
						List<Object> values = grammar.literals().boolean_().values();
						return values != null && (values.contains(Boolean.parseBoolean(literalValue)) ||
								literalValue.equals("true") || literalValue.equals("false"));
					}
					return literalValue.equals("true") || literalValue.equals("false");
				}
				case "null" -> {
					return literalValue.equals("null");
				}
			}

		}

		return false;
	}

	private void validateCardinality(String symbol, ParameterDefinition paramDef, 
	                                   int actualCount, int argIndex, SxlNode node, ValidationState state) {
		Cardinality cardinality = paramDef.cardinality();
		
		switch (cardinality) {
			case required:
				if (actualCount == 0) {
					throw new SxlParseException(
						buildContextMessage(state, symbol, -1) +
						"Symbol '" + symbol + "' requires parameter '" + paramDef.name() +
						"' (type: " + paramDef.type() + ") at position " + findNodePosition(node, state));
				}
				if (actualCount > 1) {
					throw new SxlParseException(
						buildContextMessage(state, symbol, argIndex) +
						"Symbol '" + symbol + "' parameter '" + paramDef.name() +
						"' should appear once, but found " + actualCount + " occurrences at position " +
						findNodePosition(node, state));
				}
				break;
			
			case optional:
				if (actualCount > 1) {
					throw new SxlParseException(
						buildContextMessage(state, symbol, argIndex) +
						"Symbol '" + symbol + "' parameter '" + paramDef.name() +
						"' is optional and should appear at most once, but found " + actualCount +
						" occurrences at position " + findNodePosition(node, state));
				}
				break;
			
			case oneOrMore:
				if (actualCount == 0) {
					throw new SxlParseException(
						buildContextMessage(state, symbol, -1) +
						"Symbol '" + symbol + "' requires at least one occurrence of parameter '" +
						paramDef.name() + "' (type: " + paramDef.type() + ") at position " +
						findNodePosition(node, state));
				}
				break;
			
			case zeroOrMore:
				// Any count is valid
				break;
		}
	}

	private void validateParameterType(String symbol, ParameterDefinition paramDef, 
	                                     SxlNode arg, int argIndex, SxlNode parentNode, ValidationState state) {
		String type = paramDef.type();
		
		if (type == null || "any".equals(type)) {
			return; // No type restriction
		}

		if (arg.isLiteral()) {
			if (!type.startsWith("literal(")) {
				throw new SxlParseException(
					buildContextMessage(state, symbol, argIndex) +
					"Symbol '" + symbol + "' parameter '" + paramDef.name() +
					"' expects type " + type + ", but found literal '" + arg.literalValue() +
					"' at position " + findNodePosition(arg, state));
			}
			
			if (!matchesLiteralType(arg.literalValue(), type)) {
				throw new SxlParseException(
					buildContextMessage(state, symbol, argIndex) +
					"Symbol '" + symbol + "' parameter '" + paramDef.name() +
					"' expects literal type " + type + ", but found '" + arg.literalValue() +
					"' at position " + findNodePosition(arg, state));
			}
			return;
		}

		// Argument is a symbol node
		if (type.startsWith("literal(")) {
			throw new SxlParseException(
				buildContextMessage(state, symbol, argIndex) +
				"Symbol '" + symbol + "' parameter '" + paramDef.name() +
				"' expects " + type + ", but found symbol '" + arg.symbol() +
				"' at position " + findNodePosition(arg, state));
		}

		if ("identifier".equals(type)) {
			// EMBED is always a node type, not an identifier
			if (EMBED_SYMBOL.equals(arg.symbol())) {
				throw new SxlParseException(
					buildContextMessage(state, symbol, argIndex) +
					"Symbol '" + symbol + "' parameter '" + paramDef.name() +
					"' expects an identifier, but found EMBED node at position " + findNodePosition(arg, state));
			}
			// Validate identifier pattern
			String identifier = arg.symbol();
			if (paramDef.identifierRules() != null && paramDef.identifierRules().pattern() != null) {
				Pattern pattern = Pattern.compile(paramDef.identifierRules().pattern());
				if (!pattern.matcher(identifier).matches()) {
					throw new SxlParseException(
						buildContextMessage(state, symbol, argIndex) +
						"Symbol '" + symbol + "' parameter '" + paramDef.name() +
						"' identifier '" + identifier + "' does not match required pattern: " +
						paramDef.identifierRules().pattern() + " at position " + findNodePosition(arg, state));
				}
			} else if (grammar.identifier() != null && grammar.identifier().pattern() != null) {
				Pattern pattern = Pattern.compile(grammar.identifier().pattern());
				if (!pattern.matcher(identifier).matches()) {
					throw new SxlParseException(
						buildContextMessage(state, symbol, argIndex) +
						"Symbol '" + symbol + "' parameter '" + paramDef.name() +
						"' identifier '" + identifier + "' does not match identifier pattern: " +
						grammar.identifier().pattern() + " at position " + findNodePosition(arg, state));
				}
			}
			
			// Check that identifier is not a known symbol (unless it's in allowed_symbols or is EMBED)
			if (grammar.symbols().containsKey(identifier) && 
			    (paramDef.allowedSymbols() == null || !paramDef.allowedSymbols().contains(identifier)) &&
			    !EMBED_SYMBOL.equals(identifier)) {
				throw new SxlParseException(
					buildContextMessage(state, symbol, argIndex) +
					"Symbol '" + symbol + "' parameter '" + paramDef.name() +
					"' expects an identifier, but '" + identifier + "' is a defined symbol. " +
					"Identifiers should not match symbol names at position " + findNodePosition(arg, state));
			}
			return;
		}

		if ("node".equals(type)) {
			// Check allowed symbols
			List<String> allowed = paramDef.allowedSymbols();
			if (allowed != null && !allowed.isEmpty()) {
				if (!allowed.contains(arg.symbol()) && !EMBED_SYMBOL.equals(arg.symbol())) {
					throw new SxlParseException(
						buildContextMessage(state, symbol, argIndex) +
						"Symbol '" + symbol + "' parameter '" + paramDef.name() +
						"' expects one of: " + allowed + ", but found '" + arg.symbol() +
						"' at position " + findNodePosition(arg, state));
				}
			}
			// Additional validation: ensure the symbol exists in grammar or is EMBED
			if (!grammar.symbols().containsKey(arg.symbol()) && 
			    (grammar.reservedSymbols() == null || !grammar.reservedSymbols().contains(arg.symbol())) &&
			    !EMBED_SYMBOL.equals(arg.symbol())) {
				throw new SxlParseException(
					buildContextMessage(state, symbol, argIndex) +
					"Symbol '" + symbol + "' parameter '" + paramDef.name() +
					"' contains unknown symbol '" + arg.symbol() + "' at position " +
					findNodePosition(arg, state) + ". Expected one of: " + getKnownSymbols());
			}
		}

		// dsl-id and embedded types are validated elsewhere
	}

	private void validateGlobalConstraints(List<SxlNode> nodes, ValidationState state) {
		if (grammar.constraints() == null) {
			return;
		}

		for (var constraint : grammar.constraints()) {
			if ("must_have_root".equals(constraint.rule())) {
				if (nodes.isEmpty() || !nodes.getFirst().symbol().equals(constraint.symbol())) {
					throw new SxlParseException(
						buildContextMessage(state, null, -1) +
						"Global constraint violation: must have root symbol '" + constraint.symbol() +
						"', but found: " + (nodes.isEmpty() ? "empty" : nodes.getFirst().symbol()));
				}
			}
			
			// Additional constraint types can be added here
		}
	}

	private String buildContextMessage(ValidationState state, String parentSymbol, int argIndex) {
		StringBuilder sb = new StringBuilder();
		
		// Add context chain
		if (!state.contextChain.isEmpty()) {
			sb.append("In ").append(String.join(".", state.contextChain));
			if (parentSymbol != null) {
				sb.append(".").append(parentSymbol);
			}
			sb.append(": ");
		} else if (parentSymbol != null) {
			if (argIndex < 0) {
				sb.append("In symbol '").append(parentSymbol).append("': ");
			} else {
				sb.append("In symbol '").append(parentSymbol).append("' parameter ").append(argIndex).append(": ");
			}
		}
		
		return sb.toString();
	}

	private int findNodePosition(SxlNode node, ValidationState state) {
		if (state.nodePositions != null && state.nodePositions.containsKey(node)) {
			return state.nodePositions.get(node);
		}
		// Fallback: try to find position of first child or use 0
		if (!node.isLiteral() && !node.args().isEmpty()) {
			SxlNode firstArg = node.args().getFirst();
			if (state.nodePositions != null && state.nodePositions.containsKey(firstArg)) {
				return state.nodePositions.get(firstArg);
			}
		}
		return 0; // Default if position not found
	}

	private String getKnownSymbols() {
		Set<String> known = grammar.symbols().keySet();
		return known.stream().sorted().collect(Collectors.joining(", ", "[", "]"));
	}

	private String formatParameterList(List<ParameterDefinition> params) {
		if (params == null || params.isEmpty()) {
			return "none";
		}
		return params.stream()
			.map(p -> p.name() + ":" + p.type() + "(" + p.cardinality() + ")")
			.collect(Collectors.joining(", "));
	}
}
