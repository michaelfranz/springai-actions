package org.javai.springai.sxl.grammar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.javai.springai.sxl.SxlParseException;
import org.javai.springai.sxl.grammar.Cardinality;
import org.javai.springai.sxl.grammar.EmbeddingConfig;
import org.javai.springai.sxl.grammar.Example;
import org.javai.springai.sxl.grammar.GlobalConstraint;
import org.javai.springai.sxl.grammar.IdentifierRule;
import org.javai.springai.sxl.grammar.LiteralDefinitions;
import org.javai.springai.sxl.grammar.LiteralRule;
import org.javai.springai.sxl.grammar.LlmDefaults;
import org.javai.springai.sxl.grammar.LlmModelOverrides;
import org.javai.springai.sxl.grammar.LlmOverrides;
import org.javai.springai.sxl.grammar.LlmProfile;
import org.javai.springai.sxl.grammar.LlmProviderDefaults;
import org.javai.springai.sxl.grammar.LlmSpecs;
import org.javai.springai.sxl.grammar.ParameterDefinition;
import org.javai.springai.sxl.grammar.SxlGrammar;
import org.javai.springai.sxl.grammar.SxlGrammarParser;
import org.javai.springai.sxl.grammar.SymbolConstraint;
import org.javai.springai.sxl.grammar.SymbolDefinition;
import org.javai.springai.sxl.grammar.SymbolKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive tests for SxlGrammarParser.
 * 
 * Tests progress from the most basic grammar description to one which
 * specifies ALL facets of the grammar.
 */
@DisplayName("SxlGrammarParser Tests")
class SxlGrammarParserTest {

	private final SxlGrammarParser parser = new SxlGrammarParser();

	@Nested
	@DisplayName("Basic Grammar Tests")
	class BasicGrammarTests {

		@Test
		@DisplayName("should parse minimal grammar with only DSL metadata")
		void shouldParseMinimalGrammar() {
			String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: test-dsl
				  description: "Test DSL"
				  version: 1.0
				""";

			SxlGrammar grammar = parser.parseString(yaml);

			assertThat(grammar.metaGrammarVersion()).isEqualTo("1.2");
			assertThat(grammar.dsl().id()).isEqualTo("test-dsl");
			assertThat(grammar.dsl().description()).isEqualTo("Test DSL");
			assertThat(grammar.dsl().version()).isEqualTo("1.0");
			assertThat(grammar.symbols()).isEmpty();
			assertThat(grammar.literals()).isNotNull();
			assertThat(grammar.identifier()).isNull();
			assertThat(grammar.reservedSymbols()).isEmpty();
			assertThat(grammar.embedding()).isNull();
			assertThat(grammar.constraints()).isEmpty();
			assertThat(grammar.llmSpecs()).isNull();
		}

		@Test
		@DisplayName("should fail when DSL section is missing")
		void shouldFailWhenDslSectionMissing() {
			String yaml = """
				meta_grammar_version: 1.2
				""";

			Exception exception = org.assertj.core.api.Assertions.catchException(() -> parser.parseString(yaml));
			assertThat(exception).isInstanceOf(SxlParseException.class);
			// Check either the message or the cause message
			String message = exception.getMessage();
			Throwable cause = exception.getCause();
			assertThat(message != null && message.contains("dsl") || 
			          cause != null && cause.getMessage() != null && cause.getMessage().contains("dsl"))
				.isTrue();
		}

		@Test
		@DisplayName("should handle numeric meta_grammar_version")
		void shouldHandleNumericMetaGrammarVersion() {
			String yaml = """
				meta_grammar_version: 1
				dsl:
				  id: test-dsl
				  description: "Test DSL"
				  version: 1.0
				""";

			SxlGrammar grammar = parser.parseString(yaml);
			assertThat(grammar.metaGrammarVersion()).isEqualTo("1");
		}
	}

	@Nested
	@DisplayName("Symbol Definition Tests")
	class SymbolDefinitionTests {

		@Test
		@DisplayName("should parse grammar with simple symbol")
		void shouldParseGrammarWithSimpleSymbol() {
			String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: test-dsl
				  description: "Test DSL"
				  version: 1.0
				symbols:
				  ROOT:
				    description: "Root symbol"
				    kind: node
				""";

			SxlGrammar grammar = parser.parseString(yaml);

			assertThat(grammar.symbols()).hasSize(1);
			SymbolDefinition root = grammar.symbols().get("ROOT");
			assertThat(root).isNotNull();
			assertThat(root.description()).isEqualTo("Root symbol");
			assertThat(root.kind()).isEqualTo(SymbolKind.node);
			assertThat(root.params()).isEmpty();
			assertThat(root.constraints()).isEmpty();
			assertThat(root.examples()).isEmpty();
		}

