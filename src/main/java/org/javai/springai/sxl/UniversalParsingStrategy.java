package org.javai.springai.sxl;

import java.util.ArrayList;
import java.util.List;

/**
 * Universal parsing strategy that accepts any syntactically correct s-expression.
 * This is the default strategy and enforces only basic syntax rules (parentheses matching, etc.),
 * without any DSL-specific validation.
 */
public class UniversalParsingStrategy implements ParsingStrategy {

	@Override
	public List<SxlNode> parse(List<SxlToken> tokens) {
		ParserState state = new ParserState(tokens);
		List<SxlNode> nodes = new ArrayList<>();
		
		while (!state.isAtEnd()) {
			state.skipWhitespace();
			if (state.isAtEnd()) break;
			
			SxlNode node = parseExpression(state);
			if (node != null) {
				nodes.add(node);
			}
		}
		
		return nodes;
	}

	private SxlNode parseExpression(ParserState state) {
		SxlToken token = state.peek();
		
		return switch (token.type()) {
			case LPAREN -> parseParenthesizedExpression(state);
			case IDENTIFIER -> {
				state.advance();
				yield SxlNode.symbol(token.value());
			}
			case STRING, NUMBER -> {
				state.advance();
				yield SxlNode.literal(token.value());
			}
			case RPAREN -> throw new SxlParseException(
				"Unexpected ')' at position " + token.position() + ": no matching opening parenthesis");
			case COMMA -> {
				// Commas are optional separators, just skip them
				state.advance();
				yield parseExpression(state);
			}
			case EOF -> null;
		};
	}

	private SxlNode parseParenthesizedExpression(ParserState state) {
		int startPos = state.peek().position();
		state.advance(); // consume '('
		
		state.skipWhitespace();
		
		if (state.check(SxlToken.TokenType.RPAREN)) {
			state.advance(); // consume ')'
			throw new SxlParseException(
				"Empty expression at position " + startPos + ": parentheses must contain at least one element");
		}
		
		if (state.isAtEnd()) {
			throw new SxlParseException(
				"Unmatched '(' at position " + startPos + ": reached end of input");
		}
		
		// First token is the symbol
		SxlToken symbolToken = state.peek();
		if (symbolToken.type() != SxlToken.TokenType.IDENTIFIER) {
			throw new SxlParseException(
				"Expected identifier after '(' at position " + startPos + ", found: " + symbolToken.type());
		}
		state.advance();
		String symbol = symbolToken.value();
		
		// Parse arguments
		List<SxlNode> args = new ArrayList<>();
		while (!state.check(SxlToken.TokenType.RPAREN) && !state.isAtEnd()) {
			state.skipWhitespace();
			if (state.check(SxlToken.TokenType.RPAREN)) break;
			
			// Skip commas
			if (state.check(SxlToken.TokenType.COMMA)) {
				state.advance();
				state.skipWhitespace();
				continue;
			}
			
			SxlNode arg = parseExpression(state);
			if (arg != null) {
				args.add(arg);
			}
		}
		
		if (state.isAtEnd()) {
			throw new SxlParseException(
				"Unmatched '(' at position " + startPos + ": reached end of input");
		}
		
		if (!state.check(SxlToken.TokenType.RPAREN)) {
			SxlToken unexpected = state.peek();
			throw new SxlParseException(
				"Mismatched parentheses: expected ')' to close '(' at position " + startPos + 
				", but found " + unexpected.type() + " at position " + unexpected.position());
		}
		
		state.advance(); // consume ')'
		return SxlNode.symbol(symbol, args);
	}

	/**
	 * Helper class to encapsulate parser state for reuse across strategies.
	 */
	public static class ParserState {
		protected final List<SxlToken> tokens;
		protected int current = 0;

		public ParserState(List<SxlToken> tokens) {
			this.tokens = tokens != null ? tokens : List.of();
		}

		public SxlToken peek() {
			return tokens.get(current);
		}

		public SxlToken advance() {
			if (!isAtEnd()) {
				current++;
			}
			return tokens.get(current - 1);
		}

		public boolean check(SxlToken.TokenType type) {
			if (isAtEnd()) return false;
			return peek().type() == type;
		}

		public boolean isAtEnd() {
			return current >= tokens.size() || peek().type() == SxlToken.TokenType.EOF;
		}

		public void skipWhitespace() {
			// Whitespace is already handled by the tokenizer
		}

		public int getCurrentPosition() {
			return isAtEnd() ? (tokens.isEmpty() ? 0 : tokens.getLast().position()) : peek().position();
		}

		public int getCurrentIndex() {
			return current;
		}
	}
}

