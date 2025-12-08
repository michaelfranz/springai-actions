package org.javai.springai.actions.sxl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for the universal s-expression parser.
 * The parser converts tokens from SxlTokenizer into a raw AST representation.
 * It must accept all syntactically correct s-expressions without knowing
 * anything about DSL semantics.
 */
class SxlParserTest {

	@Test
	void parseEmptyExpressionList() {
		SxlTokenizer tokenizer = new SxlTokenizer("");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).isEmpty();
	}

	@Test
	void parseSimpleIdentifier() {
		SxlTokenizer tokenizer = new SxlTokenizer("hello");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		SxlNode node = nodes.getFirst();
		assertThat(node.symbol()).isEqualTo("hello");
		assertThat(node.args()).isEmpty();
	}

	@Test
	void parseSimpleStringLiteral() {
		SxlTokenizer tokenizer = new SxlTokenizer("'hello world'");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		SxlNode node = nodes.getFirst();
		assertThat(node.isLiteral()).isTrue();
		assertThat(node.literalValue()).isEqualTo("hello world");
	}

	@Test
	void parseSimpleNumberLiteral() {
		SxlTokenizer tokenizer = new SxlTokenizer("123");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		SxlNode node = nodes.getFirst();
		assertThat(node.isLiteral()).isTrue();
		assertThat(node.literalValue()).isEqualTo("123");
	}

	@Test
	void parseNegativeNumberLiteral() {
		SxlTokenizer tokenizer = new SxlTokenizer("-42");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		assertThat(nodes.getFirst().isLiteral()).isTrue();
		assertThat(nodes.getFirst().literalValue()).isEqualTo("-42");
	}

	@Test
	void parseFloatLiteral() {
		SxlTokenizer tokenizer = new SxlTokenizer("3.14");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		assertThat(nodes.getFirst().isLiteral()).isTrue();
		assertThat(nodes.getFirst().literalValue()).isEqualTo("3.14");
	}

	@Test
	void parseSimpleSExpression() {
		SxlTokenizer tokenizer = new SxlTokenizer("(Q)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		SxlNode node = nodes.getFirst();
		assertThat(node.symbol()).isEqualTo("Q");
		assertThat(node.args()).isEmpty();
		assertThat(node.isLiteral()).isFalse();
	}

	@Test
	void parseSExpressionWithSingleIdentifierArg() {
		SxlTokenizer tokenizer = new SxlTokenizer("(F table)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		SxlNode node = nodes.getFirst();
		assertThat(node.symbol()).isEqualTo("F");
		assertThat(node.args()).hasSize(1);
		SxlNode arg = node.args().getFirst();
		assertThat(arg.symbol()).isEqualTo("table");
		assertThat(arg.args()).isEmpty();
	}

	@Test
	void parseSExpressionWithMultipleIdentifierArgs() {
		SxlTokenizer tokenizer = new SxlTokenizer("(F table alias)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		SxlNode node = nodes.getFirst();
		assertThat(node.symbol()).isEqualTo("F");
		assertThat(node.args()).hasSize(2);
		assertThat(node.args().get(0).symbol()).isEqualTo("table");
		assertThat(node.args().get(1).symbol()).isEqualTo("alias");
	}

	@Test
	void parseSExpressionWithStringLiteralArg() {
		SxlTokenizer tokenizer = new SxlTokenizer("(W 'hello')");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		SxlNode node = nodes.getFirst();
		assertThat(node.symbol()).isEqualTo("W");
		assertThat(node.args()).hasSize(1);
		SxlNode arg = node.args().getFirst();
		assertThat(arg.isLiteral()).isTrue();
		assertThat(arg.literalValue()).isEqualTo("hello");
	}

	@Test
	void parseSExpressionWithNumberLiteralArg() {
		SxlTokenizer tokenizer = new SxlTokenizer("(L 10)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		SxlNode node = nodes.getFirst();
		assertThat(node.symbol()).isEqualTo("L");
		assertThat(node.args()).hasSize(1);
		SxlNode arg = node.args().getFirst();
		assertThat(arg.isLiteral()).isTrue();
		assertThat(arg.literalValue()).isEqualTo("10");
	}

	@Test
	void parseSExpressionWithMixedLiteralArgs() {
		SxlTokenizer tokenizer = new SxlTokenizer("(FUNC 'hello' 42 3.14)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		SxlNode node = nodes.getFirst();
		assertThat(node.symbol()).isEqualTo("FUNC");
		assertThat(node.args()).hasSize(3);
		assertThat(node.args().get(0).isLiteral()).isTrue();
		assertThat(node.args().get(0).literalValue()).isEqualTo("hello");
		assertThat(node.args().get(1).isLiteral()).isTrue();
		assertThat(node.args().get(1).literalValue()).isEqualTo("42");
		assertThat(node.args().get(2).isLiteral()).isTrue();
		assertThat(node.args().get(2).literalValue()).isEqualTo("3.14");
	}

	@Test
	void parseNestedSExpression() {
		SxlTokenizer tokenizer = new SxlTokenizer("(Q (F table t))");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		SxlNode outer = nodes.getFirst();
		assertThat(outer.symbol()).isEqualTo("Q");
		assertThat(outer.args()).hasSize(1);
		
		SxlNode inner = outer.args().getFirst();
		assertThat(inner.symbol()).isEqualTo("F");
		assertThat(inner.args()).hasSize(2);
		assertThat(inner.args().get(0).symbol()).isEqualTo("table");
		assertThat(inner.args().get(1).symbol()).isEqualTo("t");
	}

	@Test
	void parseDeeplyNestedSExpression() {
		SxlTokenizer tokenizer = new SxlTokenizer("(Q (F table t) (S (AS col alias)))");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		SxlNode root = nodes.getFirst();
		assertThat(root.symbol()).isEqualTo("Q");
		assertThat(root.args()).hasSize(2);
		
		SxlNode fromClause = root.args().get(0);
		assertThat(fromClause.symbol()).isEqualTo("F");
		
		SxlNode selectClause = root.args().get(1);
		assertThat(selectClause.symbol()).isEqualTo("S");
		assertThat(selectClause.args()).hasSize(1);
		
		SxlNode asNode = selectClause.args().getFirst();
		assertThat(asNode.symbol()).isEqualTo("AS");
		assertThat(asNode.args()).hasSize(2);
	}

	@Test
	void parseSExpressionWithCommaSeparatedArgs() {
		// Commas should be treated as whitespace (optional separators)
		SxlTokenizer tokenizer = new SxlTokenizer("(FUNC arg1, arg2, arg3)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		SxlNode node = nodes.getFirst();
		assertThat(node.symbol()).isEqualTo("FUNC");
		assertThat(node.args()).hasSize(3);
		assertThat(node.args().get(0).symbol()).isEqualTo("arg1");
		assertThat(node.args().get(1).symbol()).isEqualTo("arg2");
		assertThat(node.args().get(2).symbol()).isEqualTo("arg3");
	}

	@Test
	void parseMultipleTopLevelExpressions() {
		SxlTokenizer tokenizer = new SxlTokenizer("(Q) (F table t)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(2);
		assertThat(nodes.get(0).symbol()).isEqualTo("Q");
		assertThat(nodes.get(1).symbol()).isEqualTo("F");
		assertThat(nodes.get(1).args()).hasSize(2);
	}

	@Test
	void parseMixedTopLevelExpressions() {
		SxlTokenizer tokenizer = new SxlTokenizer("hello 'world' 123 (FUNC)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(4);
		assertThat(nodes.get(0).symbol()).isEqualTo("hello");
		assertThat(nodes.get(1).isLiteral()).isTrue();
		assertThat(nodes.get(1).literalValue()).isEqualTo("world");
		assertThat(nodes.get(2).isLiteral()).isTrue();
		assertThat(nodes.get(2).literalValue()).isEqualTo("123");
		assertThat(nodes.get(3).symbol()).isEqualTo("FUNC");
	}

	@Test
	void parseEmptyParenthesesThrowsException() {
		SxlTokenizer tokenizer = new SxlTokenizer("()");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens);
		
		assertThatThrownBy(parser::parse)
			.isInstanceOf(SxlParseException.class)
			.hasMessageContaining("Empty expression");
	}

	@Test
	void parseUnmatchedLeftParenthesisThrowsException() {
		SxlTokenizer tokenizer = new SxlTokenizer("(Q (F table)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens);
		
		assertThatThrownBy(parser::parse)
			.isInstanceOf(SxlParseException.class)
			.hasMessageContaining("Unmatched")
			.hasMessageContaining("(");
	}

	@Test
	void parseUnmatchedRightParenthesisThrowsException() {
		SxlTokenizer tokenizer = new SxlTokenizer("(Q (F table)))");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens);
		
		assertThatThrownBy(parser::parse)
			.isInstanceOf(SxlParseException.class)
			.hasMessageContaining("Unexpected")
			.hasMessageContaining(")");
	}

	@Test
	void parseComplexNestedExpression() {
		// Complex real-world example from SQL grammar
		SxlTokenizer tokenizer = new SxlTokenizer("(Q (F fact_sales f) (S (AS f.id order_id) (AS f.amount total)))");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		SxlNode root = nodes.getFirst();
		assertThat(root.symbol()).isEqualTo("Q");
		assertThat(root.args()).hasSize(2);
		
		// Verify FROM clause
		SxlNode from = root.args().getFirst();
		assertThat(from.symbol()).isEqualTo("F");
		assertThat(from.args()).hasSize(2);
		assertThat(from.args().get(0).symbol()).isEqualTo("fact_sales");
		assertThat(from.args().get(1).symbol()).isEqualTo("f");
		
		// Verify SELECT clause
		SxlNode select = root.args().get(1);
		assertThat(select.symbol()).isEqualTo("S");
		assertThat(select.args()).hasSize(2);
		
		// Verify first AS clause
		SxlNode as1 = select.args().getFirst();
		assertThat(as1.symbol()).isEqualTo("AS");
		assertThat(as1.args()).hasSize(2);
		assertThat(as1.args().get(0).symbol()).isEqualTo("f.id");
		assertThat(as1.args().get(1).symbol()).isEqualTo("order_id");
	}

	@Test
	void parseExpressionWithColumnReference() {
		// Test column references with dots
		SxlTokenizer tokenizer = new SxlTokenizer("(EQ f.id p.id)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		SxlNode node = nodes.getFirst();
		assertThat(node.symbol()).isEqualTo("EQ");
		assertThat(node.args()).hasSize(2);
		assertThat(node.args().get(0).symbol()).isEqualTo("f.id");
		assertThat(node.args().get(1).symbol()).isEqualTo("p.id");
	}

	@Test
	void parseExpressionWithOperator() {
		SxlTokenizer tokenizer = new SxlTokenizer("(EQ x y)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		SxlNode node = nodes.getFirst();
		assertThat(node.symbol()).isEqualTo("EQ");
		assertThat(node.args()).hasSize(2);
		assertThat(node.args().get(0).symbol()).isEqualTo("x");
		assertThat(node.args().get(1).symbol()).isEqualTo("y");
	}

	@Test
	void parseExpressionWithFunctionCall() {
		SxlTokenizer tokenizer = new SxlTokenizer("(COUNT f.id)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		SxlNode node = nodes.getFirst();
		assertThat(node.symbol()).isEqualTo("COUNT");
		assertThat(node.args()).hasSize(1);
		assertThat(node.args().getFirst().symbol()).isEqualTo("f.id");
	}

	@Test
	void parseExpressionWithNestedFunctionCalls() {
		SxlTokenizer tokenizer = new SxlTokenizer("(ADD (MUL x y) (DIV a b))");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		SxlNode addNode = nodes.getFirst();
		assertThat(addNode.symbol()).isEqualTo("ADD");
		assertThat(addNode.args()).hasSize(2);
		
		SxlNode mulNode = addNode.args().getFirst();
		assertThat(mulNode.symbol()).isEqualTo("MUL");
		assertThat(mulNode.args()).hasSize(2);
		
		SxlNode divNode = addNode.args().get(1);
		assertThat(divNode.symbol()).isEqualTo("DIV");
		assertThat(divNode.args()).hasSize(2);
	}
}