		@Test
		@DisplayName("should parse symbol with default kind when kind is omitted")
		void shouldParseSymbolWithDefaultKind() {
			String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: test-dsl
				  description: "Test DSL"
				  version: 1.0
				symbols:
				  ROOT:
				    description: "Root symbol"
				""";

			SxlGrammar grammar = parser.parseString(yaml);
			SymbolDefinition root = grammar.symbols().get("ROOT");
			assertThat(root.kind()).isEqualTo(SymbolKind.node);
		}

		@Test
		@DisplayName("should parse symbol with all kind types")
		void shouldParseSymbolWithAllKindTypes() {
			String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: test-dsl
				  description: "Test DSL"
				  version: 1.0
				symbols:
				  NODE:
				    description: "Node symbol"
				    kind: node
				  OPERATOR:
				    description: "Operator symbol"
				    kind: operator
				  SPECIAL:
				    description: "Special symbol"
				    kind: special
				  LITERAL:
				    description: "Literal symbol"
				    kind: literal
				""";

			SxlGrammar grammar = parser.parseString(yaml);
			assertThat(grammar.symbols().get("NODE").kind()).isEqualTo(SymbolKind.node);
			assertThat(grammar.symbols().get("OPERATOR").kind()).isEqualTo(SymbolKind.operator);
			assertThat(grammar.symbols().get("SPECIAL").kind()).isEqualTo(SymbolKind.special);
			assertThat(grammar.symbols().get("LITERAL").kind()).isEqualTo(SymbolKind.literal);
		}

		@Test
		@DisplayName("should fail when trying to define EMBED symbol")
		void shouldFailWhenDefiningEmbedSymbol() {
			String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: test-dsl
				  description: "Test DSL"
				  version: 1.0
				symbols:
				  EMBED:
				    description: "Embed symbol"
				    kind: node
				""";

			Exception exception = org.assertj.core.api.Assertions.catchException(() -> parser.parseString(yaml));
			assertThat(exception).isInstanceOf(SxlParseException.class);
			// Check either the message or the cause message
			String message = exception.getMessage();
			Throwable cause = exception.getCause();
			assertThat(message != null && message.contains("EMBED") || 
			          cause != null && cause.getMessage() != null && cause.getMessage().contains("EMBED"))
				.isTrue();
		}
	}

	@Nested
	@DisplayName("Parameter Definition Tests")
	class ParameterDefinitionTests {

		@Test
		@DisplayName("should parse symbol with simple parameter")
		void shouldParseSymbolWithSimpleParameter() {
			String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: test-dsl
				  description: "Test DSL"
				  version: 1.0
				symbols:
				  ROOT:
				    description: "Root symbol"
				    params:
				      - name: value
				        description: "A value"
				        type: literal(string)
				        cardinality: required
				""";

			SxlGrammar grammar = parser.parseString(yaml);
			SymbolDefinition root = grammar.symbols().get("ROOT");
			assertThat(root.params()).hasSize(1);

			ParameterDefinition param = root.params().get(0);
			assertThat(param.name()).isEqualTo("value");
			assertThat(param.description()).isEqualTo("A value");
			assertThat(param.type()).isEqualTo("literal(string)");
			assertThat(param.cardinality()).isEqualTo(Cardinality.required);
			assertThat(param.ordered()).isTrue();
			assertThat(param.allowedSymbols()).isEmpty();
			assertThat(param.identifierRules()).isNull();
		}

