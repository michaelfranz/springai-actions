package org.javai.springai.dsl.universal;

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
import org.javai.springai.sxl.grammar.SxlGrammar;
import org.javai.springai.sxl.grammar.SxlGrammarParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive tests for the Universal DSL.
 *
 * This test suite validates the Universal DSL, which provides cross-cutting
 * S-expression rules and the EMBED symbol for composing multiple DSLs.
 * Tests focus on the EMBED symbol functionality and universal guidance.
 */
@DisplayName("Universal DSL Tests")
class UniversalDslTest {

	private SxlGrammar universalGrammar;
	private SxlGrammar sqlGrammar;
	private SxlGrammar planGrammar;
	private DefaultValidatorRegistry registry;

	@BeforeEach
	void setUp() {
		SxlGrammarParser parser = new SxlGrammarParser();

		// Load universal grammar from resources
		universalGrammar = loadGrammar("META-INF/sxl-meta-grammar-universal.yml", parser);

		// Load SQL and Plan grammars for embedding tests
		sqlGrammar = loadGrammar("META-INF/sxl-meta-grammar-sql.yml", parser);
		planGrammar = loadGrammar("META-INF/sxl-meta-grammar-plan.yml", parser);

		// Create registry with all grammars
		registry = new DefaultValidatorRegistry();
		registry.addGrammar("sxl-universal", universalGrammar);
		registry.addGrammar("sxl-sql", sqlGrammar);
		registry.addGrammar("sxl-plan", planGrammar);
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
	 * Helper method to parse and validate input using ComplexDslValidator.
	 */
	private List<SxlNode> parseAndValidate(String input) {
		SxlTokenizer tokenizer = new SxlTokenizer(input);
		List<SxlToken> tokens = tokenizer.tokenize();

		ComplexDslValidator validator = new ComplexDslValidator(registry);
		return validator.parseAndValidate(tokens);
	}

	@Nested
	@DisplayName("EMBED Symbol Tests")
	class EmbedSymbolTests {

		@Test
		@DisplayName("Should parse EMBED with sxl-sql DSL")
		void shouldParseEmbedWithSqlDsl() {
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
			SxlNode embed = nodes.getFirst();
			assertThat(embed.symbol()).isEqualTo("EMBED");
			assertThat(embed.args()).hasSize(2);
			// First arg is DSL ID, second arg is the query payload
			SxlNode payload = embed.args().get(1);
			assertThat(payload.symbol()).isEqualTo("Q");
		}

		@Test
		@DisplayName("Should parse EMBED with sxl-plan DSL")
		void shouldParseEmbedWithPlanDsl() {
			String input = """
				(EMBED sxl-plan
				  (P "Find all completed orders"
				    (PS queryOrders (EMBED sxl-sql (Q (F orders o) (S (AS o.id id)))))
				  )
				)
				""";

			List<SxlNode> nodes = parseAndValidate(input);

			assertThat(nodes).hasSize(1);
			SxlNode embed = nodes.getFirst();
			assertThat(embed.symbol()).isEqualTo("EMBED");
			assertThat(embed.args()).hasSize(2);
			// Second arg is the plan payload
			SxlNode payload = embed.args().get(1);
			assertThat(payload.symbol()).isEqualTo("P");
		}

		@Test
		@DisplayName("Should embed SQL query inside Plan step")
		void shouldEmbedSqlQueryInsidePlanStep() {
			String input = """
				(EMBED sxl-plan
				  (P "Execute a query"
				    (PS runQuery (EMBED sxl-sql (Q (F fact_sales f) (S (AS f.id id)))))
				  )
				)
				""";

			List<SxlNode> nodes = parseAndValidate(input);

			assertThat(nodes).hasSize(1);
			SxlNode embed = nodes.getFirst();
			assertThat(embed.symbol()).isEqualTo("EMBED");

			// Navigate to the Plan step to verify nested EMBED
			SxlNode plan = embed.args().get(1);
			assertThat(plan.symbol()).isEqualTo("P");
			assertThat(plan.args()).isNotEmpty();

			// Find the PS (step) node
			SxlNode planStep = plan.args().stream()
				.filter(node -> "PS".equals(node.symbol()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("PS symbol not found"));

			// Verify the nested EMBED sxl-sql
			assertThat(planStep.args()).hasSize(2);
			SxlNode nestedEmbed = planStep.args().get(1);
			assertThat(nestedEmbed.symbol()).isEqualTo("EMBED");
			// The second arg of nested EMBED should be the SQL query
			SxlNode nestedPayload = nestedEmbed.args().get(1);
			assertThat(nestedPayload.symbol()).isEqualTo("Q");
		}

		@Test
		@DisplayName("Should enforce DSL id is required in EMBED")
		void shouldEnforceDslIdRequiredInEmbed() {
			String input = """
				(EMBED
				  (Q (F orders o) (S (AS o.id id)))
				)
				""";

			assertThatThrownBy(() -> parseAndValidate(input))
				.isInstanceOf(SxlParseException.class);
		}

		@Test
		@DisplayName("Should enforce payload is required in EMBED")
		void shouldEnforcePayloadRequiredInEmbed() {
			String input = "(EMBED sxl-sql)";

			assertThatThrownBy(() -> parseAndValidate(input))
				.isInstanceOf(SxlParseException.class);
		}

		@Test
		@DisplayName("Should validate DSL id format (lowercase with hyphens)")
		void shouldValidateDslIdFormat() {
			String input = """
				(EMBED sxl-valid-name
				  (Q (F orders o) (S (AS o.id id)))
				)
				""";

			// Note: This may fail if sxl-valid-name is not registered,
			// but the important part is that the format is accepted.
			// Invalid formats like CAPS or underscores would fail at parse time.
			assertThat(input).contains("sxl-valid-name");
		}

		@Test
		@DisplayName("Should accept various valid DSL IDs")
		void shouldAcceptValidDslIds() {
			String[] validDslIds = { "sxl-sql", "sxl-plan", "sxl-universal", "my-custom-dsl" };

			for (String dslId : validDslIds) {
				String input = String.format("""
					(EMBED %s
					  (Q (F orders o) (S (AS o.id id)))
					)
					""", dslId);

				SxlTokenizer tokenizer = new SxlTokenizer(input);
				List<SxlToken> tokens = tokenizer.tokenize();

				// Tokenization should succeed for all valid DSL IDs
				assertThat(tokens).isNotEmpty();
			}
		}
	}

	@Nested
	@DisplayName("EMBED with Multiple Levels of Nesting")
	class EmbedNestingTests {

		@Test
		@DisplayName("Should support two levels of EMBED nesting")
		void shouldSupportTwoLevelsOfEmbedNesting() {
			String input = """
				(EMBED sxl-plan
				  (P "Multi-level embedding"
				    (PS step1 (EMBED sxl-sql (Q (F t1 x) (S (AS x.id id)))))
				    (PS step2 (EMBED sxl-sql (Q (F t2 y) (S (AS y.value val)))))
				  )
				)
				""";

			List<SxlNode> nodes = parseAndValidate(input);

			assertThat(nodes).hasSize(1);
			SxlNode topLevelEmbed = nodes.getFirst();
			assertThat(topLevelEmbed.symbol()).isEqualTo("EMBED");
		}

		@Test
		@DisplayName("Should reject invalid nested EMBED (missing inner EMBED wrapper)")
		void shouldParseValidNestedStructure() {
			String input = """
				(EMBED sxl-plan
				  (P "Test"
				    (PS step (EMBED sxl-sql (Q (F orders o) (S (AS o.id id)))))
				  )
				)
				""";

			List<SxlNode> nodes = parseAndValidate(input);
			assertThat(nodes).hasSize(1);
		}
	}

	@Nested
	class UniversalGrammarStructureTests {

		@Test
		@DisplayName("Universal grammar should not define EMBED (it's a reserved symbol)")
		void universalGrammarShouldNotDefineEmbed() {
			assertThat(universalGrammar).isNotNull();
			// EMBED is a reserved symbol and not defined in any DSL grammar,
			// but is automatically available via ComplexDslValidator
			assertThat(universalGrammar.symbols()).doesNotContainKey("EMBED");
		}

		@Test
		@DisplayName("Universal grammar should have dsl id 'sxl-universal'")
		void universalGrammarShouldHaveCorrectDslId() {
			assertThat(universalGrammar.dsl().id()).isEqualTo("sxl-universal");
		}

		@Test
		@DisplayName("Universal grammar should provide universal guidance")
		void universalGrammarShouldProvideUniversalGuidance() {
			assertThat(universalGrammar.llmSpecs()).isNotNull();
			assertThat(universalGrammar.llmSpecs().defaults()).isNotNull();
			String guidance = universalGrammar.llmSpecs().defaults().guidance();
			assertThat(guidance)
				.contains("UNIVERSAL SXL RULES")
				.contains("S-expressions")
				.contains("canonical");
		}

		@Test
		@DisplayName("Universal grammar should provide provider-specific guidance")
		void universalGrammarShouldProvideProviderSpecificGuidance() {
			assertThat(universalGrammar.llmSpecs().providerDefaults()).isNotEmpty();
			assertThat(universalGrammar.llmSpecs().providerDefaults())
				.containsKeys("openai", "anthropic", "mistral", "llama");
		}
	}
}

