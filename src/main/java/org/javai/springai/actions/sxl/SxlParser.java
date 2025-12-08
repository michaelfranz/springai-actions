package org.javai.springai.actions.sxl;

import java.util.ArrayList;
import java.util.List;

/**
 * Universal s-expression parser.
 * 
 * Converts a list of tokens from SxlTokenizer into a raw AST representation
 * of SxlNode objects. This parser is fully independent of any DSL semantics
 * and accepts any syntactically correct s-expression.
 */
public class SxlParser {

	private final List<SxlToken> tokens;
	private int current = 0;

	public SxlParser(List<SxlToken> tokens) {
		this.tokens = tokens != null ? tokens : List.of();
	}

	/**
	 * Parses the tokens into a list of top-level nodes.
	 * 
	 * @return list of parsed nodes (may be empty)
	 * @throws SxlParseException if syntax errors are encountered
	 */
	public List<SxlNode> parse() {
		List<SxlNode> nodes = new ArrayList<>();
		
		while (!isAtEnd()) {
			skipWhitespace();
			if (isAtEnd()) break;
			
			SxlNode node = parseExpression();
			if (node != null) {
				nodes.add(node);
			}
		}
		
		return nodes;
	}

	private SxlNode parseExpression() {
		SxlToken token = peek();
		
		return switch (token.type()) {
			case LPAREN -> parseParenthesizedExpression();
			case IDENTIFIER -> {
				advance();
				yield SxlNode.symbol(token.value());
			}
			case STRING -> {
				advance();
				yield SxlNode.literal(token.value());
			}
			case NUMBER -> {
				advance();
				yield SxlNode.literal(token.value());
			}
			case RPAREN -> throw new SxlParseException(
				"Unexpected ')' at position " + token.position() + ": no matching opening parenthesis");
			case COMMA -> {
				// Commas are optional separators, just skip them
				advance();
				yield parseExpression();
			}
			case EOF -> null;
		};
	}

	private SxlNode parseParenthesizedExpression() {
		int startPos = peek().position();
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
		return SxlNode.symbol(symbol, args);
	}

	private SxlToken peek() {
		return tokens.get(current);
	}

	private SxlToken advance() {
		if (!isAtEnd()) {
			current++;
		}
		return tokens.get(current - 1);
	}

	private boolean check(SxlToken.TokenType type) {
		if (isAtEnd()) return false;
		return peek().type() == type;
	}

	private boolean isAtEnd() {
		return current >= tokens.size() || peek().type() == SxlToken.TokenType.EOF;
	}

	private void skipWhitespace() {
		// Whitespace is already handled by the tokenizer, so this is a no-op
		// but we keep it as a placeholder for potential future needs
	}
}