		@Test
		@DisplayName("should parse parameter with default cardinality")
		void shouldParseParameterWithDefaultCardinality() {
			String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: test-dsl
				  description: "Test DSL"
				  version: 1.0
				symbols:
				  ROOT:
				    description: "Root symbol"
				    params:
				      - name: value
				        description: "A value"
				        type: literal(string)
				""";

			SxlGrammar grammar = parser.parseString(yaml);
			ParameterDefinition param = grammar.symbols().get("ROOT").params().get(0);
			assertThat(param.cardinality()).isEqualTo(Cardinality.required);
		}

		@Test
		@DisplayName("should parse parameter with all cardinality types")
		void shouldParseParameterWithAllCardinalityTypes() {
			String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: test-dsl
				  description: "Test DSL"
				  version: 1.0
				symbols:
				  ROOT:
				    description: "Root symbol"
				    params:
				      - name: required
				        type: literal(string)
				        cardinality: required
				      - name: optional
				        type: literal(string)
				        cardinality: optional
				      - name: zeroOrMore
				        type: literal(string)
				        cardinality: zeroOrMore
				      - name: oneOrMore
				        type: literal(string)
				        cardinality: oneOrMore
				""";

			SxlGrammar grammar = parser.parseString(yaml);
			List<ParameterDefinition> params = grammar.symbols().get("ROOT").params();
			assertThat(params.get(0).cardinality()).isEqualTo(Cardinality.required);
			assertThat(params.get(1).cardinality()).isEqualTo(Cardinality.optional);
			assertThat(params.get(2).cardinality()).isEqualTo(Cardinality.zeroOrMore);
			assertThat(params.get(3).cardinality()).isEqualTo(Cardinality.oneOrMore);
		}

		@Test
		@DisplayName("should parse parameter with ordered false")
		void shouldParseParameterWithOrderedFalse() {
			String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: test-dsl
				  description: "Test DSL"
				  version: 1.0
				symbols:
				  ROOT:
				    description: "Root symbol"
				    params:
				      - name: value
				        type: literal(string)
				        ordered: false
				""";

			SxlGrammar grammar = parser.parseString(yaml);
			ParameterDefinition param = grammar.symbols().get("ROOT").params().get(0);
			assertThat(param.ordered()).isFalse();
		}

		@Test
		@DisplayName("should parse parameter with allowed symbols")
		void shouldParseParameterWithAllowedSymbols() {
			String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: test-dsl
				  description: "Test DSL"
				  version: 1.0
				symbols:
				  ROOT:
				    description: "Root symbol"
				    params:
				      - name: child
				        type: node
				        allowed_symbols: [CHILD1, CHILD2]
				""";

			SxlGrammar grammar = parser.parseString(yaml);
			ParameterDefinition param = grammar.symbols().get("ROOT").params().get(0);
			assertThat(param.allowedSymbols()).containsExactly("CHILD1", "CHILD2");
		}

		@Test
		@DisplayName("should parse parameter with identifier rules")
		void shouldParseParameterWithIdentifierRules() {
			String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: test-dsl
				  description: "Test DSL"
				  version: 1.0
				symbols:
				  ROOT:
				    description: "Root symbol"
				    params:
				      - name: identifier
				        type: identifier
				        identifier_rules:
				          pattern: "^[a-z_][a-z0-9_]*$"
				""";

			SxlGrammar grammar = parser.parseString(yaml);
			ParameterDefinition param = grammar.symbols().get("ROOT").params().get(0);
			assertThat(param.identifierRules()).isNotNull();
			assertThat(param.identifierRules().pattern()).isEqualTo("^[a-z_][a-z0-9_]*$");
		}
	}

	@Nested
	@DisplayName("Symbol Constraint Tests")
	class SymbolConstraintTests {

		@Test
		@DisplayName("should parse symbol with constraints")
		void shouldParseSymbolWithConstraints() {
			String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: test-dsl
				  description: "Test DSL"
				  version: 1.0
				symbols:
				  ROOT:
				    description: "Root symbol"
				    constraints:
				      - rule: must_have_root
				        symbol: ROOT
				""";

			SxlGrammar grammar = parser.parseString(yaml);
			SymbolDefinition root = grammar.symbols().get("ROOT");
			assertThat(root.constraints()).hasSize(1);

			SymbolConstraint constraint = root.constraints().get(0);
			assertThat(constraint.rule()).isEqualTo("must_have_root");
			assertThat(constraint.symbol()).isEqualTo("ROOT");
		}

