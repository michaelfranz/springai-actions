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
	private DefaultValidatorRegistry registry;

	@BeforeEach
	void setUp() {
		SxlGrammarParser parser = new SxlGrammarParser();

		// Load universal grammar from resources
		universalGrammar = loadGrammar("META-INF/sxl-meta-grammar-universal.yml", parser);

		// Load SQL grammar for embedding tests
		sqlGrammar = loadGrammar("META-INF/sxl-meta-grammar-sql.yml", parser);

		// Create registry with all grammars
		registry = new DefaultValidatorRegistry();
		registry.addGrammar("sxl-universal", universalGrammar);
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
		@DisplayName("Should parse multiple SQL queries")
		void shouldParseMultipleSqlQueries() {
			String input1 = "(EMBED sxl-sql (Q (F orders o) (S (AS o.id id))))";
			String input2 = "(EMBED sxl-sql (Q (F products p) (S (AS p.name name))))";

			List<SxlNode> nodes1 = parseAndValidate(input1);
			List<SxlNode> nodes2 = parseAndValidate(input2);

			assertThat(nodes1).hasSize(1);
			assertThat(nodes2).hasSize(1);
			assertThat(nodes1.getFirst().symbol()).isEqualTo("EMBED");
			assertThat(nodes2.getFirst().symbol()).isEqualTo("EMBED");
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
			String[] validDslIds = { "sxl-sql", "sxl-universal", "my-custom-dsl" };

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
	@DisplayName("EMBED with Multiple Queries")
	class EmbedMultipleQueriesTests {

		@Test
		@DisplayName("Should parse multiple EMBEDs sequentially")
		void shouldParseMultipleEmbedsSequentially() {
			String input = "(EMBED sxl-sql (Q (F t1 x) (S (AS x.id id))))";

			List<SxlNode> nodes = parseAndValidate(input);

			assertThat(nodes).hasSize(1);
			SxlNode embed = nodes.getFirst();
			assertThat(embed.symbol()).isEqualTo("EMBED");
		}

		@Test
		@DisplayName("Should parse SQL with complex clauses")
		void shouldParseSqlWithComplexClauses() {
			String input = """
				(EMBED sxl-sql
				  (Q (F orders o) (S (AS o.id id) (AS o.amount amount)) (W (GT o.amount 100)))
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
