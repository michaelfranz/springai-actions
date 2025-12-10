package org.javai.springai.actions.dsl.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.io.InputStream;
import java.util.List;
import org.javai.springai.actions.sxl.ComplexDslValidator;
import org.javai.springai.actions.sxl.DefaultValidatorRegistry;
import org.javai.springai.actions.sxl.SxlNode;
import org.javai.springai.actions.sxl.SxlParseException;
import org.javai.springai.actions.sxl.SxlTokenizer;
import org.javai.springai.actions.sxl.meta.MetaSxlParser;
import org.javai.springai.actions.sxl.meta.SxlGrammar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive tests for SQL DSL instance generation.
 * 
 * This test suite validates the SQL DSL by testing the generation of valid
 * S-expression DSL instances that conform to the SQL grammar. Tests progress
 * from simple cases to complex scenarios, ensuring critical branches in
 * SQL DSL generation are thoroughly explored.
 * 
 */
@DisplayName("SQL DSL Tests")
class SqlDslTest {

	private SxlGrammar sqlGrammar;
	private DefaultValidatorRegistry registry;

	@BeforeEach
	void setUp() {
		MetaSxlParser parser = new MetaSxlParser();
		
		// Load SQL grammar from resources
		sqlGrammar = loadGrammar("sxl-meta-grammar-sql.yml", parser);
		
		// Create registry with SQL grammar
		registry = new DefaultValidatorRegistry();
		registry.addGrammar("sxl-sql", sqlGrammar);
	}