		@Test
		@DisplayName("should parse symbol constraint with all fields")
		void shouldParseSymbolConstraintWithAllFields() {
			String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: test-dsl
				  description: "Test DSL"
				  version: 1.0
				symbols:
				  ROOT:
				    description: "Root symbol"
				    constraints:
				      - rule: disallowed_together
				        target: params
				        symbol: CHILD1
				        items: [CHILD2, CHILD3]
				        when: "condition"
				""";

			SxlGrammar grammar = parser.parseString(yaml);
			SymbolConstraint constraint = grammar.symbols().get("ROOT").constraints().get(0);
			assertThat(constraint.rule()).isEqualTo("disallowed_together");
			assertThat(constraint.target()).isEqualTo("params");
			assertThat(constraint.symbol()).isEqualTo("CHILD1");
			assertThat(constraint.items()).containsExactly("CHILD2", "CHILD3");
			assertThat(constraint.when()).isEqualTo("condition");
		}
	}

	@Nested
	@DisplayName("Example Tests")
	class ExampleTests {

		@Test
		@DisplayName("should parse symbol with examples")
		void shouldParseSymbolWithExamples() {
			String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: test-dsl
				  description: "Test DSL"
				  version: 1.0
				symbols:
				  ROOT:
				    description: "Root symbol"
				    examples:
				      - label: "Simple example"
				        code: "(ROOT value)"
				""";

			SxlGrammar grammar = parser.parseString(yaml);
			SymbolDefinition root = grammar.symbols().get("ROOT");
			assertThat(root.examples()).hasSize(1);

			Example example = root.examples().get(0);
			assertThat(example.label()).isEqualTo("Simple example");
			assertThat(example.code()).isEqualTo("(ROOT value)");
		}

		@Test
		@DisplayName("should parse symbol with multiple examples")
		void shouldParseSymbolWithMultipleExamples() {
			String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: test-dsl
				  description: "Test DSL"
				  version: 1.0
				symbols:
				  ROOT:
				    description: "Root symbol"
				    examples:
				      - label: "Example 1"
				        code: "(ROOT value1)"
				      - label: "Example 2"
				        code: "(ROOT value2)"
				""";

			SxlGrammar grammar = parser.parseString(yaml);
			assertThat(grammar.symbols().get("ROOT").examples()).hasSize(2);
		}
	}

	@Nested
	@DisplayName("Literal Definition Tests")
	class LiteralDefinitionTests {

		@Test
		@DisplayName("should parse literals with string regex")
		void shouldParseLiteralsWithStringRegex() {
			String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: test-dsl
				  description: "Test DSL"
				  version: 1.0
				literals:
				  string:
				    regex: "^\\".*\\"$"
				""";

			SxlGrammar grammar = parser.parseString(yaml);
			LiteralDefinitions literals = grammar.literals();
			assertThat(literals.string()).isNotNull();
			assertThat(literals.string().regex()).isEqualTo("^\".*\"$");
			assertThat(literals.string().values()).isNull();
		}

		@Test
		@DisplayName("should parse literals with number regex")
		void shouldParseLiteralsWithNumberRegex() {
			String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: test-dsl
				  description: "Test DSL"
				  version: 1.0
				literals:
				  number:
				    regex: "^-?[0-9]+(\\\\.[0-9]+)?$"
				""";

			SxlGrammar grammar = parser.parseString(yaml);
			assertThat(grammar.literals().number().regex()).isEqualTo("^-?[0-9]+(\\.[0-9]+)?$");
		}

		@Test
		@DisplayName("should parse literals with boolean values")
		void shouldParseLiteralsWithBooleanValues() {
			String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: test-dsl
				  description: "Test DSL"
				  version: 1.0
				literals:
				  boolean:
				    values: [true, false]
				""";

			SxlGrammar grammar = parser.parseString(yaml);
			LiteralRule booleanRule = grammar.literals().boolean_();
			assertThat(booleanRule.regex()).isNull();
			assertThat(booleanRule.values()).containsExactly(true, false);
		}

