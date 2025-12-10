package org.javai.springai.dsl.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import java.util.List;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.javai.springai.sxl.SxlNode;
import org.javai.springai.sxl.SxlParser;
import org.javai.springai.sxl.SxlToken;
import org.javai.springai.sxl.SxlTokenizer;
import org.junit.jupiter.api.Test;

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

	// NEW STRING FUNCTIONS
	@Test
	void generateLowerFunction() {
		String sxl = "(Q (F customers c) (S (AS (LOWER c.name) name_lower)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("LOWER(c.name)");
	}

	@Test
	void generateLengthFunction() {
		String sxl = "(Q (F customers c) (S (AS (LENGTH c.email) email_length)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("LENGTH(c.email)");
	}

	@Test
	void generateConcatFunction() {
		String sxl = "(Q (F customers c) (S (AS (CONCAT c.first_name ' ' c.last_name) full_name)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("CONCAT(");
		assertThat(sql).contains("c.first_name");
		assertThat(sql).contains("c.last_name");
	}

	@Test
	void generateSubstrFunction() {
		String sxl = "(Q (F customers c) (S (AS (SUBSTR c.email 1 5) email_prefix)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("SUBSTR(");
		assertThat(sql).contains("1");
		assertThat(sql).contains("5");
	}

	@Test
	void generateTrimFunction() {
		String sxl = "(Q (F customers c) (S (AS (TRIM c.name) trimmed_name)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("TRIM(");
	}

	@Test
	void generateLtrimRtrimFunctions() {
		String sxl1 = "(Q (F customers c) (S (AS (LTRIM c.name) left_trim)))";
		String sxl2 = "(Q (F customers c) (S (AS (RTRIM c.name) right_trim)))";
		
		String sql1 = SqlNodeVisitor.generate(parse(sxl1));
		String sql2 = SqlNodeVisitor.generate(parse(sxl2));
		
		assertCorrectSqlSyntax(sql1);
		assertCorrectSqlSyntax(sql2);
		
		assertThat(sql1).contains("LTRIM(");
		assertThat(sql2).contains("RTRIM(");
	}

	@Test
	void generateReplaceFunction() {
		String sxl = "(Q (F customers c) (S (AS (REPLACE c.email '@old.com' '@new.com') new_email)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("REPLACE(");
		assertThat(sql).contains("@old.com");
		assertThat(sql).contains("@new.com");
	}

	@Test
	void generateLpadRpadFunctions() {
		String sxl1 = "(Q (F orders o) (S (AS (LPAD o.id 5) padded_id)))";
		String sxl2 = "(Q (F orders o) (S (AS (RPAD o.status 10) padded_status)))";
		
		String sql1 = SqlNodeVisitor.generate(parse(sxl1));
		String sql2 = SqlNodeVisitor.generate(parse(sxl2));
		
		assertCorrectSqlSyntax(sql1);
		assertCorrectSqlSyntax(sql2);
		
		assertThat(sql1).contains("LPAD(");
		assertThat(sql2).contains("RPAD(");
	}

	@Test
	void generateInstrFunction() {
		String sxl = "(Q (F customers c) (S (AS (INSTR c.email '@') at_position)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("INSTR(");
		assertThat(sql).contains("@");
	}

	// NEW NUMERIC FUNCTIONS
	@Test
	void generateAbsFunction() {
		String sxl = "(Q (F orders o) (S (AS (ABS (SUB o.expected o.actual)) difference)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("ABS(");
	}

	@Test
	void generateRoundFunction() {
		String sxl = "(Q (F orders o) (S (AS (ROUND o.amount 2) rounded_amount)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("ROUND(");
		assertThat(sql).contains("2");
	}

	@Test
	void generateCeilFloorFunctions() {
		String sxl1 = "(Q (F orders o) (S (AS (CEIL o.amount) ceiling)))";
		String sxl2 = "(Q (F orders o) (S (AS (FLOOR o.amount) flooring)))";
		
		String sql1 = SqlNodeVisitor.generate(parse(sxl1));
		String sql2 = SqlNodeVisitor.generate(parse(sxl2));
		
		assertCorrectSqlSyntax(sql1);
		assertCorrectSqlSyntax(sql2);
		
		assertThat(sql1).contains("CEIL(");
		assertThat(sql2).contains("FLOOR(");
	}

	@Test
	void generatePowerSqrtFunctions() {
		String sxl1 = "(Q (F orders o) (S (AS (POWER o.amount 2) squared)))";
		String sxl2 = "(Q (F orders o) (S (AS (SQRT o.amount) square_root)))";
		
		String sql1 = SqlNodeVisitor.generate(parse(sxl1));
		String sql2 = SqlNodeVisitor.generate(parse(sxl2));
		
		assertCorrectSqlSyntax(sql1);
		assertCorrectSqlSyntax(sql2);
		
		assertThat(sql1).contains("POWER(");
		assertThat(sql2).contains("SQRT(");
	}

	@Test
	void generateModFunction() {
		String sxl = "(Q (F orders o) (S (AS (MOD o.id 3) modulo)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("MOD(");
		assertThat(sql).contains("3");
	}

	// NEW DATE/TIME FUNCTIONS
	@Test
	void generateCurrentDateFunction() {
		String sxl = "(Q (F orders o) (S (AS (CURRENT_DATE) today)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("CURRENT_DATE");
	}

	@Test
	void generateCurrentTimestampFunction() {
		String sxl = "(Q (F orders o) (S (AS (CURRENT_TIMESTAMP) now)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("CURRENT_TIMESTAMP");
	}

	@Test
	void generateDateAddFunction() {
		String sxl = "(Q (F orders o) (S (AS (DATE_ADD o.order_date '1 MONTH') due_date)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("DATE_ADD(");
		assertThat(sql).contains("1 MONTH");
	}

	@Test
	void generateDateDiffFunction() {
		String sxl = "(Q (F orders o) (S (AS (DATE_DIFF o.shipped_date o.order_date) days_to_ship)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("DATE_DIFF(");
	}

	// NEW NULL HANDLING FUNCTIONS
	@Test
	void generateCoalesceFunction() {
		String sxl = "(Q (F customers c) (S (AS (COALESCE c.phone_work c.phone_home c.phone_mobile) phone)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("COALESCE(");
		assertThat(sql).contains("c.phone_work");
		assertThat(sql).contains("c.phone_home");
	}

	@Test
	void generateNullifFunction() {
		String sxl = "(Q (F orders o) (S (AS (NULLIF o.discount 0) discount_or_null)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("NULLIF(");
		assertThat(sql).contains("0");
	}

	@Test
	void generateNvlFunction() {
		String sxl = "(Q (F customers c) (S (AS (NVL c.phone 'No phone') contact_phone)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("NVL(");
		assertThat(sql).contains("No phone");
	}

	// NEW TYPE CONVERSION FUNCTIONS
	@Test
	void generateToDateFunction() {
		String sxl = "(Q (F orders o) (S (AS (TO_DATE o.order_date 'YYYY-MM-DD') formatted_date)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("TO_DATE(");
		assertThat(sql).contains("YYYY-MM-DD");
	}

	@Test
	void generateToNumberFunction() {
		String sxl = "(Q (F orders o) (S (AS (TO_NUMBER o.amount) numeric_amount)))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("TO_NUMBER(");
	}

	// AGGREGATE FUNCTIONS - EXPANDED
	@Test
	void generateAggregateFunction() {
		String sxlAvg = "(Q (F orders o) (S (AS (AVG o.amount) avg_amount)))";
		String sxlMin = "(Q (F orders o) (S (AS (MIN o.amount) min_amount)))";
		String sxlMax = "(Q (F orders o) (S (AS (MAX o.amount) max_amount)))";
		
		String sqlAvg = SqlNodeVisitor.generate(parse(sxlAvg));
		String sqlMin = SqlNodeVisitor.generate(parse(sxlMin));
		String sqlMax = SqlNodeVisitor.generate(parse(sxlMax));
		
		assertCorrectSqlSyntax(sqlAvg);
		assertCorrectSqlSyntax(sqlMin);
		assertCorrectSqlSyntax(sqlMax);
		
		assertThat(sqlAvg).contains("AVG(");
		assertThat(sqlMin).contains("MIN(");
		assertThat(sqlMax).contains("MAX(");
	}

	// SORT ORDER - ASC/DESC
	@Test
	void generateAscDescKeywords() {
		String sxlAsc = "(Q (F orders o) (S (AS o.id id)) (O (ASC o.amount)))";
		String sxlDesc = "(Q (F orders o) (S (AS o.id id)) (O (DESC o.amount)))";
		
		String sqlAsc = SqlNodeVisitor.generate(parse(sxlAsc));
		String sqlDesc = SqlNodeVisitor.generate(parse(sxlDesc));
		
		assertCorrectSqlSyntax(sqlAsc);
		assertCorrectSqlSyntax(sqlDesc);
		
		assertThat(sqlAsc).contains("ASC");
		assertThat(sqlDesc).contains("DESC");
	}

	// COMPLEX QUERY WITH NEW FUNCTIONS
	@Test
	void generateComplexQueryWithNewFunctions() {
		String sxl = "(Q (F orders o) (J customers c (EQ o.customer_id c.id)) (S (AS (CONCAT (UPPER (SUBSTR c.first_name 1 1)) '.' (LOWER c.last_name)) customer) (AS (ROUND o.amount 2) total) (AS (COALESCE o.discount 0) discount)) (W (AND (GT o.amount 100) (NE o.status 'CANCELLED'))) (O (DESC (ROUND o.amount 2))))";
		SxlNode node = parse(sxl);
		
		String sql = SqlNodeVisitor.generate(node);
		assertCorrectSqlSyntax(sql);
		
		assertThat(sql).contains("CONCAT(");
		assertThat(sql).contains("UPPER(");
		assertThat(sql).contains("SUBSTR(");
		assertThat(sql).contains("LOWER(");
		assertThat(sql).contains("ROUND(");
		assertThat(sql).contains("COALESCE(");
		assertThat(sql).contains("DESC");
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
		List<SxlToken> tokens = tokenizer.tokenize();
		SxlParser parser = new SxlParser(tokens);
		List<SxlNode> nodes = parser.parse();
		return nodes.get(0);
	}
}

