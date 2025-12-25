package org.javai.springai.sxl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.io.InputStream;
import java.util.List;
import org.javai.springai.sxl.SxlNode;
import org.javai.springai.sxl.SxlParseException;
import org.javai.springai.sxl.SxlParser;
import org.javai.springai.sxl.SxlToken;
import org.javai.springai.sxl.SxlTokenizer;
import org.javai.springai.sxl.grammar.SxlGrammarParser;
import org.javai.springai.sxl.grammar.SxlGrammar;
import org.javai.springai.sxl.ComplexDslValidator;
import org.javai.springai.sxl.DefaultValidatorRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the universal s-expression parser.
 * The parser converts tokens from SxlTokenizer into a raw AST representation.
 * It must accept all syntactically correct s-expressions without knowing
 * anything about DSL semantics.
 * 
 * Additionally, this test suite validates that SQL-related S-expressions are
 * semantically correct against the SQL DSL grammar, ensuring comprehensive
 * testing that validates both syntax AND semantics.
 */
class SxlParserTest {

	private SxlGrammar sqlGrammar;
	private DefaultValidatorRegistry registry;

	@BeforeEach
	void setUp() {
		SxlGrammarParser parser = new SxlGrammarParser();
		
		// Load SQL grammar from resources for validation of SQL expressions
		sqlGrammar = loadGrammar("META-INF/sxl-meta-grammar-sql.yml", parser);
		
		// Create registry with SQL grammar
		registry = new DefaultValidatorRegistry();
		registry.addGrammar("sxl-sql", sqlGrammar);
	}

	private SxlGrammar loadGrammar(String resourceName, SxlGrammarParser parser) {
		InputStream stream = getClass().getClassLoader().getResourceAsStream(resourceName);
		if (stream == null) {
			throw new IllegalStateException("Could not load grammar resource: " + resourceName);
		}
		try {
			return parser.parse(stream);
		} finally {
			try {
				stream.close();
			} catch (Exception e) {
				// Ignore
			}
		}
	}

	/**
	 * Parse and validate an S-expression against the SQL DSL grammar.
	 * This ensures the expression is both syntactically AND semantically correct.
	 */
	private List<SxlNode> parseAndValidateSql(String input) {
		String wrappedInput = "(EMBED sxl-sql " + input + ")";
		SxlTokenizer tokenizer = new SxlTokenizer(wrappedInput);
		List<SxlToken> tokens = tokenizer.tokenize();
		
		ComplexDslValidator validator = new ComplexDslValidator(registry);
		return validator.parseAndValidate(tokens);
	}

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

	/**
	 * SQL DSL Keyword Coverage and Validation Tests.
	 * 
	 * These tests ensure that:
	 * 1. Every keyword in the SQL DSL has at least one test case
	 * 2. Parsed S-expressions are validated against the SQL DSL grammar
	 * 3. Both syntactic AND semantic correctness is verified
	 */

	// ============================================================
	// SQL CLAUSE KEYWORDS (Q, D, F, S, AS, W, G, H, O, L)
	// ============================================================

	@Test
	void validateSqlQueryKeywordQ() {
		String sqlExpr = "(Q (F orders o) (S (AS o.id id)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
		assertThat(validated.getFirst().symbol()).isEqualTo("EMBED");
	}

