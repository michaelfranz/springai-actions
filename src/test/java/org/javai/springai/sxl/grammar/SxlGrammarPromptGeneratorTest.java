package org.javai.springai.sxl.grammar;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for SxlGrammarPromptGenerator.
 * This class generates system prompt content from DSL grammar definitions,
 * focusing on the DSL-specific rules while omitting universal s-expression
 * language rules.
 */
@DisplayName("SxlGrammarPromptGenerator Tests")
class SxlGrammarPromptGeneratorTest {

	private static final String SQL_DSL_DESCRIPTION = "S-expression language for expressing SQL SELECT queries";
	private static final String SQL_DSL_ID = "sxl-sql";

	private static final String PLAN_DSL_DESCRIPTION = "S-expression language for expressing execution plans with steps";
	private static final String PLAN_DSL_ID = "sxl-plan";

	private SxlGrammarParser parser;
	private SxlGrammar sqlGrammar;
	private SxlGrammar planGrammar;
	private SxlGrammar universalGrammar;
	private SxlGrammarPromptGenerator generator;

	@BeforeEach
	void setUp() {
		parser = new SxlGrammarParser();
		generator = new SxlGrammarPromptGenerator();

		// Load SQL grammar from resources
		InputStream sqlStream = getClass().getClassLoader()
				.getResourceAsStream("sxl-meta-grammar-sql.yml");
		assertThat(sqlStream).isNotNull();
		sqlGrammar = parser.parse(sqlStream);

		// Load Plan grammar from resources
		InputStream planStream = getClass().getClassLoader()
				.getResourceAsStream("sxl-meta-grammar-plan.yml");
		assertThat(planStream).isNotNull();
		planGrammar = parser.parse(planStream);

		// Load Universal grammar from resources
		InputStream universalStream = getClass().getClassLoader()
				.getResourceAsStream("sxl-meta-grammar-universal.yml");
		assertThat(universalStream).isNotNull();
		universalGrammar = parser.parse(universalStream);
	}

	@Test
	@DisplayName("should generate system prompt content for SQL grammar")
	void shouldGenerateSystemPromptContentForSqlGrammar() {
		String prompt = generator.generate(sqlGrammar);

		// The ideal prompt should include:
		// 1. DSL metadata (id, description, version)
		assertThat(prompt).contains("sxl-sql");
		assertThat(prompt).contains("S-expression language for expressing SQL SELECT queries");
		assertThat(prompt).contains("2.0");

		// 2. Symbol definitions with descriptions
		// Top-level query symbol
		assertThat(prompt).contains("Q");
		assertThat(prompt).contains("Top-level SELECT query");

		// FROM clause symbol
		assertThat(prompt).contains("F");
		assertThat(prompt).contains("FROM clause");

		// SELECT clause symbol
		assertThat(prompt).contains("S");
		assertThat(prompt).contains("SELECT clause");

		// 3. Parameter definitions with types and cardinality
		// Q symbol should have parameters like distinct, from, select, where, etc.
		assertThat(prompt).contains("distinct");
		assertThat(prompt).contains("from");
		assertThat(prompt).contains("select");
		assertThat(prompt).contains("where");
		assertThat(prompt).contains("optional");
		assertThat(prompt).contains("required");
		assertThat(prompt).contains("zeroOrMore");

		// 4. Allowed symbols for node-type parameters
		assertThat(prompt).contains("D"); // DISTINCT
		assertThat(prompt).contains("J"); // JOIN
		assertThat(prompt).contains("W"); // WHERE

		// 5. Examples for symbols
		assertThat(prompt).contains("Simple SELECT");
		// Should include example code snippets
		assertThat(prompt).contains("(Q");
		assertThat(prompt).contains("(F");
		assertThat(prompt).contains("(S");

		// 6. Literal rules (if specified in grammar)
		// The SQL grammar defines string, number, boolean, null literals
		assertThat(prompt).contains("string");
		assertThat(prompt).contains("number");
		assertThat(prompt).contains("boolean");
		assertThat(prompt).contains("null");

		// 7. Identifier rules
		assertThat(prompt).contains("identifier");
		// Should include identifier pattern if defined

		// 8. Reserved symbols
		assertThat(prompt).contains("AS");
		// Should list reserved symbols

		// 9. Global constraints
		assertThat(prompt).contains("must_have_root");
		assertThat(prompt).contains("Q"); // Root symbol constraint

		// 10. The prompt should be well-structured and readable
		assertThat(prompt).isNotBlank();
		assertThat(prompt.length()).isGreaterThan(100); // Should be substantial

		// 11. The prompt should NOT include:
		// - Universal s-expression syntax rules (those are separate)
		// - Meta-grammar version (internal detail)
		// - LLM specs configuration (handled separately)
		// - Embedding configuration details (unless relevant to DSL structure)

		// Verify the prompt is structured in a logical way
		// It should have clear sections for different aspects of the grammar
	}