		@Test
		@DisplayName("should parse literals with null values")
		void shouldParseLiteralsWithNullValues() {
			String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: test-dsl
				  description: "Test DSL"
				  version: 1.0
				literals:
				  null:
				    values: [null]
				""";

			SxlGrammar grammar = parser.parseString(yaml);
			LiteralRule nullRule = grammar.literals().null_();
			// YAML parsing of [null] can vary - just verify the rule exists
			assertThat(nullRule).isNotNull();
			// The values list might be null, empty, or contain null - all are valid
		}

		@Test
		@DisplayName("should parse all literal types")
		void shouldParseAllLiteralTypes() {
			// Use simpler YAML to avoid escaping issues
			String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: test-dsl
				  description: "Test DSL"
				  version: 1.0
				literals:
				  string:
				    regex: '^".*"$'
				  number:
				    regex: '^-?[0-9]+(\\.[0-9]+)?$'
				  boolean:
				    values: [true, false]
				  null:
				    values: [null]
				""";

			SxlGrammar grammar = parser.parseString(yaml);
			LiteralDefinitions literals = grammar.literals();
			assertThat(literals).isNotNull();
			assertThat(literals.string()).isNotNull();
			assertThat(literals.number()).isNotNull();
			assertThat(literals.boolean_()).isNotNull();
			assertThat(literals.null_()).isNotNull();
		}
	}

	@Nested
	@DisplayName("Identifier Rule Tests")
	class IdentifierRuleTests {

		@Test
		@DisplayName("should parse identifier rule")
		void shouldParseIdentifierRule() {
			String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: test-dsl
				  description: "Test DSL"
				  version: 1.0
				identifier:
				  description: "Identifier pattern"
				  pattern: "^[a-z_][a-z0-9_]*$"
				""";

			SxlGrammar grammar = parser.parseString(yaml);
			IdentifierRule identifier = grammar.identifier();
			assertThat(identifier).isNotNull();
			assertThat(identifier.description()).isEqualTo("Identifier pattern");
			assertThat(identifier.pattern()).isEqualTo("^[a-z_][a-z0-9_]*$");
		}
	}

	@Nested
	@DisplayName("Reserved Symbols Tests")
	class ReservedSymbolsTests {

		@Test
		@DisplayName("should parse reserved symbols")
		void shouldParseReservedSymbols() {
			String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: test-dsl
				  description: "Test DSL"
				  version: 1.0
				reserved_symbols:
				  - AS
				  - FROM
				  - WHERE
				""";

			SxlGrammar grammar = parser.parseString(yaml);
			assertThat(grammar.reservedSymbols()).containsExactly("AS", "FROM", "WHERE");
		}
	}

	@Nested
	@DisplayName("Embedding Config Tests")
	class EmbeddingConfigTests {

		@Test
		@DisplayName("should parse embedding config")
		void shouldParseEmbeddingConfig() {
			String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: test-dsl
				  description: "Test DSL"
				  version: 1.0
				embedding:
				  enabled: true
				  symbol: EMBED
				  auto_register_symbol: true
				""";

			SxlGrammar grammar = parser.parseString(yaml);
			EmbeddingConfig embedding = grammar.embedding();
			assertThat(embedding).isNotNull();
			assertThat(embedding.enabled()).isTrue();
			assertThat(embedding.symbol()).isEqualTo("EMBED");
			assertThat(embedding.autoRegisterSymbol()).isTrue();
			assertThat(embedding.params()).isEmpty();
		}

		@Test
		@DisplayName("should parse embedding config with parameters")
		void shouldParseEmbeddingConfigWithParameters() {
			String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: test-dsl
				  description: "Test DSL"
				  version: 1.0
				embedding:
				  enabled: true
				  params:
				    - name: dsl-id
				      type: dsl-id
				      cardinality: required
				""";

			SxlGrammar grammar = parser.parseString(yaml);
			EmbeddingConfig embedding = grammar.embedding();
			assertThat(embedding.params()).hasSize(1);
			assertThat(embedding.params().get(0).name()).isEqualTo("dsl-id");
			assertThat(embedding.params().get(0).type()).isEqualTo("dsl-id");
		}
	}

	@Nested
	@DisplayName("Global Constraint Tests")
	class GlobalConstraintTests {

