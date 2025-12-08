package org.javai.springai.actions.sxl;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple tokenizer for S-expression query language.
 * Converts input string into a stream of tokens.
 */
public class SxlTokenizer {
	
	private final String input;
	private int pos = 0;
	
	public SxlTokenizer(String input) {
		this.input = input != null ? input : "";
	}
	
	/**
	 * Tokenizes the entire input string.
	 * 
	 * @return list of tokens (includes EOF token at end)
	 * @throws SxlParseException if invalid syntax is encountered
	 */
	public List<SxlToken> tokenize() {
		List<SxlToken> tokens = new ArrayList<>();
		
		while (!isAtEnd()) {
			skipWhitespace();
			if (isAtEnd()) break;
			
			tokens.add(nextToken());
		}
		
		tokens.add(new SxlToken(SxlToken.TokenType.EOF, "", pos));
		return tokens;
	}
	
	private SxlToken nextToken() {
		int start = pos;
		char c = peek();
		
		return switch (c) {
			case '(' -> {
				advance();
				yield new SxlToken(SxlToken.TokenType.LPAREN, "(", start);
			}
			case ')' -> {
				advance();
				yield new SxlToken(SxlToken.TokenType.RPAREN, ")", start);
			}
			case ',' -> {
				advance();
				yield new SxlToken(SxlToken.TokenType.COMMA, ",", start);
			}
			case '\'' -> scanString();
			default -> {
				if (isDigit(c) || (c == '-' && pos + 1 < input.length() && isDigit(input.charAt(pos + 1)))) {
					yield scanNumber();
				} else if (isIdentifierStart(c)) {
					yield scanIdentifier();
				} else {
					throw new SxlParseException("Unexpected character: '" + c + "' at position " + pos);
				}
			}
		};
	}
	
	private SxlToken scanString() {
		int start = pos;
		advance(); // consume opening '
		
		StringBuilder sb = new StringBuilder();
		while (!isAtEnd() && peek() != '\'') {
			char c = advance();
			if (c == '\\' && !isAtEnd()) {
				// Handle escaped characters
				char next = advance();
				sb.append(switch (next) {
					case 'n' -> '\n';
					case 't' -> '\t';
					case 'r' -> '\r';
					case '\'' -> '\'';
					case '\\' -> '\\';
					default -> next;
				});
			} else {
				sb.append(c);
			}
		}
		
		if (isAtEnd()) {
			throw new SxlParseException("Unterminated string at position " + start);
		}
		
		advance(); // consume closing '
		return new SxlToken(SxlToken.TokenType.STRING, sb.toString(), start);
	}
	
	private SxlToken scanNumber() {
		int start = pos;
		
		if (peek() == '-') {
			advance();
		}
		
		while (!isAtEnd() && isDigit(peek())) {
			advance();
		}
		
		// Check for decimal part
		if (!isAtEnd() && peek() == '.' && pos + 1 < input.length() && isDigit(input.charAt(pos + 1))) {
			advance(); // consume '.'
			while (!isAtEnd() && isDigit(peek())) {
				advance();
			}
		}
		
		String value = input.substring(start, pos);
		return new SxlToken(SxlToken.TokenType.NUMBER, value, start);
	}
	
	private SxlToken scanIdentifier() {
		int start = pos;
		
		while (!isAtEnd() && isIdentifierChar(peek())) {
			advance();
		}
		
		String value = input.substring(start, pos);
		return new SxlToken(SxlToken.TokenType.IDENTIFIER, value, start);
	}
	
	private void skipWhitespace() {
		while (!isAtEnd() && isWhitespace(peek())) {
			advance();
		}
	}
	
	private char peek() {
		return isAtEnd() ? '\0' : input.charAt(pos);
	}
	
	private char advance() {
		return input.charAt(pos++);
	}
	
	private boolean isAtEnd() {
		return pos >= input.length();
	}
	
	private boolean isWhitespace(char c) {
		return c == ' ' || c == '\t' || c == '\n' || c == '\r';
	}
	
	private boolean isDigit(char c) {
		return c >= '0' && c <= '9';
	}
	
	private boolean isIdentifierStart(char c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
	}
	
	private boolean isIdentifierChar(char c) {
		return isIdentifierStart(c) || isDigit(c) || c == '.';
	}
}