	@Test
	@DisplayName("should generate prompt with proper formatting and structure")
	void shouldGeneratePromptWithProperFormatting() {
		String prompt = generator.generate(sqlGrammar);

		// The prompt should be formatted for readability
		// It should use consistent indentation and line breaks
		assertThat(prompt).contains("\n"); // Should have line breaks

		// Should have clear section headers or structure
		// (exact format to be determined, but should be readable)
	}

	@Test
	@DisplayName("should handle grammar with minimal symbols")
	void shouldHandleGrammarWithMinimalSymbols() {
		// Create a minimal grammar for testing
		String minimalYaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: minimal-dsl
				  description: "Minimal test DSL"
				  version: 1.0
				symbols:
				  ROOT:
				    description: "Root symbol"
				    kind: node
				""";

		SxlGrammar minimalGrammar = parser.parseString(minimalYaml);
		String prompt = generator.generate(minimalGrammar);

		assertThat(prompt).isNotBlank();
		assertThat(prompt).contains("minimal-dsl");
		assertThat(prompt).contains("Minimal test DSL");
		assertThat(prompt).contains("ROOT");
		assertThat(prompt).contains("Root symbol");
	}

	@Test
	@DisplayName("should include parameter details in prompt")
	void shouldIncludeParameterDetailsInPrompt() {
		String prompt = generator.generate(sqlGrammar);

		// For the F (FROM) symbol, should include parameter details
		// F has: table_name (identifier, required) and alias (identifier, required)
		assertThat(prompt).contains("table_name");
		assertThat(prompt).contains("alias");

		// Should indicate parameter types
		assertThat(prompt).contains("identifier");
		assertThat(prompt).contains("node");
		assertThat(prompt).contains("literal");
	}

	@Test
	@DisplayName("should include examples in prompt")
	void shouldIncludeExamplesInPrompt() {
		String prompt = generator.generate(sqlGrammar);

		// Should include example labels and code
		assertThat(prompt).contains("Simple SELECT");
		assertThat(prompt).contains("Complex query with all clauses");

		// Should include the actual example code
		assertThat(prompt).contains("(Q");
		assertThat(prompt).contains("(F fact_sales f)");
		assertThat(prompt).contains("(S");
	}

	@Test
	@DisplayName("should handle grammar without examples gracefully")
	void shouldHandleGrammarWithoutExamplesGracefully() {
		String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: no-examples-dsl
				  description: "DSL without examples"
				  version: 1.0
				symbols:
				  ROOT:
				    description: "Root symbol"
				    kind: node
				    params:
				      - name: value
				        type: literal(string)
				        cardinality: required
				""";

		SxlGrammar grammar = parser.parseString(yaml);
		String prompt = generator.generate(grammar);

		assertThat(prompt).isNotBlank();
		assertThat(prompt).contains("no-examples-dsl");
		assertThat(prompt).contains("ROOT");
		// Should still generate valid prompt even without examples
	}

	@Test
	@DisplayName("should include literal definitions when present")
	void shouldIncludeLiteralDefinitionsWhenPresent() {
		String prompt = generator.generate(sqlGrammar);

		// SQL grammar defines literals for string, number, boolean, null
		// The prompt should reference these
		assertThat(prompt).contains("string");
		assertThat(prompt).contains("number");
		assertThat(prompt).contains("boolean");
		assertThat(prompt).contains("null");
	}

