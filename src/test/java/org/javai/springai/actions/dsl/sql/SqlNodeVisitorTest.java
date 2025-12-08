package org.javai.springai.actions.dsl.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.javai.springai.actions.sxl.SxlNode;
import org.javai.springai.actions.sxl.SxlParser;
import org.javai.springai.actions.sxl.SxlTokenizer;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Comprehensive tests for SqlNodeVisitor.
 * Tests all SQL constructs supported by the SQL DSL.
 */
class SqlNodeVisitorTest {

	@Test
	void generateSimpleSelect() {
		String sxl = "(Q (F fact_sales f) (S (AS f.id order_id) (AS f.amount total)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).isEqualTo(
			"SELECT f.id AS order_id, f.amount AS total FROM fact_sales AS f"
		);
	}

	@Test
	void generateSelectWithDistinct() {
		String sxl = "(Q (D) (F orders o) (S (AS o.status status)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).isEqualTo(
			"SELECT DISTINCT o.status AS status FROM orders AS o"
		);
	}

	@Test
	void generateSelectWithInnerJoin() {
		String sxl = "(Q (F fact_sales f) (J dim_customer c (EQ c.id f.customer_id)) (S (AS c.name customer)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).isEqualTo(
			"SELECT c.name AS customer FROM fact_sales AS f INNER JOIN dim_customer AS c ON (c.id = f.customer_id)"
		);
	}

	@Test
	void generateSelectWithLeftJoin() {
		String sxl = "(Q (F orders o) (J_LEFT products p (EQ o.product_id p.id)) (S (AS p.name product)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).isEqualTo(
			"SELECT p.name AS product FROM orders AS o LEFT JOIN products AS p ON (o.product_id = p.id)"
		);
	}

	@Test
	void generateSelectWithRightJoin() {
		String sxl = "(Q (F orders o) (J_RIGHT products p (EQ o.product_id p.id)) (S (AS p.name product)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).isEqualTo(
			"SELECT p.name AS product FROM orders AS o RIGHT JOIN products AS p ON (o.product_id = p.id)"
		);
	}

	@Test
	void generateSelectWithFullJoin() {
		String sxl = "(Q (F orders o) (J_FULL products p (EQ o.product_id p.id)) (S (AS p.name product)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).isEqualTo(
			"SELECT p.name AS product FROM orders AS o FULL OUTER JOIN products AS p ON (o.product_id = p.id)"
		);
	}

	@Test
	void generateSelectWithMultipleJoins() {
		String sxl = "(Q (F fact_sales f) (J dim_customer c (EQ c.id f.customer_id)) (J_LEFT dim_product p (EQ p.id f.product_id)) (S (AS c.name customer) (AS p.name product)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("INNER JOIN dim_customer AS c ON");
		assertThat(sql).contains("LEFT JOIN dim_product AS p ON");
	}

	@Test
	void generateSelectWithWhereClause() {
		String sxl = "(Q (F fact_sales f) (S (AS f.amount amount)) (W (EQ f.status 'COMPLETED')))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).isEqualTo(
			"SELECT f.amount AS amount FROM fact_sales AS f WHERE (f.status = 'COMPLETED')"
		);
	}

	@Test
	void generateSelectWithWhereAndComparison() {
		String sxl = "(Q (F products p) (S (AS p.price price)) (W (GT p.price 100)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).isEqualTo(
			"SELECT p.price AS price FROM products AS p WHERE (p.price > 100)"
		);
	}

	@Test
	void generateSelectWithGroupBy() {
		String sxl = "(Q (F fact_sales f) (S (AS (COUNT f.id) orders) (AS (SUM f.amount) total)) (G f.customer_id))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).isEqualTo(
			"SELECT COUNT(f.id) AS orders, SUM(f.amount) AS total FROM fact_sales AS f GROUP BY f.customer_id"
		);
	}

	@Test
	void generateSelectWithHaving() {
		String sxl = "(Q (F fact_sales f) (S (AS (SUM f.amount) total)) (G f.customer_id) (H (GT (SUM f.amount) 5000)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("HAVING");
		assertThat(sql).contains("(SUM(f.amount) > 5000)");
	}

	@Test
	void generateSelectWithOrderBy() {
		// ORDER BY with simple expression
		String sxl = "(Q (F fact_sales f) (S (AS (SUM f.amount) total)) (G f.customer_id) (O (SUM f.amount)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("ORDER BY");
		assertThat(sql).contains("SUM(f.amount)");
	}

	@Test
	void generateSelectWithLimit() {
		String sxl = "(Q (F fact_sales f) (S (AS f.id id)) (L 10))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).isEqualTo(
			"SELECT f.id AS id FROM fact_sales AS f LIMIT 10"
		);
	}

	@Test
	void generateSelectWithComplexQuery() {
		String sxl = "(Q (F fact_sales f) (J dim_customer c (EQ c.id f.customer_id)) (S (AS c.name customer) (AS (COUNT f.id) orders) (AS (SUM f.amount) total)) (W (EQ f.status 'COMPLETED')) (G c.name) (H (GT (SUM f.amount) 5000)) (O (SUM f.amount)) (L 10))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("SELECT");
		assertThat(sql).contains("FROM fact_sales AS f");
		assertThat(sql).contains("INNER JOIN");
		assertThat(sql).contains("WHERE");
		assertThat(sql).contains("GROUP BY");
		assertThat(sql).contains("HAVING");
		assertThat(sql).contains("ORDER BY");
		assertThat(sql).contains("LIMIT 10");
	}

	// Comparison operators
	@Test
	void generateComparisonOperators() {
		testOperator("EQ", "=", "(Q (F table t) (S t.col) (W (EQ t.col 5)))");
		testOperator("NE", "!=", "(Q (F table t) (S t.col) (W (NE t.col 5)))");
		testOperator("LT", "<", "(Q (F table t) (S t.col) (W (LT t.col 5)))");
		testOperator("GT", ">", "(Q (F table t) (S t.col) (W (GT t.col 5)))");
		testOperator("LE", "<=", "(Q (F table t) (S t.col) (W (LE t.col 5)))");
		testOperator("GE", ">=", "(Q (F table t) (S t.col) (W (GE t.col 5)))");
	}

	private void testOperator(String op, String sqlOp, String sxl) {
		SxlNode node = parse(sxl);
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		// Operators in SQL have spaces around them: " = ", " > ", etc.
		assertThat(sql).contains(" " + sqlOp + " ");
	}

	// Logical operators
	@Test
	void generateAndOperator() {
		String sxl = "(Q (F table t) (S t.col) (W (AND (GT t.col 10) (LT t.col 20))))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("((t.col > 10)) AND ((t.col < 20))");
	}

	@Test
	void generateOrOperator() {
		String sxl = "(Q (F table t) (S t.col) (W (OR (LT t.col 10) (GT t.col 20))))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("((t.col < 10)) OR ((t.col > 20))");
	}

	@Test
	void generateNotOperator() {
		String sxl = "(Q (F table t) (S t.col) (W (NOT (EQ t.col 5))))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("NOT ((t.col = 5))");
	}

	// Pattern matching
	@Test
	void generateLikeOperator() {
		String sxl = "(Q (F customers c) (S c.name) (W (LIKE c.name '%Smith%')))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("LIKE");
		assertThat(sql).contains("'%Smith%'");
	}

	@Test
	void generateIlikeOperator() {
		// ILIKE generates UPPER() LIKE UPPER() in ANSI SQL
		String sxl = "(Q (F customers c) (S c.name) (W (ILIKE c.name '%smith%')))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("UPPER");
		assertThat(sql).contains("LIKE");
	}

	// Range and membership
	@Test
	void generateBetweenOperator() {
		String sxl = "(Q (F products p) (S p.price) (W (BETWEEN p.price 10 100)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("BETWEEN");
		assertThat(sql).contains("AND");
	}

	@Test
	void generateInOperator() {
		String sxl = "(Q (F orders o) (S o.status) (W (IN o.status 'PENDING' 'COMPLETED')))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("IN (");
		assertThat(sql).contains("'PENDING'");
		assertThat(sql).contains("'COMPLETED'");
	}

	@Test
	void generateNotInOperator() {
		String sxl = "(Q (F orders o) (S o.status) (W (NOT_IN o.status 'CANCELLED')))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("NOT IN (");
	}

	// NULL checks
	@Test
	void generateIsNullOperator() {
		String sxl = "(Q (F orders o) (S o.id) (W (IS_NULL o.deleted_at)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("IS NULL");
	}

	@Test
	void generateIsNotNullOperator() {
		String sxl = "(Q (F orders o) (S o.id) (W (IS_NOT_NULL o.deleted_at)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("IS NOT NULL");
	}

	// Aggregate functions
	@Test
	void generateCountFunction() {
		String sxl = "(Q (F orders o) (S (AS (COUNT o.id) count)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("COUNT(o.id)");
	}

	@Test
	void generateCountStar() {
		String sxl = "(Q (F orders o) (S (AS (COUNT) count)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("COUNT(*)");
	}

	@Test
	void generateSumFunction() {
		String sxl = "(Q (F orders o) (S (AS (SUM o.amount) total)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("SUM(o.amount)");
	}

	// String functions
	@Test
	void generateUpperFunction() {
		String sxl = "(Q (F customers c) (S (AS (UPPER c.name) name_upper)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("UPPER(c.name)");
	}

	// Date functions
	@Test
	void generateDateTruncFunction() {
		String sxl = "(Q (F orders o) (S (AS (DATE_TRUNC 'month' o.created_at) month)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("DATE_TRUNC(");
		assertThat(sql).contains("'month'");
	}

	@Test
	void generateExtractFunction() {
		String sxl = "(Q (F orders o) (S (AS (EXTRACT 'YEAR' o.created_at) year)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("EXTRACT(");
		assertThat(sql).contains("FROM");
		assertThat(sql).contains("'YEAR'");
	}

	// Arithmetic operators
	@Test
	void generateArithmeticOperators() {
		testArithmeticOperator("ADD", "+", "(Q (F table t) (S (AS (ADD t.col1 t.col2) sum)))");
		testArithmeticOperator("SUB", "-", "(Q (F table t) (S (AS (SUB t.col1 t.col2) diff)))");
		testArithmeticOperator("MUL", "*", "(Q (F table t) (S (AS (MUL t.col1 t.col2) product)))");
		testArithmeticOperator("DIV", "/", "(Q (F table t) (S (AS (DIV t.col1 t.col2) quotient)))");
	}

	private void testArithmeticOperator(String op, String sqlOp, String sxl) {
		SxlNode node = parse(sxl);
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		// Arithmetic operators in expressions: " + ", " - ", etc.
		assertThat(sql).contains(" " + sqlOp + " ");
	}

	// Literals
	@Test
	void generateStringLiteral() {
		String sxl = "(Q (F orders o) (S (AS o.status status)) (W (EQ o.status 'COMPLETED')))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("'COMPLETED'");
	}

	@Test
	void generateNumberLiteral() {
		String sxl = "(Q (F products p) (S (AS p.price price)) (W (GT p.price 100)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("100");
		assertThat(sql).doesNotContain("'100'");
	}

	@Test
	void generateNegativeNumberLiteral() {
		String sxl = "(Q (F table t) (S (AS -10 value)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("-10");
	}

	/**
	 * Validates that the generated SQL has correct syntax using JSqlParser.
	 * 
	 * @param sql the SQL string to validate
	 */
	protected void assertCorrectSqlSyntax(String sql) {
		try {
			Statement statement = CCJSqlParserUtil.parse(sql);
			assertThat(statement)
				.as("Generated SQL should parse to a valid Statement: %s", sql)
				.isNotNull();
		} catch (Exception e) {
			fail("Generated SQL failed syntax validation: %s%nError: %s", sql, e.getMessage(), e);
		}
	}

	// Helper method
	private SxlNode parse(String sxl) {
		SxlTokenizer tokenizer = new SxlTokenizer(sxl);
		List<org.javai.springai.actions.sxl.SxlToken> tokens = tokenizer.tokenize();
		SxlParser parser = new SxlParser(tokens);
		List<SxlNode> nodes = parser.parse();
		return nodes.get(0);
	}
}