	@Test
	void validateSqlDistinctKeywordD() {
		String sqlExpr = "(Q (D) (F orders o) (S o.status))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlFromKeywordF() {
		String sqlExpr = "(Q (F customers c) (S (AS c.id id)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlSelectKeywordS() {
		String sqlExpr = "(Q (F orders o) (S (AS o.id id) (AS o.amount amount)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlAsKeyword() {
		String sqlExpr = "(Q (F orders o) (S (AS o.id order_id) (AS o.status order_status)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlWhereKeywordW() {
		String sqlExpr = "(Q (F orders o) (S (AS o.id id)) (W (EQ o.status \"COMPLETED\")))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlGroupByKeywordG() {
		String sqlExpr = "(Q (F orders o) (S (AS o.status status) (AS (COUNT o.id) count)) (G o.status))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlHavingKeywordH() {
		String sqlExpr = "(Q (F orders o) (S (AS (COUNT o.id) count)) (G o.status) (H (GT (COUNT o.id) 5)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlOrderByKeywordO() {
		String sqlExpr = "(Q (F orders o) (S (AS o.id id)) (O (DESC o.amount)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlLimitKeywordL() {
		String sqlExpr = "(Q (F orders o) (S (AS o.id id)) (L 10))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	// ============================================================
	// SQL JOIN KEYWORDS (J, J_LEFT, J_RIGHT, J_FULL)
	// ============================================================

	@Test
	void validateSqlInnerJoinKeywordJ() {
		String sqlExpr = "(Q (F orders o) (J customers c (EQ o.customer_id c.id)) (S (AS o.id id)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlLeftJoinKeywordJLeft() {
		String sqlExpr = "(Q (F orders o) (J_LEFT customers c (EQ o.customer_id c.id)) (S (AS o.id id)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlRightJoinKeywordJRight() {
		String sqlExpr = "(Q (F orders o) (J_RIGHT customers c (EQ o.customer_id c.id)) (S (AS o.id id)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlFullOuterJoinKeywordJFull() {
		String sqlExpr = "(Q (F orders o) (J_FULL customers c (EQ o.customer_id c.id)) (S (AS o.id id)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	// ============================================================
	// SQL COMPARISON OPERATORS (EQ, NE, LT, GT, LE, GE)
	// ============================================================

	@Test
	void validateSqlEqualOperatorEQ() {
		String sqlExpr = "(Q (F orders o) (S (AS o.id id)) (W (EQ o.status \"ACTIVE\")))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlNotEqualOperatorNE() {
		String sqlExpr = "(Q (F orders o) (S (AS o.id id)) (W (NE o.status \"CANCELLED\")))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlLessThanOperatorLT() {
		String sqlExpr = "(Q (F orders o) (S (AS o.id id)) (W (LT o.amount 100)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlGreaterThanOperatorGT() {
		String sqlExpr = "(Q (F orders o) (S (AS o.id id)) (W (GT o.amount 500)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlLessEqualOperatorLE() {
		String sqlExpr = "(Q (F orders o) (S (AS o.id id)) (W (LE o.amount 1000)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlGreaterEqualOperatorGE() {
		String sqlExpr = "(Q (F orders o) (S (AS o.id id)) (W (GE o.amount 50)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	// ============================================================
	// SQL ARITHMETIC OPERATORS (ADD, SUB, MUL, DIV)
	// ============================================================

	@Test
	void validateSqlAdditionOperatorADD() {
		String sqlExpr = "(Q (F orders o) (S (AS (ADD o.amount o.tax) total)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlSubtractionOperatorSUB() {
		String sqlExpr = "(Q (F orders o) (S (AS (SUB o.amount o.discount) net)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlMultiplicationOperatorMUL() {
		String sqlExpr = "(Q (F orders o) (S (AS (MUL o.quantity o.unit_price) total)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlDivisionOperatorDIV() {
		String sqlExpr = "(Q (F orders o) (S (AS (DIV o.total_amount o.quantity) avg_price)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	// ============================================================
	// SQL BOOLEAN OPERATORS (AND, OR, NOT)
	// ============================================================

	@Test
	void validateSqlAndOperator() {
		String sqlExpr = "(Q (F orders o) (S (AS o.id id)) (W (AND (GT o.amount 100) (EQ o.status \"ACTIVE\"))))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlOrOperator() {
		String sqlExpr = "(Q (F orders o) (S (AS o.id id)) (W (OR (EQ o.status \"ACTIVE\") (EQ o.status \"PENDING\"))))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlNotOperator() {
		String sqlExpr = "(Q (F orders o) (S (AS o.id id)) (W (NOT (EQ o.status \"CANCELLED\"))))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	// ============================================================
	// SQL PATTERN MATCHING OPERATORS (LIKE, ILIKE)
	// ============================================================

	@Test
	void validateSqlLikeOperator() {
		String sqlExpr = "(Q (F customers c) (S (AS c.id id)) (W (LIKE c.name \"%Smith%\")))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlIlikeOperator() {
		String sqlExpr = "(Q (F customers c) (S (AS c.id id)) (W (ILIKE c.name \"%johnson%\")))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	// ============================================================
	// SQL RANGE AND MEMBERSHIP OPERATORS (BETWEEN, IN, NOT_IN)
	// ============================================================

	@Test
	void validateSqlBetweenOperator() {
		String sqlExpr = "(Q (F orders o) (S (AS o.id id)) (W (BETWEEN o.amount 100 500)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlInOperator() {
		String sqlExpr = "(Q (F orders o) (S (AS o.id id)) (W (IN o.status \"ACTIVE\" \"PENDING\" \"SHIPPED\")))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlNotInOperator() {
		String sqlExpr = "(Q (F orders o) (S (AS o.id id)) (W (NOT_IN o.status \"CANCELLED\" \"REJECTED\")))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	// ============================================================
	// SQL NULL CHECK OPERATORS (IS_NULL, IS_NOT_NULL)
	// ============================================================

	@Test
	void validateSqlIsNullOperator() {
		String sqlExpr = "(Q (F orders o) (S (AS o.id id)) (W (IS_NULL o.shipped_date)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlIsNotNullOperator() {
		String sqlExpr = "(Q (F orders o) (S (AS o.id id)) (W (IS_NOT_NULL o.customer_id)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	// ============================================================
	// SQL AGGREGATE FUNCTIONS (COUNT, SUM, AVG, MIN, MAX)
	// ============================================================

	@Test
	void validateSqlCountFunction() {
		String sqlExpr = "(Q (F orders o) (S (AS (COUNT o.id) total_orders)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlCountFunctionWithoutArg() {
		String sqlExpr = "(Q (F orders o) (S (AS (COUNT) total_rows)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlSumFunction() {
		String sqlExpr = "(Q (F orders o) (S (AS (SUM o.amount) total_amount)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlAvgFunction() {
		String sqlExpr = "(Q (F orders o) (S (AS (AVG o.amount) average_amount)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlMinFunction() {
		String sqlExpr = "(Q (F orders o) (S (AS (MIN o.amount) min_amount)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlMaxFunction() {
		String sqlExpr = "(Q (F orders o) (S (AS (MAX o.amount) max_amount)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	// ============================================================
	// SQL STRING FUNCTIONS (UPPER)
	// ============================================================

	@Test
	void validateSqlUpperFunction() {
		String sqlExpr = "(Q (F customers c) (S (AS (UPPER c.name) name_upper)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	// ============================================================
	// SQL DATE FUNCTIONS (DATE_TRUNC, EXTRACT)
	// ============================================================

	@Test
	void validateSqlDateTruncFunction() {
		String sqlExpr = "(Q (F orders o) (S (AS (DATE_TRUNC \"month\" o.order_date) order_month)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlExtractFunction() {
		String sqlExpr = "(Q (F orders o) (S (AS (EXTRACT \"YEAR\" o.order_date) order_year)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	// ============================================================
	// SQL SORT KEYWORDS (ASC, DESC)
	// ============================================================

	@Test
	void validateSqlAscKeyword() {
		String sqlExpr = "(Q (F orders o) (S (AS o.id id)) (O (ASC o.created_date)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlDescKeyword() {
		String sqlExpr = "(Q (F orders o) (S (AS o.id id)) (O (DESC o.amount)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	// ============================================================
	// COMPLEX SQL EXPRESSIONS COMBINING MULTIPLE KEYWORDS
	// ============================================================

	@Test
	void validateComplexSqlWithAllKeywords() {
		String sqlExpr = """
			(Q
			  (D)
			  (F orders o)
			  (J_LEFT customers c (EQ o.customer_id c.id))
			  (S (AS c.name customer) (AS (COUNT o.id) order_count) (AS (SUM o.amount) total))
			  (W (AND (GT o.amount 100) (NE o.status "CANCELLED")))
			  (G c.name)
			  (H (GE (SUM o.amount) 500))
			  (O (DESC (SUM o.amount)))
			  (L 20)
			)
			""";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	// ============================================================
	// NEW STRING FUNCTIONS (LOWER, LENGTH, CONCAT, SUBSTR, TRIM, etc.)
	// ============================================================

	@Test
	void validateSqlLowerFunction() {
		String sqlExpr = "(Q (F customers c) (S (AS (LOWER c.name) name_lower)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlLengthFunction() {
		String sqlExpr = "(Q (F customers c) (S (AS (LENGTH c.email) email_length)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlConcatFunction() {
		String sqlExpr = "(Q (F customers c) (S (AS (CONCAT c.first_name \" \" c.last_name) full_name)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlSubstrFunction() {
		String sqlExpr = "(Q (F customers c) (S (AS (SUBSTR c.email 1 5) email_prefix)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlTrimFunction() {
		String sqlExpr = "(Q (F customers c) (S (AS (TRIM c.name) trimmed_name)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlLtrimRtrimFunctions() {
		String sqlExpr = "(Q (F customers c) (S (AS (LTRIM c.name) left_trim) (AS (RTRIM c.name) right_trim)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlReplaceFunction() {
		String sqlExpr = "(Q (F customers c) (S (AS (REPLACE c.email \"@old.com\" \"@new.com\") new_email)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlLpadRpadFunctions() {
		String sqlExpr = "(Q (F orders o) (S (AS (LPAD o.id 5) padded_id) (AS (RPAD o.status 10) padded_status)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlInstrFunction() {
		String sqlExpr = "(Q (F customers c) (S (AS (INSTR c.email \"@\") at_position)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	// ============================================================
	// NEW NUMERIC FUNCTIONS (ABS, ROUND, CEIL, FLOOR, POWER, SQRT, MOD)
	// ============================================================

	@Test
	void validateSqlAbsFunction() {
		String sqlExpr = "(Q (F orders o) (S (AS (ABS (SUB o.expected o.actual)) difference)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlRoundFunction() {
		String sqlExpr = "(Q (F orders o) (S (AS (ROUND o.amount 2) rounded_amount)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlCeilFloorFunctions() {
		String sqlExpr = "(Q (F orders o) (S (AS (CEIL o.amount) ceiling) (AS (FLOOR o.amount) flooring)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlPowerSqrtFunctions() {
		String sqlExpr = "(Q (F orders o) (S (AS (POWER o.amount 2) squared) (AS (SQRT o.amount) square_root)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlModFunction() {
		String sqlExpr = "(Q (F orders o) (S (AS (MOD o.id 3) modulo)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	// ============================================================
	// NEW DATE/TIME FUNCTIONS (CURRENT_DATE, CURRENT_TIMESTAMP, DATE_ADD, DATE_DIFF)
	// ============================================================

	@Test
	void validateSqlCurrentDateFunction() {
		String sqlExpr = "(Q (F orders o) (S (AS o.id id) (AS (CURRENT_DATE) today)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlCurrentTimestampFunction() {
		String sqlExpr = "(Q (F orders o) (S (AS o.id id) (AS (CURRENT_TIMESTAMP) now)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlDateAddFunction() {
		String sqlExpr = "(Q (F orders o) (S (AS (DATE_ADD o.order_date \"1 MONTH\") due_date)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlDateDiffFunction() {
		String sqlExpr = "(Q (F orders o) (S (AS (DATE_DIFF o.shipped_date o.order_date) days_to_ship)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	// ============================================================
	// NEW NULL HANDLING FUNCTIONS (COALESCE, NULLIF, NVL)
	// ============================================================

	@Test
	void validateSqlCoalesceFunction() {
		String sqlExpr = "(Q (F customers c) (S (AS (COALESCE c.phone_work c.phone_home c.phone_mobile) phone)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlNullifFunction() {
		String sqlExpr = "(Q (F orders o) (S (AS (NULLIF o.discount 0) discount_or_null)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlNvlFunction() {
		String sqlExpr = "(Q (F customers c) (S (AS (NVL c.phone \"No phone\") contact_phone)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	// ============================================================
	// NEW TYPE CONVERSION FUNCTIONS (TO_DATE, TO_NUMBER)
	// ============================================================

	@Test
	void validateSqlToDateFunction() {
		String sqlExpr = "(Q (F orders o) (S (AS o.id id)) (W (EQ (TO_DATE o.order_date \"YYYY-MM-DD\") (CURRENT_DATE))))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	@Test
	void validateSqlToNumberFunction() {
		String sqlExpr = "(Q (F orders o) (S (AS (TO_NUMBER o.amount) numeric_amount)))";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}

	// ============================================================
	// COMPLEX EXPRESSIONS WITH NEW FUNCTIONS
	// ============================================================

	@Test
	void validateComplexQueryWithNewFunctions() {
		String sqlExpr = """
			(Q
			  (F orders o)
			  (J customers c (EQ o.customer_id c.id))
			  (S 
			    (AS (CONCAT (UPPER (SUBSTR c.first_name 1 1)) \".\" (LOWER c.last_name)) customer)
			    (AS (ROUND o.amount 2) total)
			    (AS (COALESCE o.discount 0) discount)
			  )
			  (W (AND (GT o.amount 100) (NE o.status "CANCELLED")))
			)
			""";
		List<SxlNode> validated = parseAndValidateSql(sqlExpr);
		assertThat(validated).hasSize(1);
	}
}