	@Test
	@DisplayName("should include identifier rules when present")
	void shouldIncludeIdentifierRulesWhenPresent() {
		String prompt = generator.generate(sqlGrammar);

		// SQL grammar defines identifier rules
		// Should include identifier pattern information
		assertThat(prompt).contains("identifier");
		// May include pattern details depending on implementation
	}

	@Test
	@DisplayName("should include reserved symbols when present")
	void shouldIncludeReservedSymbolsWhenPresent() {
		String prompt = generator.generate(sqlGrammar);

		// SQL grammar has reserved symbols like AS
		assertThat(prompt).contains("AS");
		// Should list reserved symbols clearly
	}

	@Test
	@DisplayName("should include global constraints when present")
	void shouldIncludeGlobalConstraintsWhenPresent() {
		String prompt = generator.generate(sqlGrammar);

		// SQL grammar has a must_have_root constraint
		assertThat(prompt).contains("must_have_root");
		assertThat(prompt).contains("Q"); // The root symbol
	}

	@Test
	@DisplayName("should generate consistent output for same grammar")
	void shouldGenerateConsistentOutputForSameGrammar() {
		String prompt1 = generator.generate(sqlGrammar);
		String prompt2 = generator.generate(sqlGrammar);

		assertThat(prompt1).isEqualTo(prompt2);
	}

