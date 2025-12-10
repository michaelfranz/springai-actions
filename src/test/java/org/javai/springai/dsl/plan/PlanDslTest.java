package org.javai.springai.dsl.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.io.InputStream;
import java.util.List;
import org.javai.springai.sxl.ComplexDslValidator;
import org.javai.springai.sxl.DefaultValidatorRegistry;
import org.javai.springai.sxl.SxlNode;
import org.javai.springai.sxl.SxlParseException;
import org.javai.springai.sxl.SxlToken;
import org.javai.springai.sxl.SxlTokenizer;
import org.javai.springai.sxl.grammar.LiteralDefinitions;
import org.javai.springai.sxl.grammar.SxlGrammarParser;
import org.javai.springai.sxl.grammar.SxlGrammar;
import org.javai.springai.sxl.grammar.Cardinality;
import org.javai.springai.sxl.grammar.DslMetadata;
import org.javai.springai.sxl.grammar.IdentifierRule;
import org.javai.springai.sxl.grammar.ParameterDefinition;
import org.javai.springai.sxl.grammar.SymbolDefinition;
import org.javai.springai.sxl.grammar.SymbolKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive tests for the Plan DSL.
 * 
 * This test suite validates the Plan DSL as the first test case for embeddable expressions.
 * Tests progress from simple cases to complex scenarios, ensuring critical branches
 * in processing embedded DSL grammars are thoroughly explored.
 */
@DisplayName("Plan DSL Tests")
class PlanDslTest {

	private SxlGrammar planGrammar;
	private SxlGrammar sqlGrammar;
	private DefaultValidatorRegistry registry;