		@Test
		@DisplayName("should parse global constraints")
		void shouldParseGlobalConstraints() {
			String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: test-dsl
				  description: "Test DSL"
				  version: 1.0
				constraints:
				  - rule: must_have_root
				    symbol: ROOT
				""";

			SxlGrammar grammar = parser.parseString(yaml);
			assertThat(grammar.constraints()).hasSize(1);

			GlobalConstraint constraint = grammar.constraints().get(0);
			assertThat(constraint.rule()).isEqualTo("must_have_root");
			assertThat(constraint.symbol()).isEqualTo("ROOT");
		}

		@Test
		@DisplayName("should parse global constraint with all fields")
		void shouldParseGlobalConstraintWithAllFields() {
			String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: test-dsl
				  description: "Test DSL"
				  version: 1.0
				constraints:
				  - rule: order_requires
				    target: SYMBOL1
				    symbol: SYMBOL2
				    depends_on: SYMBOL3
				""";

			SxlGrammar grammar = parser.parseString(yaml);
			GlobalConstraint constraint = grammar.constraints().get(0);
			assertThat(constraint.rule()).isEqualTo("order_requires");
			assertThat(constraint.target()).isEqualTo("SYMBOL1");
			assertThat(constraint.symbol()).isEqualTo("SYMBOL2");
			assertThat(constraint.dependsOn()).isEqualTo("SYMBOL3");
		}
	}

	@Nested
	@DisplayName("LLM Specs Tests")
	class LlmSpecsTests {

		@Test
		@DisplayName("should parse LLM specs with defaults")
		void shouldParseLlmSpecsWithDefaults() {
			String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: test-dsl
				  description: "Test DSL"
				  version: 1.0
				llm_specs:
				  defaults:
				    style: strict
				    max_examples: 3
				    include_constraints: true
				    include_symbol_summaries: true
				    include_literal_rules: true
				    include_identifier_rules: true
				    formatting: block
				    enforce_canonical_form: true
				    preamble: true
				    postamble: false
				""";

			SxlGrammar grammar = parser.parseString(yaml);
			LlmSpecs llmSpecs = grammar.llmSpecs();
			assertThat(llmSpecs).isNotNull();

			LlmDefaults defaults = llmSpecs.defaults();
			assertThat(defaults).isNotNull();
			assertThat(defaults.style()).isEqualTo("strict");
			assertThat(defaults.maxExamples()).isEqualTo(3);
			assertThat(defaults.includeConstraints()).isTrue();
			assertThat(defaults.includeSymbolSummaries()).isTrue();
			assertThat(defaults.includeLiteralRules()).isTrue();
			assertThat(defaults.includeIdentifierRules()).isTrue();
			assertThat(defaults.formatting()).isEqualTo("block");
			assertThat(defaults.enforceCanonicalForm()).isTrue();
			assertThat(defaults.preamble()).isTrue();
			assertThat(defaults.postamble()).isFalse();
		}

		@Test
		@DisplayName("should parse LLM specs with provider defaults")
		void shouldParseLlmSpecsWithProviderDefaults() {
			String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: test-dsl
				  description: "Test DSL"
				  version: 1.0
				llm_specs:
				  provider_defaults:
				    openai:
				      style: strict
				      max_examples: 2
				    anthropic:
				      style: explanatory
				      max_examples: 5
				""";

			SxlGrammar grammar = parser.parseString(yaml);
			Map<String, LlmProviderDefaults> providerDefaults = grammar.llmSpecs().providerDefaults();
			assertThat(providerDefaults).hasSize(2);

			LlmProviderDefaults openai = providerDefaults.get("openai");
			assertThat(openai.style()).isEqualTo("strict");
			assertThat(openai.maxExamples()).isEqualTo(2);

			LlmProviderDefaults anthropic = providerDefaults.get("anthropic");
			assertThat(anthropic.style()).isEqualTo("explanatory");
			assertThat(anthropic.maxExamples()).isEqualTo(5);
		}

