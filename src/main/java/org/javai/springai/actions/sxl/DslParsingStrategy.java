package org.javai.springai.actions.sxl;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.javai.springai.actions.sxl.meta.Cardinality;
import org.javai.springai.actions.sxl.meta.ParameterDefinition;
import org.javai.springai.actions.sxl.meta.SxlGrammar;
import org.javai.springai.actions.sxl.meta.SymbolDefinition;

/**
 * DSL-aware parsing strategy that validates token sequences against a meta-grammar.
 * 
 * This strategy:
 * - Uses universal parsing for basic syntax with position tracking
 * - Validates symbols exist in the grammar
 * - Validates parameter counts match cardinality rules
 * - Validates parameter types (allowed symbols, literal types, identifier patterns)
 * - Enforces DSL-specific constraints
 * - Provides detailed error messages with token positions
 */
public class DslParsingStrategy implements ParsingStrategy {

	private final SxlGrammar grammar;
	private Map<SxlNode, Integer> nodePositions; // Maps nodes to token positions

	public DslParsingStrategy(SxlGrammar grammar) {
		if (grammar == null) {
			throw new IllegalArgumentException("Grammar cannot be null");
		}
		this.grammar = grammar;
	}

	@Override
	public List<SxlNode> parse(List<SxlToken> tokens) throws SxlParseException {
		// Parse with position tracking
		nodePositions = new IdentityHashMap<>();
		PositionAwareParser parser = new PositionAwareParser(tokens, nodePositions);
		List<SxlNode> nodes = parser.parse();
		
		// Validate against grammar rules
		for (SxlNode node : nodes) {
			validateNode(node, null, 0);
		}
		
		// Validate global constraints
		validateGlobalConstraints(nodes);
		
		return nodes;
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
	 * 
	 * @param node the node to validate
	 * @param parentSymbol the parent symbol name (for context in error messages)
	 * @param argIndex the argument index in the parent (for context)
	 */
	private void validateNode(SxlNode node, String parentSymbol, int argIndex) {
		if (node.isLiteral()) {
			// Literal validation happens at parameter level
			return;
		}

		String symbol = node.symbol();
		
		// Check if this is a function call (has args) or a bare identifier
		// Bare identifiers (no args) are validated at parameter type level, not here
		if (node.args().isEmpty()) {
			// This is a bare identifier - it will be validated when checking parameter types
			// Don't validate it as a symbol here
			return;
		}
		
		// This is a function call - validate as a symbol
		SymbolDefinition symbolDef = grammar.symbols().get(symbol);
		if (symbolDef == null) {
			// Check if it's a reserved symbol
			if (grammar.reservedSymbols() != null && grammar.reservedSymbols().contains(symbol)) {
				throw new SxlParseException(
					createContextMessage(parentSymbol, argIndex) +
					"Reserved symbol '" + symbol + "' cannot be used as a regular symbol at position " +
					findNodePosition(node) + ". Reserved symbols: " + grammar.reservedSymbols());
			}
			throw new SxlParseException(
				createContextMessage(parentSymbol, argIndex) +
				"Unknown symbol '" + symbol + "' at position " + findNodePosition(node) +
				". Expected one of: " + getKnownSymbols());
		}

		// Validate parameters
		validateParameters(symbol, symbolDef, node.args(), node);

		// Recursively validate children (they might be function calls or identifiers)
		int index = 0;
		for (SxlNode arg : node.args()) {
			validateNode(arg, symbol, index++);
		}
	}

	private void validateParameters(String symbol, SymbolDefinition symbolDef, 
	                                  List<SxlNode> actualArgs, SxlNode node) {
		List<ParameterDefinition> paramDefs = symbolDef.params();
		
		if (paramDefs == null || paramDefs.isEmpty()) {
			if (!actualArgs.isEmpty()) {
				throw new SxlParseException(
					createContextMessage(symbol, -1) +
					"Symbol '" + symbol + "' does not accept parameters, but found " + actualArgs.size() +
					" argument(s) at position " + findNodePosition(node));
			}
			return;
		}

		// Group arguments by parameter definition (handling cardinality)
		int paramIndex = 0;
		int argIndex = 0;

		while (paramIndex < paramDefs.size() && argIndex < actualArgs.size()) {
			ParameterDefinition paramDef = paramDefs.get(paramIndex);
			Cardinality cardinality = paramDef.cardinality();

			int argCount = countMatchingArgs(actualArgs, argIndex, paramDef, symbol, node);

			// If no match and parameter is optional, skip to next parameter
			if (argCount == 0 && cardinality == Cardinality.optional) {
				paramIndex++;
				continue;
			}

			// Validate cardinality
			validateCardinality(symbol, paramDef, argCount, argIndex, node);

			// Validate each matching argument
			for (int i = 0; i < argCount; i++) {
				SxlNode arg = actualArgs.get(argIndex + i);
				validateParameterType(symbol, paramDef, arg, argIndex + i, node);
			}

			argIndex += argCount;

			// Move to next parameter (unless it's zeroOrMore/oneOrMore, then check if more match)
			if (cardinality == Cardinality.zeroOrMore || cardinality == Cardinality.oneOrMore) {
				// Check if next argument also matches this parameter
				if (argIndex < actualArgs.size()) {
					SxlNode nextArg = actualArgs.get(argIndex);
					if (!matchesParameter(nextArg, paramDef)) {
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
					createContextMessage(symbol, -1) +
					"Symbol '" + symbol + "' requires parameter '" + paramDef.name() +
					"' (type: " + paramDef.type() + ") at position " + findNodePosition(node) +
					", but it was not provided");
			}
			paramIndex++;
		}

		// Check for extra arguments
		if (argIndex < actualArgs.size()) {
			throw new SxlParseException(
				createContextMessage(symbol, -1) +
				"Symbol '" + symbol + "' has too many arguments. Expected parameters: " +
				formatParameterList(paramDefs) + " at position " + findNodePosition(node));
		}
	}

	private int countMatchingArgs(List<SxlNode> args, int startIndex, 
	                                ParameterDefinition paramDef, String parentSymbol, SxlNode parentNode) {
		if (paramDef.cardinality() == Cardinality.required || paramDef.cardinality() == Cardinality.optional) {
			if (startIndex < args.size() && matchesParameter(args.get(startIndex), paramDef)) {
				return 1;
			}
			return 0;
		}

		// For zeroOrMore and oneOrMore, count consecutive matching arguments
		int count = 0;
		for (int i = startIndex; i < args.size(); i++) {
			if (matchesParameter(args.get(i), paramDef)) {
				count++;
			} else {
				break;
			}
		}
		return count;
	}

	private boolean matchesParameter(SxlNode arg, ParameterDefinition paramDef) {
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

		if ("identifier".equals(type)) {
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

		if ("node".equals(type)) {
			// Node type must be a function call (has args), not a bare identifier
			if (arg.args().isEmpty()) {
				return false; // This is a bare identifier, not a function call
			}
			// Check if symbol is in allowed_symbols
			List<String> allowed = paramDef.allowedSymbols();
			if (allowed != null && !allowed.isEmpty()) {
				return allowed.contains(arg.symbol());
			}
			// If no allowed_symbols specified, accept any symbol (but validate it exists in grammar)
			return grammar.symbols().containsKey(arg.symbol()) || 
			       (grammar.reservedSymbols() != null && grammar.reservedSymbols().contains(arg.symbol()));
		}

		if ("dsl-id".equals(type) || "embedded".equals(type)) {
			// These are special types that we can't validate at parse time
			return true;
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
			
			if ("string".equals(type)) {
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
			}

			if ("number".equals(type)) {
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
			}

			if ("boolean".equals(type)) {
				if (grammar.literals() != null && grammar.literals().boolean_() != null) {
					List<Object> values = grammar.literals().boolean_().values();
					return values != null && (values.contains(Boolean.parseBoolean(literalValue)) || 
					                          literalValue.equals("true") || literalValue.equals("false"));
				}
				return literalValue.equals("true") || literalValue.equals("false");
			}

			if ("null".equals(type)) {
				return literalValue.equals("null");
			}
		}

		return false;
	}

	private void validateCardinality(String symbol, ParameterDefinition paramDef, 
	                                   int actualCount, int argIndex, SxlNode node) {
		Cardinality cardinality = paramDef.cardinality();
		
		switch (cardinality) {
			case required:
				if (actualCount == 0) {
					throw new SxlParseException(
						createContextMessage(symbol, -1) +
						"Symbol '" + symbol + "' requires parameter '" + paramDef.name() +
						"' (type: " + paramDef.type() + ") at position " + findNodePosition(node));
				}
				if (actualCount > 1) {
					throw new SxlParseException(
						createContextMessage(symbol, argIndex) +
						"Symbol '" + symbol + "' parameter '" + paramDef.name() +
						"' should appear once, but found " + actualCount + " occurrences at position " +
						findNodePosition(node));
				}
				break;
			
			case optional:
				if (actualCount > 1) {
					throw new SxlParseException(
						createContextMessage(symbol, argIndex) +
						"Symbol '" + symbol + "' parameter '" + paramDef.name() +
						"' is optional and should appear at most once, but found " + actualCount +
						" occurrences at position " + findNodePosition(node));
				}
				break;
			
			case oneOrMore:
				if (actualCount == 0) {
					throw new SxlParseException(
						createContextMessage(symbol, -1) +
						"Symbol '" + symbol + "' requires at least one occurrence of parameter '" +
						paramDef.name() + "' (type: " + paramDef.type() + ") at position " +
						findNodePosition(node));
				}
				break;
			
			case zeroOrMore:
				// Any count is valid
				break;
		}
	}

	private void validateParameterType(String symbol, ParameterDefinition paramDef, 
	                                     SxlNode arg, int argIndex, SxlNode parentNode) {
		String type = paramDef.type();
		
		if (type == null || "any".equals(type)) {
			return; // No type restriction
		}

		if (arg.isLiteral()) {
			if (!type.startsWith("literal(")) {
				throw new SxlParseException(
					createContextMessage(symbol, argIndex) +
					"Symbol '" + symbol + "' parameter '" + paramDef.name() +
					"' expects type " + type + ", but found literal '" + arg.literalValue() +
					"' at position " + findNodePosition(arg));
			}
			
			if (!matchesLiteralType(arg.literalValue(), type)) {
				throw new SxlParseException(
					createContextMessage(symbol, argIndex) +
					"Symbol '" + symbol + "' parameter '" + paramDef.name() +
					"' expects literal type " + type + ", but found '" + arg.literalValue() +
					"' at position " + findNodePosition(arg));
			}
			return;
		}

		// Argument is a symbol node
		if (type.startsWith("literal(")) {
			throw new SxlParseException(
				createContextMessage(symbol, argIndex) +
				"Symbol '" + symbol + "' parameter '" + paramDef.name() +
				"' expects " + type + ", but found symbol '" + arg.symbol() +
				"' at position " + findNodePosition(arg));
		}

		if ("identifier".equals(type)) {
			// Validate identifier pattern
			String identifier = arg.symbol();
			if (paramDef.identifierRules() != null && paramDef.identifierRules().pattern() != null) {
				Pattern pattern = Pattern.compile(paramDef.identifierRules().pattern());
				if (!pattern.matcher(identifier).matches()) {
					throw new SxlParseException(
						createContextMessage(symbol, argIndex) +
						"Symbol '" + symbol + "' parameter '" + paramDef.name() +
						"' identifier '" + identifier + "' does not match required pattern: " +
						paramDef.identifierRules().pattern() + " at position " + findNodePosition(arg));
				}
			} else if (grammar.identifier() != null && grammar.identifier().pattern() != null) {
				Pattern pattern = Pattern.compile(grammar.identifier().pattern());
				if (!pattern.matcher(identifier).matches()) {
					throw new SxlParseException(
						createContextMessage(symbol, argIndex) +
						"Symbol '" + symbol + "' parameter '" + paramDef.name() +
						"' identifier '" + identifier + "' does not match identifier pattern: " +
						grammar.identifier().pattern() + " at position " + findNodePosition(arg));
				}
			}
			
			// Check that identifier is not a known symbol (unless it's in allowed_symbols)
			if (grammar.symbols().containsKey(identifier) && 
			    (paramDef.allowedSymbols() == null || !paramDef.allowedSymbols().contains(identifier))) {
				throw new SxlParseException(
					createContextMessage(symbol, argIndex) +
					"Symbol '" + symbol + "' parameter '" + paramDef.name() +
					"' expects an identifier, but '" + identifier + "' is a defined symbol. " +
					"Identifiers should not match symbol names at position " + findNodePosition(arg));
			}
			return;
		}

		if ("node".equals(type)) {
			// Check allowed symbols
			List<String> allowed = paramDef.allowedSymbols();
			if (allowed != null && !allowed.isEmpty()) {
				if (!allowed.contains(arg.symbol())) {
					throw new SxlParseException(
						createContextMessage(symbol, argIndex) +
						"Symbol '" + symbol + "' parameter '" + paramDef.name() +
						"' expects one of: " + allowed + ", but found '" + arg.symbol() +
						"' at position " + findNodePosition(arg));
				}
			}
			// Additional validation: ensure the symbol exists in grammar
			if (!grammar.symbols().containsKey(arg.symbol()) && 
			    (grammar.reservedSymbols() == null || !grammar.reservedSymbols().contains(arg.symbol()))) {
				throw new SxlParseException(
					createContextMessage(symbol, argIndex) +
					"Symbol '" + symbol + "' parameter '" + paramDef.name() +
					"' contains unknown symbol '" + arg.symbol() + "' at position " +
					findNodePosition(arg) + ". Expected one of: " + getKnownSymbols());
			}
			return;
		}

		// dsl-id and embedded types are validated elsewhere
	}

	private void validateGlobalConstraints(List<SxlNode> nodes) {
		if (grammar.constraints() == null) {
			return;
		}

		for (var constraint : grammar.constraints()) {
			if ("must_have_root".equals(constraint.rule())) {
				if (nodes.isEmpty() || !nodes.get(0).symbol().equals(constraint.symbol())) {
					throw new SxlParseException(
						"Global constraint violation: must have root symbol '" + constraint.symbol() +
						"', but found: " + (nodes.isEmpty() ? "empty" : nodes.get(0).symbol()));
				}
			}
			
			// Additional constraint types can be added here
		}
	}

	private String createContextMessage(String parentSymbol, int argIndex) {
		if (parentSymbol == null) {
			return "";
		}
		if (argIndex < 0) {
			return "In symbol '" + parentSymbol + "': ";
		}
		return "In symbol '" + parentSymbol + "' parameter " + argIndex + ": ";
	}

	private int findNodePosition(SxlNode node) {
		if (nodePositions != null && nodePositions.containsKey(node)) {
			return nodePositions.get(node);
		}
		// Fallback: try to find position of first child or use 0
		if (!node.isLiteral() && !node.args().isEmpty()) {
			SxlNode firstArg = node.args().get(0);
			if (nodePositions != null && nodePositions.containsKey(firstArg)) {
				return nodePositions.get(firstArg);
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