	@BeforeEach
	void setUp() {
		SxlGrammarParser parser = new SxlGrammarParser();
		
		// Load plan grammar from resources
		planGrammar = loadGrammar("sxl-meta-grammar-plan.yml", parser);
		
		// Create simplified SQL grammar for embedding tests
		// This avoids issues with parameter ordering in the full SQL grammar
		// while still testing the embedding functionality comprehensively
		sqlGrammar = createSimplifiedSqlGrammar();
		
		// Create registry with both grammars
		registry = new DefaultValidatorRegistry();
		registry.addGrammar("sxl-plan", planGrammar);
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

	@Nested
	@DisplayName("Basic Plan Structure Tests")
	class BasicPlanStructureTests {

		@Test
		@DisplayName("Should parse minimal plan with one step")
		void shouldParseMinimalPlanWithOneStep() {
			String input = """
				(EMBED sxl-plan
				  (P
				    (PS executeQuery (EMBED sxl-sql (Q (F orders o) (S (AS o.id id)))))
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
			
			assertThat(nodes).hasSize(1);
			SxlNode embed = nodes.get(0);
			assertThat(embed.symbol()).isEqualTo("EMBED");
			SxlNode plan = embed.args().get(1);
			assertThat(plan.symbol()).isEqualTo("P");
			assertThat(plan.args()).hasSize(1); // One PS step
		}

		@Test
		@DisplayName("Should parse plan with optional description")
		void shouldParsePlanWithOptionalDescription() {
			String input = """
				(EMBED sxl-plan
				  (P "Find all completed orders"
				    (PS queryOrders (EMBED sxl-sql (Q (F orders o) (S (AS o.status status)))))
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
			
			assertThat(nodes).hasSize(1);
			SxlNode plan = nodes.get(0).args().get(1);
			assertThat(plan.symbol()).isEqualTo("P");
			assertThat(plan.args()).hasSize(2); // Description + one PS step
		}

		@Test
		@DisplayName("Should parse plan with multiple steps")
		void shouldParsePlanWithMultipleSteps() {
			String input = """
				(EMBED sxl-plan
				  (P "Multi-step execution plan"
				    (PS getOrders (EMBED sxl-sql (Q (F orders o) (S (AS o.id id)))))
				    (PS getCustomers (EMBED sxl-sql (Q (F customers c) (S (AS c.name name)))))
				    (PS joinOrderCustomerData (EMBED sxl-sql (Q (F orders o) (J customers c (EQ o.customer_id c.id)) (S (AS o.id order_id) (AS c.name customer_name)))))
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
			
			SxlNode plan = nodes.get(0).args().get(1);
			assertThat(plan.symbol()).isEqualTo("P");
			assertThat(plan.args()).hasSize(4); // Description + 3 PS steps
		}

		@Test
		@DisplayName("Should parse plan without description but with steps")
		void shouldParsePlanWithoutDescriptionButWithSteps() {
			String input = """
				(EMBED sxl-plan
				  (P
				    (PS firstStep (EMBED sxl-sql (Q (F table1 t1) (S (AS t1.id id)))))
				    (PS secondStep (EMBED sxl-sql (Q (F table2 t2) (S (AS t2.value value)))))
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
			
			SxlNode plan = nodes.get(0).args().get(1);
			assertThat(plan.symbol()).isEqualTo("P");
			assertThat(plan.args()).hasSize(2); // Two PS steps, no description
		}
	}

	@Nested
	@DisplayName("Plan Step (PS) Tests")
	class PlanStepTests {

		@Test
		@DisplayName("Should require action-name parameter")
		void shouldRequireActionNameParameter() {
			String input = """
				(EMBED sxl-plan
				  (P
				    (PS (EMBED sxl-sql (Q (F orders o) (S (AS o.id id)))))
				  )
				)
				""";
			
			assertThatThrownBy(() -> parseAndValidate(input))
				.isInstanceOf(SxlParseException.class)
				.hasMessageContaining("parameter 'action-name' expects an identifier")
				.hasMessageContaining("action-name");
		}

		@Test
		@DisplayName("Should require dsl-instance parameter")
		void shouldRequireDslInstanceParameter() {
			String input = """
				(EMBED sxl-plan
				  (P
				    (PS someAction)
				  )
				)
				""";
			
			assertThatThrownBy(() -> parseAndValidate(input))
				.isInstanceOf(SxlParseException.class)
				.hasMessageContaining("requires parameter")
				.hasMessageContaining("dsl-instance");
		}

		@Test
		@DisplayName("Should accept action-name as identifier")
		void shouldAcceptActionNameAsIdentifier() {
			String input = """
				(EMBED sxl-plan
				  (P
				    (PS retrieveCustomerData (EMBED sxl-sql (Q (F customers c) (S (AS c.id id)))))
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
			
			SxlNode plan = nodes.get(0).args().get(1);
			SxlNode step = plan.args().get(0);
			assertThat(step.symbol()).isEqualTo("PS");
			assertThat(step.args()).hasSize(2); // action-name + dsl-instance
		}

		@Test
		@DisplayName("Should reject invalid identifier action-name")
		void shouldRejectInvalidIdentifierActionName() {
			// Test with identifier that contains invalid characters (hyphen)
			String input = """
				(EMBED sxl-plan
				  (P
				    (PS invalid-action-name (EMBED sxl-sql (Q (F orders o) (S (AS o.id id)))))
				  )
				)
				""";
			
			assertThatThrownBy(() -> parseAndValidate(input))
				.isInstanceOf(SxlParseException.class);
		}
	}

	@Nested
	@DisplayName("Embedding Tests")
	class EmbeddingTests {

		@Test
		@DisplayName("Should validate embedded SQL DSL in plan step")
		void shouldValidateEmbeddedSqlDslInPlanStep() {
			String input = """
				(EMBED sxl-plan
				  (P
				    (PS queryOrders (EMBED sxl-sql (Q (F orders o) (S (AS o.id id) (AS o.amount total)))))
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
			
			SxlNode plan = nodes.get(0).args().get(1);
			SxlNode step = plan.args().get(0);
			SxlNode embed = step.args().get(1);
			assertThat(embed.symbol()).isEqualTo("EMBED");
			assertThat(embed.args().get(0).symbol()).isEqualTo("sxl-sql");
		}

		@Test
		@DisplayName("Should reject embedded DSL with unknown DSL ID")
		void shouldRejectEmbeddedDslWithUnknownDslId() {
			String input = """
				(EMBED sxl-plan
				  (P
				    (PS invalidStep (EMBED unknown-dsl (SOME_SYMBOL)))
				  )
				)
				""";
			
			assertThatThrownBy(() -> parseAndValidate(input))
				.isInstanceOf(SxlParseException.class)
				.hasMessageContaining("unknown DSL")
				.hasMessageContaining("unknown-dsl");
		}

		@Test
		@DisplayName("Should validate multiple embedded SQL queries in different steps")
		void shouldValidateMultipleEmbeddedSqlQueriesInDifferentSteps() {
			String input = """
				(EMBED sxl-plan
				  (P "Complex query plan"
				    (PS getOrders (EMBED sxl-sql (Q (F orders o) (S (AS o.id id)))))
				    (PS getCustomers (EMBED sxl-sql (Q (F customers c) (S (AS c.name name) (AS c.email email)))))
				    (PS getOrderDetails (EMBED sxl-sql (Q (F orders o) (S (AS o.id order_id) (AS o.amount amount)))))
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
			
			SxlNode plan = nodes.get(0).args().get(1);
			assertThat(plan.args()).hasSize(4); // Description + 3 steps
		}

		@Test
		@DisplayName("Should reject invalid embedded SQL syntax")
		void shouldRejectInvalidEmbeddedSqlSyntax() {
			String input = """
				(EMBED sxl-plan
				  (P
				    (PS invalidSql (EMBED sxl-sql (INVALID_SYMBOL)))
				  )
				)
				""";
			
			assertThatThrownBy(() -> parseAndValidate(input))
				.isInstanceOf(SxlParseException.class)
				.hasMessageContaining("Unknown symbol")
				.hasMessageContaining("INVALID_SYMBOL");
		}

		@Test
		@DisplayName("Should validate complex SQL query with multiple select items")
		void shouldValidateComplexSqlQueryWithMultipleSelectItems() {
			String input = """
				(EMBED sxl-plan
				  (P "Complex data retrieval"
				    (PS getOrderDetails (EMBED sxl-sql 
					  (Q 
					    (F orders o)
					    (S 
					      (AS o.id order_id)
					      (AS o.amount order_amount)
					      (AS o.status status)
					      (AS o.created_at created_at)
					    )
					  )
					))
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
			
			assertThat(nodes).hasSize(1);
			// Validation succeeds if no exception is thrown
		}
	}

	@Nested
	@DisplayName("Global Constraints Tests")
	class GlobalConstraintsTests {

		@Test
		@DisplayName("Should require P as root symbol")
		void shouldRequirePAsRootSymbol() {
			String input = """
				(EMBED sxl-plan
				  (PS stepWithoutPlan (EMBED sxl-sql (Q (F orders o) (S (AS o.id id)))))
				)
				""";
			
			assertThatThrownBy(() -> parseAndValidate(input))
				.isInstanceOf(SxlParseException.class)
				.hasMessageContaining("must have root symbol")
				.hasMessageContaining("P");
		}

		@Test
		@DisplayName("Should accept P as root symbol")
		void shouldAcceptPAsRootSymbol() {
			String input = """
				(EMBED sxl-plan
				  (P
				    (PS validStep (EMBED sxl-sql (Q (F orders o) (S (AS o.id id)))))
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
			
			SxlNode plan = nodes.get(0).args().get(1);
			assertThat(plan.symbol()).isEqualTo("P");
		}
	}

	@Nested
	@DisplayName("Edge Cases and Complex Scenarios")
	class EdgeCasesAndComplexScenariosTests {

		@Test
		@DisplayName("Should handle plan with single step and description")
		void shouldHandlePlanWithSingleStepAndDescription() {
			String input = """
				(EMBED sxl-plan
				  (P "Simple plan with one step"
				    (PS executeQuery (EMBED sxl-sql (Q (F orders o) (S (AS o.id id)))))
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
			
			SxlNode plan = nodes.get(0).args().get(1);
			assertThat(plan.symbol()).isEqualTo("P");
			assertThat(plan.args()).hasSize(2); // Description + step
		}

		@Test
		@DisplayName("Should handle plan with many steps")
		void shouldHandlePlanWithManySteps() {
			String input = """
				(EMBED sxl-plan
				  (P "Multi-step workflow"
				    (PS step1 (EMBED sxl-sql (Q (F table1 t) (S (AS t.col1 col1)))))
				    (PS step2 (EMBED sxl-sql (Q (F table2 t) (S (AS t.col2 col2)))))
				    (PS step3 (EMBED sxl-sql (Q (F table3 t) (S (AS t.col3 col3)))))
				    (PS step4 (EMBED sxl-sql (Q (F table4 t) (S (AS t.col4 col4)))))
				    (PS step5 (EMBED sxl-sql (Q (F table5 t) (S (AS t.col5 col5)))))
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
			
			SxlNode plan = nodes.get(0).args().get(1);
			assertThat(plan.args()).hasSize(6); // Description + 5 steps
		}

		@Test
		@DisplayName("Should handle empty plan (no steps)")
		void shouldHandleEmptyPlanNoSteps() {
			String input = """
				(EMBED sxl-plan
				  (P "Empty plan")
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
			
			SxlNode plan = nodes.get(0).args().get(1);
			assertThat(plan.symbol()).isEqualTo("P");
			assertThat(plan.args()).hasSize(1); // Only description, no steps
		}

		@Test
		@DisplayName("Should handle plan with minimal description string")
		void shouldHandlePlanWithMinimalDescriptionString() {
			String input = """
				(EMBED sxl-plan
				  (P ""
				    (PS step (EMBED sxl-sql (Q (F orders o) (S (AS o.id id)))))
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
			
			SxlNode plan = nodes.get(0).args().get(1);
			assertThat(plan.symbol()).isEqualTo("P");
		}

		@Test
		@DisplayName("Should handle plan with very long description")
		void shouldHandlePlanWithVeryLongDescription() {
			String longDescription = "This is a very long description that explains the purpose " +
				"of this execution plan in great detail. It may contain multiple sentences " +
				"and span across several lines of text to provide comprehensive context " +
				"about what this plan intends to accomplish.";
			
			String input = String.format("""
				(EMBED sxl-plan
				  (P "%s"
				    (PS step (EMBED sxl-sql (Q (F orders o) (S (AS o.id id)))))
				  )
				)
				""", longDescription);
			
			List<SxlNode> nodes = parseAndValidate(input);
			
			SxlNode plan = nodes.get(0).args().get(1);
			assertThat(plan.symbol()).isEqualTo("P");
		}

		@Test
		@DisplayName("Should handle action names with special characters")
		void shouldHandleActionNamesWithSpecialCharacters() {
			String input = """
				(EMBED sxl-plan
				  (P
				    (PS queryOrders (EMBED sxl-sql (Q (F orders o) (S (AS o.id id)))))
				    (PS processAndTransformData (EMBED sxl-sql (Q (F orders o) (S (AS o.id id)))))
				    (PS validateResults (EMBED sxl-sql (Q (F orders o) (S (AS o.id id)))))
				  )
				)
				""";
			
			List<SxlNode> nodes = parseAndValidate(input);
			
			SxlNode plan = nodes.get(0).args().get(1);
			assertThat(plan.args()).hasSize(3); // Three steps
		}
	}

	@Nested
	@DisplayName("Error Handling Tests")
	class ErrorHandlingTests {

		@Test
		@DisplayName("Should reject plan with missing EMBED wrapper")
		void shouldRejectPlanWithMissingEmbedWrapper() {
			String input = """
				(P
				  (PS step (EMBED sxl-sql (Q (F orders o) (S (AS o.id id)))))
				)
				""";
			
			// This will fail because we're using ComplexDslValidator which expects EMBED
			// But if we used DslParsingStrategy directly, it might work differently
			assertThatThrownBy(() -> parseAndValidate(input))
				.isInstanceOf(SxlParseException.class);
		}

		@Test
		@DisplayName("Should reject plan with wrong root DSL ID")
		void shouldRejectPlanWithWrongRootDslId() {
			String input = """
				(EMBED wrong-dsl
				  (P
				    (PS step (EMBED sxl-sql (Q (F orders o) (S (AS o.id id)))))
				  )
				)
				""";
			
			assertThatThrownBy(() -> parseAndValidate(input))
				.isInstanceOf(SxlParseException.class)
				.hasMessageContaining("unknown DSL");
		}

		@Test
		@DisplayName("Should reject PS node outside of P node")
		void shouldRejectPSNodeOutsideOfPNode() {
			String input = """
				(EMBED sxl-plan
				  (PS orphanStep (EMBED sxl-sql (Q (F orders o) (S (AS o.id id)))))
				)
				""";
			
			assertThatThrownBy(() -> parseAndValidate(input))
				.isInstanceOf(SxlParseException.class)
				.hasMessageContaining("must have root symbol")
				.hasMessageContaining("P");
		}

		@Test
		@DisplayName("Should provide meaningful error for missing dsl-instance")
		void shouldProvideMeaningfulErrorForMissingDslInstance() {
			String input = """
				(EMBED sxl-plan
				  (P
				    (PS incompleteStep)
				  )
				)
				""";
			
			assertThatThrownBy(() -> parseAndValidate(input))
				.isInstanceOf(SxlParseException.class)
				.hasMessageContaining("dsl-instance");
		}
	}

	/**
	 * Helper method to parse and validate input using ComplexDslValidator.
	 */
	private List<SxlNode> parseAndValidate(String input) {
		SxlTokenizer tokenizer = new SxlTokenizer(input);
		List<SxlToken> tokens = tokenizer.tokenize();
		
		ComplexDslValidator validator = new ComplexDslValidator(registry);
		return validator.parseAndValidate(tokens);
	}

	/**
	 * Creates a simplified SQL grammar for testing plan DSL embedding.
	 * This grammar focuses on testing embedding functionality without
	 * the complexity of the full SQL grammar's parameter ordering.
	 */
	private SxlGrammar createSimplifiedSqlGrammar() {
		SymbolDefinition qSymbol =
			new SymbolDefinition(
				"Query",
				SymbolKind.node,
				List.of(
					new ParameterDefinition(
						"from", "FROM clause", "node", List.of("F"), 
						Cardinality.optional, true, null),
					new ParameterDefinition(
						"join", "JOIN clause", "node", List.of("J"), 
						Cardinality.optional, true, null),
					new ParameterDefinition(
						"select", "SELECT clause", "node", List.of("S"), 
						Cardinality.required, true, null)
				),
				List.of(),
				List.of()
			);
		
		SymbolDefinition fSymbol =
			new SymbolDefinition(
				"FROM clause",
				SymbolKind.node,
				List.of(
					new ParameterDefinition(
						"table", "Table name", "identifier", List.of(), 
						Cardinality.required, true, null),
					new ParameterDefinition(
						"alias", "Table alias", "identifier", List.of(), 
						Cardinality.required, true, null)
				),
				List.of(),
				List.of()
			);
		
		SymbolDefinition sSymbol =
			new SymbolDefinition(
				"SELECT clause",
				SymbolKind.node,
				List.of(
					new ParameterDefinition(
						"items", "Select items", "node", List.of("AS"), 
						Cardinality.oneOrMore, true, null)
				),
				List.of(),
				List.of()
			);
		
		SymbolDefinition asSymbol =
			new SymbolDefinition(
				"AS clause",
				SymbolKind.node,
				List.of(
					new ParameterDefinition(
						"expr", "Expression", "any", List.of(), 
						Cardinality.required, true, null),
					new ParameterDefinition(
						"alias", "Alias name", "identifier", List.of(), 
						Cardinality.required, true, null)
				),
				List.of(),
				List.of()
			);
		
		SymbolDefinition jSymbol =
			new SymbolDefinition(
				"JOIN clause",
				SymbolKind.node,
				List.of(
					new ParameterDefinition(
						"table", "Table name", "identifier", List.of(), 
						Cardinality.required, true, null),
					new ParameterDefinition(
						"alias", "Table alias", "identifier", List.of(), 
						Cardinality.required, true, null),
					new ParameterDefinition(
						"condition", "Join condition", "node", List.of("EQ"), 
						Cardinality.required, true, null)
				),
				List.of(),
				List.of()
			);
		
		SymbolDefinition eqSymbol =
			new SymbolDefinition(
				"Equal operator",
				SymbolKind.operator,
				List.of(
					new ParameterDefinition(
						"left", "Left operand", "any", List.of(), 
						Cardinality.required, true, null),
					new ParameterDefinition(
						"right", "Right operand", "any", List.of(), 
						Cardinality.required, true, null)
				),
				List.of(),
				List.of()
			);
		
		return new SxlGrammar(
			"1.2",
			new DslMetadata("sxl-sql", "SQL DSL", "2.0"),
			java.util.Map.of("Q", qSymbol, "F", fSymbol, "J", jSymbol, "S", sSymbol, "AS", asSymbol, "EQ", eqSymbol),
			new LiteralDefinitions(null, null, null, null),
			new IdentifierRule("Identifier", "^[a-z_][a-z0-9_.]*$"),
			List.of(),
			null,
			List.of(),
			null
		);
	}
}