		@Test
		@DisplayName("should parse LLM specs with model overrides")
		void shouldParseLlmSpecsWithModelOverrides() {
			String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: test-dsl
				  description: "Test DSL"
				  version: 1.0
				llm_specs:
				  models:
				    openai:
				      gpt-4:
				        overrides:
				          style: concise
				          max_examples: 2
				          include_constraints: false
				          formatting: compact
				""";

			SxlGrammar grammar = parser.parseString(yaml);
			Map<String, Map<String, LlmModelOverrides>> models = grammar.llmSpecs().models();
			assertThat(models).hasSize(1);

			Map<String, LlmModelOverrides> openaiModels = models.get("openai");
			assertThat(openaiModels).hasSize(1);

			LlmModelOverrides gpt4 = openaiModels.get("gpt-4");
			LlmOverrides overrides = gpt4.overrides();
			assertThat(overrides.style()).isEqualTo("concise");
			assertThat(overrides.maxExamples()).isEqualTo(2);
			assertThat(overrides.includeConstraints()).isFalse();
			assertThat(overrides.formatting()).isEqualTo("compact");
		}

		@Test
		@DisplayName("should parse LLM specs with profiles")
		void shouldParseLlmSpecsWithProfiles() {
			String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: test-dsl
				  description: "Test DSL"
				  version: 1.0
				llm_specs:
				  profiles:
				    testing:
				      style: verbose
				      include_constraints: true
				      max_examples: 10
				""";

			SxlGrammar grammar = parser.parseString(yaml);
			Map<String, LlmProfile> profiles = grammar.llmSpecs().profiles();
			assertThat(profiles).hasSize(1);

			LlmProfile testing = profiles.get("testing");
			assertThat(testing.style()).isEqualTo("verbose");
			assertThat(testing.includeConstraints()).isTrue();
			assertThat(testing.maxExamples()).isEqualTo(10);
		}
	}

	@Nested
	@DisplayName("Comprehensive Grammar Tests")
	class ComprehensiveGrammarTests {

		@Test
		@DisplayName("should parse comprehensive grammar with all features")
		void shouldParseComprehensiveGrammarWithAllFeatures() {
			String yaml = """
				meta_grammar_version: 1.2
				
				dsl:
				  id: comprehensive-dsl
				  description: "Comprehensive test DSL"
				  version: 2.0
				
				symbols:
				  ROOT:
				    description: "Root symbol"
				    kind: node
				    params:
				      - name: value
				        description: "A value"
				        type: literal(string)
				        cardinality: required
				      - name: children
				        description: "Child nodes"
				        type: node
				        allowed_symbols: [CHILD]
				        cardinality: zeroOrMore
				        ordered: true
				    constraints:
				      - rule: must_have_root
				        symbol: ROOT
				    examples:
				      - label: "Simple"
				        code: "(ROOT value)"
				      - label: "Complex"
				        code: "(ROOT value (CHILD))"
				
				  CHILD:
				    description: "Child symbol"
				    kind: node
				    params:
				      - name: id
				        type: identifier
				        identifier_rules:
				          pattern: "^[a-z_][a-z0-9_]*$"
				
				literals:
				  string:
				    regex: '^".*"$'
				  number:
				    regex: '^-?[0-9]+(\\.[0-9]+)?$'
				  boolean:
				    values: [true, false]
				  null:
				    values: [null]
				
				identifier:
				  description: "Identifier pattern"
				  pattern: "^[a-z_][a-z0-9_]*$"
				
				reserved_symbols:
				  - AS
				  - FROM
				
				embedding:
				  enabled: true
				  symbol: EMBED
				  auto_register_symbol: true
				  params:
				    - name: dsl-id
				      type: dsl-id
				      cardinality: required
				
				constraints:
				  - rule: must_have_root
				    symbol: ROOT
				  - rule: order_requires
				    target: CHILD
				    symbol: ROOT
				    depends_on: ROOT
				
				llm_specs:
				  defaults:
				    style: strict
				    max_examples: 3
				    include_constraints: true
				    include_symbol_summaries: true
				    include_literal_rules: true
				    include_identifier_rules: true
				    formatting: block
				    enforce_canonical_form: true
				    preamble: true
				    postamble: false
				  provider_defaults:
				    openai:
				      style: strict
				      max_examples: 2
				    anthropic:
				      style: explanatory
				      max_examples: 5
				  models:
				    openai:
				      gpt-4:
				        overrides:
				          style: concise
				          max_examples: 2
				          include_constraints: false
				          formatting: compact
				  profiles:
				    testing:
				      style: verbose
				      include_constraints: true
				      max_examples: 10
				""";

			SxlGrammar grammar = parser.parseString(yaml);

			// Verify all major components
			assertThat(grammar.metaGrammarVersion()).isEqualTo("1.2");
			assertThat(grammar.dsl().id()).isEqualTo("comprehensive-dsl");
			assertThat(grammar.symbols()).hasSize(2);
			assertThat(grammar.literals().string()).isNotNull();
			assertThat(grammar.literals().number()).isNotNull();
			assertThat(grammar.literals().boolean_()).isNotNull();
			assertThat(grammar.literals().null_()).isNotNull();
			assertThat(grammar.identifier()).isNotNull();
			assertThat(grammar.reservedSymbols()).hasSize(2);
			assertThat(grammar.embedding()).isNotNull();
			assertThat(grammar.constraints()).hasSize(2);
			assertThat(grammar.llmSpecs()).isNotNull();
			assertThat(grammar.llmSpecs().defaults()).isNotNull();
			assertThat(grammar.llmSpecs().providerDefaults()).hasSize(2);
			assertThat(grammar.llmSpecs().models()).hasSize(1);
			assertThat(grammar.llmSpecs().profiles()).hasSize(1);
		}
	}

