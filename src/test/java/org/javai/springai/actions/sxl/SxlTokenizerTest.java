package org.javai.springai.actions.sxl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class SxlTokenizerTest {

	@Test
	void tokenizeEmptyString() {
		SxlTokenizer tokenizer = new SxlTokenizer("");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).hasSize(1);
		assertThat(tokens.getFirst().type()).isEqualTo(SxlToken.TokenType.EOF);
	}

	@Test
	void tokenizeWhitespaceOnly() {
		SxlTokenizer tokenizer = new SxlTokenizer("   \n\t  ");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).hasSize(1);
		assertThat(tokens.getFirst().type()).isEqualTo(SxlToken.TokenType.EOF);
	}

	@Test
	void tokenizeNullInput() {
		SxlTokenizer tokenizer = new SxlTokenizer(null);
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).hasSize(1);
		assertThat(tokens.getFirst().type()).isEqualTo(SxlToken.TokenType.EOF);
	}

	@Test
	void tokenizeParentheses() {
		SxlTokenizer tokenizer = new SxlTokenizer("()");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).hasSize(3);
		assertThat(tokens.get(0).type()).isEqualTo(SxlToken.TokenType.LPAREN);
		assertThat(tokens.get(0).value()).isEqualTo("(");
		assertThat(tokens.get(1).type()).isEqualTo(SxlToken.TokenType.RPAREN);
		assertThat(tokens.get(1).value()).isEqualTo(")");
		assertThat(tokens.get(2).type()).isEqualTo(SxlToken.TokenType.EOF);
	}

	@Test
	void tokenizeComma() {
		SxlTokenizer tokenizer = new SxlTokenizer(",");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).hasSize(2);
		assertThat(tokens.get(0).type()).isEqualTo(SxlToken.TokenType.COMMA);
		assertThat(tokens.get(0).value()).isEqualTo(",");
		assertThat(tokens.get(1).type()).isEqualTo(SxlToken.TokenType.EOF);
	}

	@Test
	void tokenizeSimpleIdentifier() {
		SxlTokenizer tokenizer = new SxlTokenizer("hello");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).hasSize(2);
		assertThat(tokens.get(0).type()).isEqualTo(SxlToken.TokenType.IDENTIFIER);
		assertThat(tokens.get(0).value()).isEqualTo("hello");
		assertThat(tokens.get(1).type()).isEqualTo(SxlToken.TokenType.EOF);
	}

	@Test
	void tokenizeIdentifierWithUnderscore() {
		SxlTokenizer tokenizer = new SxlTokenizer("hello_world");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).hasSize(2);
		assertThat(tokens.getFirst().type()).isEqualTo(SxlToken.TokenType.IDENTIFIER);
		assertThat(tokens.getFirst().value()).isEqualTo("hello_world");
	}

	@Test
	void tokenizeIdentifierWithNumbers() {
		SxlTokenizer tokenizer = new SxlTokenizer("var123");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).hasSize(2);
		assertThat(tokens.getFirst().type()).isEqualTo(SxlToken.TokenType.IDENTIFIER);
		assertThat(tokens.getFirst().value()).isEqualTo("var123");
	}

	@Test
	void tokenizeIdentifierStartingWithCapital() {
		SxlTokenizer tokenizer = new SxlTokenizer("Q");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).hasSize(2);
		assertThat(tokens.getFirst().type()).isEqualTo(SxlToken.TokenType.IDENTIFIER);
		assertThat(tokens.getFirst().value()).isEqualTo("Q");
	}

	@Test
	void tokenizePositiveInteger() {
		SxlTokenizer tokenizer = new SxlTokenizer("123");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).hasSize(2);
		assertThat(tokens.getFirst().type()).isEqualTo(SxlToken.TokenType.NUMBER);
		assertThat(tokens.getFirst().value()).isEqualTo("123");
	}

	@Test
	void tokenizeNegativeInteger() {
		SxlTokenizer tokenizer = new SxlTokenizer("-42");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).hasSize(2);
		assertThat(tokens.getFirst().type()).isEqualTo(SxlToken.TokenType.NUMBER);
		assertThat(tokens.getFirst().value()).isEqualTo("-42");
	}

	@Test
	void tokenizeFloat() {
		SxlTokenizer tokenizer = new SxlTokenizer("3.14");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).hasSize(2);
		assertThat(tokens.getFirst().type()).isEqualTo(SxlToken.TokenType.NUMBER);
		assertThat(tokens.getFirst().value()).isEqualTo("3.14");
	}

	@Test
	void tokenizeNegativeFloat() {
		SxlTokenizer tokenizer = new SxlTokenizer("-3.14");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).hasSize(2);
		assertThat(tokens.getFirst().type()).isEqualTo(SxlToken.TokenType.NUMBER);
		assertThat(tokens.getFirst().value()).isEqualTo("-3.14");
	}

	@Test
	void tokenizeSimpleString() {
		SxlTokenizer tokenizer = new SxlTokenizer("'hello'");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).hasSize(2);
		assertThat(tokens.getFirst().type()).isEqualTo(SxlToken.TokenType.STRING);
		assertThat(tokens.getFirst().value()).isEqualTo("hello");
	}

	@Test
	void tokenizeEmptyStringLiteral() {
		SxlTokenizer tokenizer = new SxlTokenizer("''");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).hasSize(2);
		assertThat(tokens.getFirst().type()).isEqualTo(SxlToken.TokenType.STRING);
		assertThat(tokens.getFirst().value()).isEqualTo("");
	}

	@Test
	void tokenizeStringWithSpaces() {
		SxlTokenizer tokenizer = new SxlTokenizer("'hello world'");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).hasSize(2);
		assertThat(tokens.getFirst().type()).isEqualTo(SxlToken.TokenType.STRING);
		assertThat(tokens.getFirst().value()).isEqualTo("hello world");
	}

	@Test
	void tokenizeStringWithEscapedNewline() {
		SxlTokenizer tokenizer = new SxlTokenizer("'hello\\nworld'");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).hasSize(2);
		assertThat(tokens.getFirst().type()).isEqualTo(SxlToken.TokenType.STRING);
		assertThat(tokens.getFirst().value()).isEqualTo("hello\nworld");
	}

	@Test
	void tokenizeStringWithEscapedTab() {
		SxlTokenizer tokenizer = new SxlTokenizer("'hello\\tworld'");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).hasSize(2);
		assertThat(tokens.getFirst().type()).isEqualTo(SxlToken.TokenType.STRING);
		assertThat(tokens.getFirst().value()).isEqualTo("hello\tworld");
	}

	@Test
	void tokenizeStringWithEscapedQuote() {
		SxlTokenizer tokenizer = new SxlTokenizer("'hello\\'world'");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).hasSize(2);
		assertThat(tokens.getFirst().type()).isEqualTo(SxlToken.TokenType.STRING);
		assertThat(tokens.getFirst().value()).isEqualTo("hello'world");
	}

	@Test
	void tokenizeStringWithEscapedBackslash() {
		SxlTokenizer tokenizer = new SxlTokenizer("'hello\\\\world'");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).hasSize(2);
		assertThat(tokens.getFirst().type()).isEqualTo(SxlToken.TokenType.STRING);
		assertThat(tokens.getFirst().value()).isEqualTo("hello\\world");
	}

	@Test
	void tokenizeSimpleSExpression() {
		SxlTokenizer tokenizer = new SxlTokenizer("(Q)");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).hasSize(4);
		assertThat(tokens.get(0).type()).isEqualTo(SxlToken.TokenType.LPAREN);
		assertThat(tokens.get(1).type()).isEqualTo(SxlToken.TokenType.IDENTIFIER);
		assertThat(tokens.get(1).value()).isEqualTo("Q");
		assertThat(tokens.get(2).type()).isEqualTo(SxlToken.TokenType.RPAREN);
		assertThat(tokens.get(3).type()).isEqualTo(SxlToken.TokenType.EOF);
	}

	@Test
	void tokenizeSExpressionWithArguments() {
		SxlTokenizer tokenizer = new SxlTokenizer("(F table t)");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).hasSize(6);
		assertThat(tokens.get(0).type()).isEqualTo(SxlToken.TokenType.LPAREN);
		assertThat(tokens.get(1).type()).isEqualTo(SxlToken.TokenType.IDENTIFIER);
		assertThat(tokens.get(1).value()).isEqualTo("F");
		assertThat(tokens.get(2).type()).isEqualTo(SxlToken.TokenType.IDENTIFIER);
		assertThat(tokens.get(2).value()).isEqualTo("table");
		assertThat(tokens.get(3).type()).isEqualTo(SxlToken.TokenType.IDENTIFIER);
		assertThat(tokens.get(3).value()).isEqualTo("t");
		assertThat(tokens.get(4).type()).isEqualTo(SxlToken.TokenType.RPAREN);
		assertThat(tokens.get(5).type()).isEqualTo(SxlToken.TokenType.EOF);
	}

	@Test
	void tokenizeNestedSExpression() {
		SxlTokenizer tokenizer = new SxlTokenizer("(Q (F orders o))");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).hasSize(9);
		assertThat(tokens.get(0).type()).isEqualTo(SxlToken.TokenType.LPAREN);
		assertThat(tokens.get(1).type()).isEqualTo(SxlToken.TokenType.IDENTIFIER);
		assertThat(tokens.get(1).value()).isEqualTo("Q");
		assertThat(tokens.get(2).type()).isEqualTo(SxlToken.TokenType.LPAREN);
		assertThat(tokens.get(3).type()).isEqualTo(SxlToken.TokenType.IDENTIFIER);
		assertThat(tokens.get(3).value()).isEqualTo("F");
		assertThat(tokens.get(4).type()).isEqualTo(SxlToken.TokenType.IDENTIFIER);
		assertThat(tokens.get(4).value()).isEqualTo("orders");
		assertThat(tokens.get(5).type()).isEqualTo(SxlToken.TokenType.IDENTIFIER);
		assertThat(tokens.get(5).value()).isEqualTo("o");
		assertThat(tokens.get(6).type()).isEqualTo(SxlToken.TokenType.RPAREN);
		assertThat(tokens.get(7).type()).isEqualTo(SxlToken.TokenType.RPAREN);
		assertThat(tokens.get(8).type()).isEqualTo(SxlToken.TokenType.EOF);
	}

	@Test
	void tokenizeSExpressionWithNumbers() {
		SxlTokenizer tokenizer = new SxlTokenizer("(L 10)");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).hasSize(5);
		assertThat(tokens.get(0).type()).isEqualTo(SxlToken.TokenType.LPAREN);
		assertThat(tokens.get(1).type()).isEqualTo(SxlToken.TokenType.IDENTIFIER);
		assertThat(tokens.get(1).value()).isEqualTo("L");
		assertThat(tokens.get(2).type()).isEqualTo(SxlToken.TokenType.NUMBER);
		assertThat(tokens.get(2).value()).isEqualTo("10");
		assertThat(tokens.get(3).type()).isEqualTo(SxlToken.TokenType.RPAREN);
		assertThat(tokens.get(4).type()).isEqualTo(SxlToken.TokenType.EOF);
	}

	@Test
	void tokenizeSExpressionWithString() {
		SxlTokenizer tokenizer = new SxlTokenizer("(W 'hello')");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).hasSize(5);
		assertThat(tokens.get(0).type()).isEqualTo(SxlToken.TokenType.LPAREN);
		assertThat(tokens.get(1).type()).isEqualTo(SxlToken.TokenType.IDENTIFIER);
		assertThat(tokens.get(1).value()).isEqualTo("W");
		assertThat(tokens.get(2).type()).isEqualTo(SxlToken.TokenType.STRING);
		assertThat(tokens.get(2).value()).isEqualTo("hello");
		assertThat(tokens.get(3).type()).isEqualTo(SxlToken.TokenType.RPAREN);
		assertThat(tokens.get(4).type()).isEqualTo(SxlToken.TokenType.EOF);
	}

	@Test
	void tokenizeComplexSExpression() {
		SxlTokenizer tokenizer = new SxlTokenizer("(Q (F fact_sales f) (S (AS f.id order_id)))");
		List<SxlToken> tokens = tokenizer.tokenize();

		// Verify structure: ( Q ( F fact_sales f ) ( S ( AS f.id order_id ) ) )
		// Token positions: 0=( 1=Q 2=( 3=F 4=fact_sales 5=f 6=) 7=( 8=S 9=( 10=AS 11=f.id 12=order_id 13=) 14=) 15=) 16=EOF
		assertThat(tokens).hasSize(17);
		assertThat(tokens.get(0).type()).isEqualTo(SxlToken.TokenType.LPAREN);
		assertThat(tokens.get(1).value()).isEqualTo("Q");
		assertThat(tokens.get(2).type()).isEqualTo(SxlToken.TokenType.LPAREN);
		assertThat(tokens.get(3).value()).isEqualTo("F");
		assertThat(tokens.get(4).value()).isEqualTo("fact_sales");
		assertThat(tokens.get(5).value()).isEqualTo("f");
		assertThat(tokens.get(6).type()).isEqualTo(SxlToken.TokenType.RPAREN);
		assertThat(tokens.get(7).type()).isEqualTo(SxlToken.TokenType.LPAREN);
		assertThat(tokens.get(8).value()).isEqualTo("S");
		assertThat(tokens.get(9).type()).isEqualTo(SxlToken.TokenType.LPAREN);
		assertThat(tokens.get(10).value()).isEqualTo("AS");
		assertThat(tokens.get(11).value()).isEqualTo("f.id");
		assertThat(tokens.get(12).value()).isEqualTo("order_id");
		assertThat(tokens.get(13).type()).isEqualTo(SxlToken.TokenType.RPAREN);
		assertThat(tokens.get(14).type()).isEqualTo(SxlToken.TokenType.RPAREN);
		assertThat(tokens.get(15).type()).isEqualTo(SxlToken.TokenType.RPAREN);
		assertThat(tokens.get(16).type()).isEqualTo(SxlToken.TokenType.EOF);
	}

	@Test
	void tokenizeSExpressionWithOperators() {
		SxlTokenizer tokenizer = new SxlTokenizer("(EQ x y)");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).hasSize(6);
		assertThat(tokens.get(0).type()).isEqualTo(SxlToken.TokenType.LPAREN);
		assertThat(tokens.get(1).type()).isEqualTo(SxlToken.TokenType.IDENTIFIER);
		assertThat(tokens.get(1).value()).isEqualTo("EQ");
		assertThat(tokens.get(2).type()).isEqualTo(SxlToken.TokenType.IDENTIFIER);
		assertThat(tokens.get(2).value()).isEqualTo("x");
		assertThat(tokens.get(3).type()).isEqualTo(SxlToken.TokenType.IDENTIFIER);
		assertThat(tokens.get(3).value()).isEqualTo("y");
		assertThat(tokens.get(4).type()).isEqualTo(SxlToken.TokenType.RPAREN);
	}

	@Test
	void tokenizeSExpressionWithComparisonOperators() {
		// Test various alphabetic comparison operators
		SxlTokenizer tokenizer = new SxlTokenizer("(LT x y) (GT a b) (LE val 100) (GE amount 0) (NE status 'ACTIVE')");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).isNotEmpty();
		assertThat(tokens.stream().anyMatch(t -> t.value().equals("LT"))).isTrue();
		assertThat(tokens.stream().anyMatch(t -> t.value().equals("GT"))).isTrue();
		assertThat(tokens.stream().anyMatch(t -> t.value().equals("LE"))).isTrue();
		assertThat(tokens.stream().anyMatch(t -> t.value().equals("GE"))).isTrue();
		assertThat(tokens.stream().anyMatch(t -> t.value().equals("NE"))).isTrue();
	}

	@Test
	void tokenizeSExpressionWithArithmeticOperators() {
		// Test arithmetic operators: ADD, SUB, MUL, DIV
		SxlTokenizer tokenizer = new SxlTokenizer("(ADD x y) (SUB a b) (MUL price qty) (DIV total count)");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).isNotEmpty();
		assertThat(tokens.stream().anyMatch(t -> t.value().equals("ADD"))).isTrue();
		assertThat(tokens.stream().anyMatch(t -> t.value().equals("SUB"))).isTrue();
		assertThat(tokens.stream().anyMatch(t -> t.value().equals("MUL"))).isTrue();
		assertThat(tokens.stream().anyMatch(t -> t.value().equals("DIV"))).isTrue();
	}

	@Test
	void tokenizeSExpressionWithAsKeyword() {
		// Test AS keyword for aliases
		SxlTokenizer tokenizer = new SxlTokenizer("(AS column_name alias)");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).hasSize(6);
		assertThat(tokens.get(1).value()).isEqualTo("AS");
		assertThat(tokens.get(2).value()).isEqualTo("column_name");
		assertThat(tokens.get(3).value()).isEqualTo("alias");
	}

	@Test
	void tokenizeMultipleExpressions() {
		SxlTokenizer tokenizer = new SxlTokenizer("(Q) (F)");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).hasSize(7);
		assertThat(tokens.get(0).type()).isEqualTo(SxlToken.TokenType.LPAREN);
		assertThat(tokens.get(1).value()).isEqualTo("Q");
		assertThat(tokens.get(2).type()).isEqualTo(SxlToken.TokenType.RPAREN);
		assertThat(tokens.get(3).type()).isEqualTo(SxlToken.TokenType.LPAREN);
		assertThat(tokens.get(4).value()).isEqualTo("F");
		assertThat(tokens.get(5).type()).isEqualTo(SxlToken.TokenType.RPAREN);
	}

	@Test
	void tokenizeWithExtraWhitespace() {
		SxlTokenizer tokenizer = new SxlTokenizer("(  Q   \n  (  F  table  t  )  \t  )");
		List<SxlToken> tokens = tokenizer.tokenize();

		// Should still parse correctly despite extra whitespace
		assertThat(tokens).hasSize(9);
		assertThat(tokens.get(1).value()).isEqualTo("Q");
		assertThat(tokens.get(3).value()).isEqualTo("F");
		assertThat(tokens.get(4).value()).isEqualTo("table");
		assertThat(tokens.get(5).value()).isEqualTo("t");
	}

	@Test
	void tokenizeColumnReferenceWithDot() {
		SxlTokenizer tokenizer = new SxlTokenizer("f.id");
		List<SxlToken> tokens = tokenizer.tokenize();

		// Dots are allowed in identifiers, so f.id should be a single identifier token
		assertThat(tokens).hasSize(2);
		assertThat(tokens.get(0).type()).isEqualTo(SxlToken.TokenType.IDENTIFIER);
		assertThat(tokens.get(0).value()).isEqualTo("f.id");
		assertThat(tokens.get(1).type()).isEqualTo(SxlToken.TokenType.EOF);
	}

	@Test
	void tokenizeFloatStartingWithZero() {
		SxlTokenizer tokenizer = new SxlTokenizer("0.5");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).hasSize(2);
		assertThat(tokens.getFirst().type()).isEqualTo(SxlToken.TokenType.NUMBER);
		assertThat(tokens.getFirst().value()).isEqualTo("0.5");
	}

	@Test
	void tokenizeMultipleNumbers() {
		SxlTokenizer tokenizer = new SxlTokenizer("123 -456 7.89 -0.1");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).hasSize(5);
		assertThat(tokens.get(0).value()).isEqualTo("123");
		assertThat(tokens.get(1).value()).isEqualTo("-456");
		assertThat(tokens.get(2).value()).isEqualTo("7.89");
		assertThat(tokens.get(3).value()).isEqualTo("-0.1");
	}

	@Test
	void tokenizeUnterminatedStringThrowsException() {
		SxlTokenizer tokenizer = new SxlTokenizer("'unterminated");
		
		assertThatThrownBy(tokenizer::tokenize)
			.isInstanceOf(SxlParseException.class)
			.hasMessageContaining("Unterminated string");
	}

	@Test
	void tokenizeUnexpectedCharacterThrowsException() {
		SxlTokenizer tokenizer = new SxlTokenizer("@invalid");
		
		assertThatThrownBy(tokenizer::tokenize)
			.isInstanceOf(SxlParseException.class)
			.hasMessageContaining("Unexpected character");
	}

	@Test
	void tokenizeExampleFromGrammar() {
		// Example from sxl-grammar-sql.txt: (Q (F fact_sales f) (S (AS f.id order_id) (AS f.amount total)))
		SxlTokenizer tokenizer = new SxlTokenizer("(Q (F fact_sales f) (S (AS f.id order_id) (AS f.amount total)))");
		List<SxlToken> tokens = tokenizer.tokenize();

		// Should successfully tokenize without exceptions
		assertThat(tokens).isNotEmpty();
		assertThat(tokens.getLast().type()).isEqualTo(SxlToken.TokenType.EOF);
		// Verify AS keyword is present
		assertThat(tokens.stream().anyMatch(t -> t.value().equals("AS"))).isTrue();
	}

	@Test
	void tokenizeExampleWithJoin() {
		// Example: (J products p (EQ o.product_id p.id))
		SxlTokenizer tokenizer = new SxlTokenizer("(J products p (EQ o.product_id p.id))");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).isNotEmpty();
		assertThat(tokens.get(1).value()).isEqualTo("J");
		assertThat(tokens.get(2).value()).isEqualTo("products");
		assertThat(tokens.get(3).value()).isEqualTo("p");
		// Verify EQ operator is present
		assertThat(tokens.stream().anyMatch(t -> t.value().equals("EQ"))).isTrue();
	}

	@Test
	void tokenizeExampleWithAggregate() {
		// Example: (AS (COUNT f.id) orders)
		SxlTokenizer tokenizer = new SxlTokenizer("(AS (COUNT f.id) orders)");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens).isNotEmpty();
		// Verify structure is parsed
		assertThat(tokens.getLast().type()).isEqualTo(SxlToken.TokenType.EOF);
		// Verify AS and COUNT are present
		assertThat(tokens.stream().anyMatch(t -> t.value().equals("AS"))).isTrue();
		assertThat(tokens.stream().anyMatch(t -> t.value().equals("COUNT"))).isTrue();
	}

	@Test
	void tokenizePositionTracking() {
		SxlTokenizer tokenizer = new SxlTokenizer("(Q hello)");
		List<SxlToken> tokens = tokenizer.tokenize();

		// Verify positions are tracked
		assertThat(tokens.get(0).position()).isEqualTo(0); // LPAREN at start
		assertThat(tokens.get(1).position()).isEqualTo(1); // Q after (
		assertThat(tokens.get(1).value()).isEqualTo("Q");
	}

	@Test
	void tokenizeTokenToStringRepresentation() {
		SxlTokenizer tokenizer = new SxlTokenizer("(hello 'world' 123)");
		List<SxlToken> tokens = tokenizer.tokenize();

		assertThat(tokens.get(1).toString()).contains("IDENTIFIER(hello)");
		assertThat(tokens.get(2).toString()).contains("STRING('world')");
		assertThat(tokens.get(3).toString()).contains("NUMBER(123)");
	}

	@Test
	void tokenizeTokenTypeChecking() {
		SxlTokenizer tokenizer = new SxlTokenizer("hello");
		List<SxlToken> tokens = tokenizer.tokenize();

		SxlToken token = tokens.getFirst();
		assertThat(token.isType(SxlToken.TokenType.IDENTIFIER)).isTrue();
		assertThat(token.isType(SxlToken.TokenType.STRING)).isFalse();
		assertThat(token.isIdentifier("hello")).isTrue();
		assertThat(token.isIdentifier("goodbye")).isFalse();
	}
}