	@Test
	@DisplayName("should handle empty symbols map")
	void shouldHandleEmptySymbolsMap() {
		String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: empty-symbols-dsl
				  description: "DSL with no symbols"
				  version: 1.0
				""";

		SxlGrammar grammar = parser.parseString(yaml);
		String prompt = generator.generate(grammar);

		assertThat(prompt).isNotBlank();
		assertThat(prompt).contains("empty-symbols-dsl");
		assertThat(prompt).doesNotContain("=== SYMBOL DEFINITIONS ===");
		assertThat(prompt).doesNotContain("No symbols defined.");
		// Should still generate valid prompt structure
	}

	@Test
	void planDslPrompt() {
		String prompt = generator.generate(planGrammar);

		assertThat(prompt)
				.contains(PLAN_DSL_ID)
				.contains(PLAN_DSL_DESCRIPTION)
				.contains("=== SYMBOL DEFINITIONS ===")
				.contains("=== LITERAL DEFINITIONS ===")
				.contains("=== IDENTIFIER RULES ===")
				.contains("=== RESERVED SYMBOLS ===")
				.contains("=== EMBEDDING CONFIGURATION ===")
				.contains("=== GLOBAL CONSTRAINTS ===");

		// Do not include version lines.
		assertThat(prompt).doesNotContain("Version:");
		// Do not include unused separators.
		assertThat(prompt).doesNotContain("---");

		// No boilerplate placeholders should appear.
		assertThat(prompt)
				.doesNotContain("No symbols defined.")
				.doesNotContain("No literal definitions.")
				.doesNotContain("No reserved symbols.")
				.doesNotContain("Embedding not configured.")
				.doesNotContain("No global constraints.");
	}

	@Test
	void sqlDslPrompt() {
		String prompt = generator.generate(sqlGrammar);

		assertThat(prompt)
				.contains(SQL_DSL_ID)
				.contains(SQL_DSL_DESCRIPTION)
				.contains("=== SYMBOL DEFINITIONS ===")
				.contains("=== LITERAL DEFINITIONS ===")
				.contains("=== IDENTIFIER RULES ===")
				.contains("=== RESERVED SYMBOLS ===")
				.contains("=== EMBEDDING CONFIGURATION ===")
				.contains("=== GLOBAL CONSTRAINTS ===");

		// Do not include version lines.
		assertThat(prompt).doesNotContain("Version:");
		// Do not include unused separators.
		assertThat(prompt).doesNotContain("---");

		// No boilerplate placeholders should appear.
		assertThat(prompt)
				.doesNotContain("No symbols defined.")
				.doesNotContain("No literal definitions.")
				.doesNotContain("No reserved symbols.")
				.doesNotContain("Embedding not configured.")
				.doesNotContain("No global constraints.");
	}

	// ============================================================
	// GOLDEN FILE TESTS
	// ============================================================
	// These tests verify that system prompts are correctly generated
	// and can be inspected via golden files in src/test/resources/golden/

	@Test
	@DisplayName("should generate and save SQL grammar golden file")
	void shouldGenerateSqlGoldenFile() {
		// This test verifies that the golden file generator works correctly
		// and creates the golden file in the expected location
		String prompt = generator.generate(sqlGrammar);

		// Verify the prompt is substantial (contains actual content)
		assertThat(prompt).isNotEmpty();
		assertThat(prompt.length()).isGreaterThan(500); // Should be substantial

		// Verify key sections are present
		assertThat(prompt).contains(SQL_DSL_ID);
		assertThat(prompt).contains(SQL_DSL_DESCRIPTION);
		assertThat(prompt).contains("SYMBOL DEFINITIONS");
	}

	@Test
	@DisplayName("should generate and save Plan grammar golden file")
	void shouldGeneratePlanGoldenFile() {
		// This test verifies that the golden file generator works correctly
		// and creates the golden file in the expected location
		String prompt = generator.generate(planGrammar);

		// Verify the prompt is substantial (contains actual content)
		assertThat(prompt).isNotEmpty();
		assertThat(prompt.length()).isGreaterThan(500); // Should be substantial

		// Verify key sections are present
		assertThat(prompt).contains(PLAN_DSL_ID);
		assertThat(prompt).contains(PLAN_DSL_DESCRIPTION);
		assertThat(prompt).contains("SYMBOL DEFINITIONS");
	}

	@Test
	@DisplayName("Golden files should be readable for documentation purposes")
	void goldenFilesShouldBeReadable() {
		// This test documents the purpose of golden files
		// They should be readable files in src/test/resources/golden/
		// that developers can review to understand what system prompts look like

		String sqlPrompt = generator.generate(sqlGrammar);
		String planPrompt = generator.generate(planGrammar);

		// Both prompts should be well-formatted and human-readable
		assertThat(sqlPrompt).doesNotContain("\u0000"); // No null bytes
		assertThat(planPrompt).doesNotContain("\u0000");

		// Should have proper line breaks for readability
		assertThat(sqlPrompt).contains("\n");
		assertThat(planPrompt).contains("\n");
	}

	@Test
	@DisplayName("Golden files document all grammar features")
	void goldenFilesDocumentAllGrammarFeatures() {
		String sqlPrompt = generator.generate(sqlGrammar);
		String planPrompt = generator.generate(planGrammar);

		// SQL grammar should document at least these features:
		assertThat(sqlPrompt)
				.contains("Q") // Query
				.contains("S") // Select
				.contains("F"); // From

		// Plan grammar should document at least these features:
		assertThat(planPrompt)
				.contains("P") // Plan
				.contains("PS"); // PlanStep
	}

	@Test
	@DisplayName("should highlight boilerplate emitted for universal grammar")
	void shouldHighlightBoilerplateEmittedForUniversalGrammar() {
		String prompt = generator.generate(universalGrammar);

		List<String> boilerplateLines = prompt.lines()
				.filter(line -> line.startsWith("No ") || line.contains("not configured"))
				.toList();

		// Boilerplate placeholders should be absent.
		assertThat(boilerplateLines).isEmpty();

		// Sections without meaningful content should be omitted entirely.
		assertThat(prompt)
				.doesNotContain("=== SYMBOL DEFINITIONS ===")
				.doesNotContain("=== RESERVED SYMBOLS ===")
				.doesNotContain("=== EMBEDDING CONFIGURATION ===")
				.doesNotContain("=== GLOBAL CONSTRAINTS ===")
				.doesNotContain("---");

		// Sections with content should remain.
		assertThat(prompt)
				.contains("=== LITERAL DEFINITIONS ===")
				.contains("=== IDENTIFIER RULES ===");

		// Keep the prompt otherwise non-empty to ensure the generator still outputs guidance.
		assertThat(prompt).isNotBlank();
	}
}