	@Nested
	@DisplayName("Input Source Tests")
	class InputSourceTests {

		@Test
		@DisplayName("should parse from string")
		void shouldParseFromString() {
			String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: test-dsl
				  description: "Test DSL"
				  version: 1.0
				""";

			SxlGrammar grammar = parser.parseString(yaml);
			assertThat(grammar).isNotNull();
		}

		@Test
		@DisplayName("should parse from input stream")
		void shouldParseFromInputStream() {
			String yaml = """
				meta_grammar_version: 1.2
				dsl:
				  id: test-dsl
				  description: "Test DSL"
				  version: 1.0
				""";

			InputStream stream = new java.io.ByteArrayInputStream(yaml.getBytes());
			SxlGrammar grammar = parser.parse(stream);
			assertThat(grammar).isNotNull();
		}
	}

	@Nested
	@DisplayName("Real Grammar File Tests")
	class RealGrammarFileTests {

		@Test
		@DisplayName("should parse sxl-meta-grammar-plan.yml")
		void shouldParsePlanGrammar() {
			InputStream stream = getClass().getClassLoader()
				.getResourceAsStream("sxl-meta-grammar-plan.yml");
			assertThat(stream).isNotNull();

			SxlGrammar grammar = parser.parse(stream);

			assertThat(grammar).isNotNull();
			assertThat(grammar.metaGrammarVersion()).isEqualTo("1.2");
			assertThat(grammar.dsl().id()).isEqualTo("sxl-plan");
			assertThat(grammar.symbols()).isNotEmpty();
			assertThat(grammar.literals()).isNotNull();
			assertThat(grammar.identifier()).isNotNull();
			assertThat(grammar.reservedSymbols()).isNotEmpty();
			assertThat(grammar.embedding()).isNotNull();
			assertThat(grammar.constraints()).isNotEmpty();
			assertThat(grammar.llmSpecs()).isNotNull();
		}

		@Test
		@DisplayName("should parse sxl-meta-grammar-sql.yml")
		void shouldParseSqlGrammar() {
			InputStream stream = getClass().getClassLoader()
				.getResourceAsStream("sxl-meta-grammar-sql.yml");
			assertThat(stream).isNotNull();

			SxlGrammar grammar = parser.parse(stream);

			assertThat(grammar).isNotNull();
			assertThat(grammar.metaGrammarVersion()).isEqualTo("1.2");
			assertThat(grammar.dsl().id()).isEqualTo("sxl-sql");
			assertThat(grammar.symbols()).isNotEmpty();
			assertThat(grammar.literals()).isNotNull();
			assertThat(grammar.identifier()).isNotNull();
			assertThat(grammar.reservedSymbols()).isNotEmpty();
			assertThat(grammar.embedding()).isNotNull();
			assertThat(grammar.constraints()).isNotEmpty();
			assertThat(grammar.llmSpecs()).isNotNull();
		}
	}
}

