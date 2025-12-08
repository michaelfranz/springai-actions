package org.javai.springai.actions.sxl;

/**
 * Represents a token in the S-expression query language.
 * 
 * @param type the token type
 * @param value the token value (text content)
 * @param position the character position in the input string
 */
public record SxlToken(TokenType type, String value, int position) {
	
	public enum TokenType {
		IDENTIFIER,    // function names, keywords
		STRING,        // 'quoted strings'
		NUMBER,        // integers and decimals
		LPAREN,        // (
		RPAREN,        // )
		COMMA,         // ,
		EOF            // end of input
	}
	
	@Override
	public String toString() {
		return switch (type) {
			case STRING -> "STRING('" + value + "')";
			case NUMBER, IDENTIFIER -> type + "(" + value + ")";
			default -> type.toString();
		};
	}
	
	public boolean isType(TokenType expectedType) {
		return this.type == expectedType;
	}
	
	public boolean isIdentifier(String expected) {
		return type == TokenType.IDENTIFIER && value.equals(expected);
	}
}