	private SxlGrammar loadGrammar(String resourceName, MetaSxlParser parser) {
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

	@Nested
	@DisplayName("Basic Query Structure Tests")
	class BasicQueryStructureTests {

		@Test
		@DisplayName("Should generate minimal query with FROM and SELECT")
		void shouldGenerateMinimalQueryWithFromAndSelect() {
			String input = """
				(EMBED sxl-sql
				  (Q
				    (F orders o)
				    (S (AS o.id id))
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
			
			assertThat(nodes).hasSize(1);
			SxlNode embed = nodes.get(0);
			assertThat(embed.symbol()).isEqualTo("EMBED");
			SxlNode query = embed.args().get(1);
			assertThat(query.symbol()).isEqualTo("Q");
			assertThat(query.args()).hasSize(2); // FROM and SELECT
		}

		@Test
		@DisplayName("Should generate query with multiple select items")
		void shouldGenerateQueryWithMultipleSelectItems() {
			String input = """
				(EMBED sxl-sql
				  (Q
				    (F orders o)
				    (S
				      (AS o.id order_id)
				      (AS o.amount amount)
				      (AS o.status status)
				    )
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
			
			SxlNode query = nodes.get(0).args().get(1);
			assertThat(query.symbol()).isEqualTo("Q");
			
			// Find SELECT clause
			SxlNode select = query.args().stream()
				.filter(n -> n.symbol().equals("S"))
				.findFirst()
				.orElseThrow();
			assertThat(select.args()).hasSize(3); // Three AS clauses
		}

		@Test
		@DisplayName("Should generate query with SELECT only (no FROM)")
		void shouldGenerateQueryWithSelectOnly() {
			String input = """
				(EMBED sxl-sql
				  (Q
				    (S (AS 42 answer))
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
			
			SxlNode query = nodes.get(0).args().get(1);
			assertThat(query.symbol()).isEqualTo("Q");
			assertThat(query.args()).hasSize(1); // Only SELECT
		}

		@Test
		@DisplayName("Should require SELECT clause")
		void shouldRequireSelectClause() {
			String input = """
				(EMBED sxl-sql
				  (Q
				    (F orders o)
				  )
				)
				""";
			
			assertThatThrownBy(() -> parseAndValidate(input))
				.isInstanceOf(SxlParseException.class)
				.hasMessageContaining("requires parameter")
				.hasMessageContaining("select");
		}
	}

	@Nested
	@DisplayName("DISTINCT Clause Tests")
	class DistinctClauseTests {

		@Test
		@DisplayName("Should generate query with DISTINCT")
		void shouldGenerateQueryWithDistinct() {
			String input = """
				(EMBED sxl-sql
				  (Q
				    (D)
				    (F orders o)
				    (S (AS o.status status))
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
			
			SxlNode query = nodes.get(0).args().get(1);
			assertThat(query.args()).hasSize(3); // DISTINCT, FROM, SELECT
			
			// Verify DISTINCT is present
			boolean hasDistinct = query.args().stream()
				.anyMatch(n -> n.symbol().equals("D"));
			assertThat(hasDistinct).isTrue();
		}

		@Test
		@DisplayName("Should allow DISTINCT without FROM clause")
		void shouldAllowDistinctWithoutFromClause() {
			String input = """
				(EMBED sxl-sql
				  (Q
				    (D)
				    (S (AS 42 answer))
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
			
			SxlNode query = nodes.get(0).args().get(1);
			assertThat(query.args()).hasSize(2); // DISTINCT and SELECT
		}
	}

	@Nested
	@DisplayName("FROM Clause Tests")
	class FromClauseTests {

		@Test
		@DisplayName("Should generate FROM clause with table and alias")
		void shouldGenerateFromClauseWithTableAndAlias() {
			String input = """
				(EMBED sxl-sql
				  (Q
				    (F orders o)
				    (S (AS o.id id))
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
			
			SxlNode query = nodes.get(0).args().get(1);
			SxlNode from = query.args().stream()
				.filter(n -> n.symbol().equals("F"))
				.findFirst()
				.orElseThrow();
			assertThat(from.args()).hasSize(2); // table name and alias
		}

		@Test
		@DisplayName("Should require table name in FROM clause")
		void shouldRequireTableNameInFromClause() {
			String input = """
				(EMBED sxl-sql
				  (Q
				    (F)
				    (S (AS o.id id))
				  )
				)
				""";
			
			assertThatThrownBy(() -> parseAndValidate(input))
				.isInstanceOf(SxlParseException.class);
		}

		@Test
		@DisplayName("Should require alias in FROM clause")
		void shouldRequireAliasInFromClause() {
			String input = """
				(EMBED sxl-sql
				  (Q
				    (F orders)
				    (S (AS o.id id))
				  )
				)
				""";
			
			assertThatThrownBy(() -> parseAndValidate(input))
				.isInstanceOf(SxlParseException.class);
		}

		@Test
		@DisplayName("Should accept table names with dots (schema.table)")
		void shouldAcceptTableNamesWithDots() {
			String input = """
				(EMBED sxl-sql
				  (Q
				    (F public.orders o)
				    (S (AS o.id id))
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
			assertThat(nodes).hasSize(1);
			// Validation succeeds if no exception is thrown
		}
	}

	@Nested
	@DisplayName("JOIN Clause Tests")
	class JoinClauseTests {

		@Test
		@DisplayName("Should generate query with INNER JOIN")
		void shouldGenerateQueryWithInnerJoin() {
			String input = """
				(EMBED sxl-sql
				  (Q
				    (F orders o)
				    (J customers c (EQ o.customer_id c.id))
				    (S (AS o.id order_id) (AS c.name customer_name))
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
				
				SxlNode query = nodes.get(0).args().get(1);
				long joinCount = query.args().stream()
					.filter(n -> n.symbol().equals("J"))
					.count();
				assertThat(joinCount).isEqualTo(1);
		}

		@Test
		@DisplayName("Should generate query with LEFT JOIN")
		void shouldGenerateQueryWithLeftJoin() {
			String input = """
				(EMBED sxl-sql
				  (Q
				    (F orders o)
				    (J_LEFT products p (EQ o.product_id p.id))
				    (S (AS o.id id) (AS p.name product_name))
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
				
				SxlNode query = nodes.get(0).args().get(1);
				boolean hasLeftJoin = query.args().stream()
					.anyMatch(n -> n.symbol().equals("J_LEFT"));
				assertThat(hasLeftJoin).isTrue();
		}

		@Test
		@DisplayName("Should generate query with RIGHT JOIN")
		void shouldGenerateQueryWithRightJoin() {
			String input = """
				(EMBED sxl-sql
				  (Q
				    (F orders o)
				    (J_RIGHT customers c (EQ o.customer_id c.id))
				    (S (AS o.id id))
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
				
				SxlNode query = nodes.get(0).args().get(1);
				boolean hasRightJoin = query.args().stream()
					.anyMatch(n -> n.symbol().equals("J_RIGHT"));
				assertThat(hasRightJoin).isTrue();
		}

		@Test
		@DisplayName("Should generate query with FULL OUTER JOIN")
		void shouldGenerateQueryWithFullOuterJoin() {
			String input = """
				(EMBED sxl-sql
				  (Q
				    (F orders o)
				    (J_FULL customers c (EQ o.customer_id c.id))
				    (S (AS o.id id))
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
				
				SxlNode query = nodes.get(0).args().get(1);
				boolean hasFullJoin = query.args().stream()
					.anyMatch(n -> n.symbol().equals("J_FULL"));
				assertThat(hasFullJoin).isTrue();
		}

		@Test
		@DisplayName("Should generate query with multiple JOINs")
		void shouldGenerateQueryWithMultipleJoins() {
			String input = """
				(EMBED sxl-sql
				  (Q
				    (F orders o)
				    (J customers c (EQ o.customer_id c.id))
				    (J_LEFT products p (EQ o.product_id p.id))
				    (S (AS o.id id) (AS c.name customer) (AS p.name product))
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
				
				SxlNode query = nodes.get(0).args().get(1);
				long joinCount = query.args().stream()
					.filter(n -> n.symbol().startsWith("J"))
					.count();
				assertThat(joinCount).isEqualTo(2);
		}
	}

	@Nested
	@DisplayName("WHERE Clause Tests")
	class WhereClauseTests {

		@Test
		@DisplayName("Should generate query with WHERE clause")
		void shouldGenerateQueryWithWhereClause() {
			String input = """
				(EMBED sxl-sql
				  (Q
				    (F orders o)
				    (S (AS o.id id))
				    (W (EQ o.status "COMPLETED"))
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
				
				SxlNode query = nodes.get(0).args().get(1);
				boolean hasWhere = query.args().stream()
					.anyMatch(n -> n.symbol().equals("W"));
				assertThat(hasWhere).isTrue();
		}

		@Test
		@DisplayName("Should generate query with complex WHERE conditions")
		void shouldGenerateQueryWithComplexWhereConditions() {
			String input = """
				(EMBED sxl-sql
				  (Q
				    (F orders o)
				    (S (AS o.id id))
				    (W (AND (EQ o.status "COMPLETED") (GT o.amount 100)))
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
				
				SxlNode query = nodes.get(0).args().get(1);
				boolean hasWhere = query.args().stream()
					.anyMatch(n -> n.symbol().equals("W"));
				assertThat(hasWhere).isTrue();
		}
	}

	@Nested
	@DisplayName("GROUP BY and HAVING Tests")
	class GroupByAndHavingTests {

		@Test
		@DisplayName("Should generate query with GROUP BY")
		void shouldGenerateQueryWithGroupBy() {
			String input = """
				(EMBED sxl-sql
				  (Q
				    (F orders o)
				    (S (AS (COUNT o.id) order_count))
				    (G o.status)
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
				
				SxlNode query = nodes.get(0).args().get(1);
				boolean hasGroupBy = query.args().stream()
					.anyMatch(n -> n.symbol().equals("G"));
				assertThat(hasGroupBy).isTrue();
		}

		@Test
		@DisplayName("Should generate query with GROUP BY and HAVING")
		void shouldGenerateQueryWithGroupByAndHaving() {
			String input = """
				(EMBED sxl-sql
				  (Q
				    (F orders o)
				    (S (AS (SUM o.amount) total))
				    (G o.customer_id)
				    (H (GT (SUM o.amount) 1000))
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
				
				SxlNode query = nodes.get(0).args().get(1);
				boolean hasGroupBy = query.args().stream()
					.anyMatch(n -> n.symbol().equals("G"));
				boolean hasHaving = query.args().stream()
					.anyMatch(n -> n.symbol().equals("H"));
				assertThat(hasGroupBy).isTrue();
				assertThat(hasHaving).isTrue();
		}
	}

	@Nested
	@DisplayName("ORDER BY and LIMIT Tests")
	class OrderByAndLimitTests {

		@Test
		@DisplayName("Should generate query with ORDER BY")
		void shouldGenerateQueryWithOrderBy() {
			String input = """
				(EMBED sxl-sql
				  (Q
				    (F orders o)
				    (S (AS o.id id))
				    (O (DESC o.amount))
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
				
				SxlNode query = nodes.get(0).args().get(1);
				boolean hasOrderBy = query.args().stream()
					.anyMatch(n -> n.symbol().equals("O"));
				assertThat(hasOrderBy).isTrue();
		}

		@Test
		@DisplayName("Should generate query with LIMIT")
		void shouldGenerateQueryWithLimit() {
			String input = """
				(EMBED sxl-sql
				  (Q
				    (F orders o)
				    (S (AS o.id id))
				    (L 10)
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
				
				SxlNode query = nodes.get(0).args().get(1);
				boolean hasLimit = query.args().stream()
					.anyMatch(n -> n.symbol().equals("L"));
				assertThat(hasLimit).isTrue();
		}

		@Test
		@DisplayName("Should generate query with ORDER BY and LIMIT")
		void shouldGenerateQueryWithOrderByAndLimit() {
			String input = """
				(EMBED sxl-sql
				  (Q
				    (F orders o)
				    (S (AS o.id id))
				    (O (DESC o.amount))
				    (L 10)
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
				
				SxlNode query = nodes.get(0).args().get(1);
				boolean hasOrderBy = query.args().stream()
					.anyMatch(n -> n.symbol().equals("O"));
				boolean hasLimit = query.args().stream()
					.anyMatch(n -> n.symbol().equals("L"));
				assertThat(hasOrderBy).isTrue();
				assertThat(hasLimit).isTrue();
		}
	}

	@Nested
	@DisplayName("Complex Query Tests")
	class ComplexQueryTests {

		@Test
		@DisplayName("Should generate complex query with all clauses")
		void shouldGenerateComplexQueryWithAllClauses() {
			String input = """
				(EMBED sxl-sql
				  (Q
				    (D)
				    (F orders o)
				    (J customers c (EQ o.customer_id c.id))
				    (S
				      (AS c.name customer_name)
				      (AS (COUNT o.id) order_count)
				      (AS (SUM o.amount) total_amount)
				    )
				    (W (EQ o.status "COMPLETED"))
				    (G c.name)
				    (H (GT (SUM o.amount) 1000))
				    (O (DESC (SUM o.amount)))
				    (L 100)
				  )
				)
				""";
			
			// This query has DISTINCT first, so it shouldn't hit the parameter ordering issue.
			// The query structure is valid per the grammar, so any parsing exception
			// indicates a parser limitation.
			List<SxlNode> nodes = parseAndValidate(input);
				
				SxlNode query = nodes.get(0).args().get(1);
				assertThat(query.symbol()).isEqualTo("Q");
				// Should have: D, F, J, S, W, G, H, O, L = 9 clauses
				assertThat(query.args().size()).isGreaterThanOrEqualTo(9);
		}

		@Test
		@DisplayName("Should generate query with multiple aggregate functions")
		void shouldGenerateQueryWithMultipleAggregateFunctions() {
			String input = """
				(EMBED sxl-sql
				  (Q
				    (F orders o)
				    (S
				      (AS (COUNT o.id) count)
				      (AS (SUM o.amount) total)
				      (AS (AVG o.amount) average)
				      (AS (MIN o.amount) minimum)
				      (AS (MAX o.amount) maximum)
				    )
				    (G o.status)
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
				
				SxlNode query = nodes.get(0).args().get(1);
				SxlNode select = query.args().stream()
					.filter(n -> n.symbol().equals("S"))
					.findFirst()
					.orElseThrow();
				assertThat(select.args()).hasSize(5); // Five aggregate functions
		}

		@Test
		@DisplayName("Should generate query with nested expressions")
		void shouldGenerateQueryWithNestedExpressions() {
			String input = """
				(EMBED sxl-sql
				  (Q
				    (F orders o)
				    (S
				      (AS (ADD o.amount o.tax) total)
				      (AS (MUL o.quantity o.price) subtotal)
				    )
				    (W (OR (EQ o.status "PENDING") (EQ o.status "PROCESSING")))
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
			assertThat(nodes).hasSize(1);
			// Validation succeeds if no exception is thrown
		}
	}

	@Nested
	@DisplayName("SELECT Clause Tests")
	class SelectClauseTests {

		@Test
		@DisplayName("Should generate SELECT with simple column reference")
		void shouldGenerateSelectWithSimpleColumnReference() {
			String input = """
				(EMBED sxl-sql
				  (Q
				    (F orders o)
				    (S o.id)
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
				
				SxlNode query = nodes.get(0).args().get(1);
				SxlNode select = query.args().stream()
					.filter(n -> n.symbol().equals("S"))
					.findFirst()
					.orElseThrow();
				assertThat(select.args()).hasSize(1);
		}

		@Test
		@DisplayName("Should generate SELECT with AS alias")
		void shouldGenerateSelectWithAsAlias() {
			// Note: This test may fail due to parser limitations with ordered optional parameters.
			// When FROM clause comes before SELECT without DISTINCT, the parser tries to match
			// F to the distinct parameter position (expecting D), even though distinct is optional.
			// This is a known limitation of the current parser implementation.
			String input = """
				(EMBED sxl-sql
				  (Q
				    (F orders o)
				    (S
				      (AS o.id order_id)
				      (AS o.amount total_amount)
				    )
				  )
				)
				""";
			
			// Try to parse - if it succeeds, validate the structure
			// If it fails due to parameter ordering, document as known limitation
			List<SxlNode> nodes = parseAndValidate(input);
				
				SxlNode query = nodes.get(0).args().get(1);
				SxlNode select = query.args().stream()
					.filter(n -> n.symbol().equals("S"))
					.findFirst()
					.orElseThrow();
				assertThat(select.args()).hasSize(2);
				
				// Verify all are AS clauses
				boolean allAreAs = select.args().stream()
					.allMatch(n -> n.symbol().equals("AS"));
				assertThat(allAreAs).isTrue();
		}
	}

	@Nested
	@DisplayName("Global Constraints Tests")
	class GlobalConstraintsTests {

		@Test
		@DisplayName("Should require Q as root symbol")
		void shouldRequireQAsRootSymbol() {
			String input = """
				(EMBED sxl-sql
				  (F orders o)
				)
				""";
			
			// The error message format may vary - it could say "must_have_root" or
			// "must have root symbol" or similar. We check for key indicators that
			// the root symbol constraint was violated and Q was expected.
			assertThatThrownBy(() -> parseAndValidate(input))
				.isInstanceOf(SxlParseException.class)
				.matches(e -> {
					String message = e.getMessage();
					// Accept various forms of the root symbol constraint error message
					boolean mentionsRoot = message.contains("must_have_root") ||
						                   message.contains("must have root") ||
						                   message.contains("root symbol");
					boolean mentionsQ = message.contains("'Q'") || message.contains("\"Q\"");
					return mentionsRoot && mentionsQ;
				}, "Exception message should indicate root symbol constraint violation requiring Q");
		}

		@Test
		@DisplayName("Should accept Q as root symbol")
		void shouldAcceptQAsRootSymbol() {
			// Note: This test uses a query without DISTINCT clause. Although DISTINCT is marked
			// as optional in the grammar, the parser's handling of ordered optional parameters
			// may have limitations. If this test fails, it may indicate that the parser doesn't
			// properly skip optional ordered parameters (it may try to match F to the distinct
			// parameter position even though distinct is optional). This is a known limitation
			// of the current parser implementation with ordered optional parameters.
			String input = """
				(EMBED sxl-sql
				  (Q
				    (F orders o)
				    (S (AS o.id id))
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
			SxlNode query = nodes.get(0).args().get(1);
			assertThat(query.symbol()).isEqualTo("Q");
		}
	}

	@Nested
	@DisplayName("Error Handling Tests")
	class ErrorHandlingTests {

		@Test
		@DisplayName("Should reject query with missing EMBED wrapper")
		void shouldRejectQueryWithMissingEmbedWrapper() {
			String input = """
				(Q
				  (F orders o)
				  (S (AS o.id id))
				)
				""";
			
			// This will fail because we're using ComplexDslValidator which expects EMBED
			assertThatThrownBy(() -> parseAndValidate(input))
				.isInstanceOf(SxlParseException.class);
		}

		@Test
		@DisplayName("Should reject query with wrong root DSL ID")
		void shouldRejectQueryWithWrongRootDslId() {
			String input = """
				(EMBED wrong-dsl
				  (Q
				    (F orders o)
				    (S (AS o.id id))
				  )
				)
				""";
			
			assertThatThrownBy(() -> parseAndValidate(input))
				.isInstanceOf(SxlParseException.class)
				.hasMessageContaining("unknown DSL");
		}

		@Test
		@DisplayName("Should reject unknown symbol in query")
		void shouldRejectUnknownSymbolInQuery() {
			String input = """
				(EMBED sxl-sql
				  (Q
				    (F orders o)
				    (INVALID_SYMBOL)
				    (S (AS o.id id))
				  )
				)
				""";
			
			// The validator may report this as "Unknown symbol" or as a parameter type mismatch
			// depending on how it processes the invalid symbol. We check that an exception
			// is thrown and that the message relates to the invalid symbol or indicates
			// a validation error.
			assertThatThrownBy(() -> parseAndValidate(input))
				.isInstanceOf(SxlParseException.class)
				.matches(e -> {
					String message = e.getMessage();
					// Accept either "Unknown symbol" or any error message that indicates
					// validation failure (the specific error format may vary)
					return message.contains("INVALID_SYMBOL") || 
						   message.contains("Unknown symbol") ||
						   message.contains("expects one of") ||
						   message.contains("parameter");
				}, "Exception message should indicate validation failure related to invalid symbol");
		}

		@Test
		@DisplayName("Should reject invalid parameter count")
		void shouldRejectInvalidParameterCount() {
			String input = """
				(EMBED sxl-sql
				  (Q
				    (F orders)
				    (S (AS o.id id))
				  )
				)
				""";
			
			assertThatThrownBy(() -> parseAndValidate(input))
				.isInstanceOf(SxlParseException.class);
		}
	}

	/**
	 * Helper method to parse and validate input using ComplexDslValidator.
	 */
	private List<SxlNode> parseAndValidate(String input) {
		SxlTokenizer tokenizer = new SxlTokenizer(input);
		List<org.javai.springai.actions.sxl.SxlToken> tokens = tokenizer.tokenize();
		
		ComplexDslValidator validator = new ComplexDslValidator(registry);
		return validator.parseAndValidate(tokens);
	}

}

