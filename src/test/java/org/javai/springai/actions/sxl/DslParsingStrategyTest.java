package org.javai.springai.actions.sxl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import java.util.Map;
import org.javai.springai.actions.sxl.meta.Cardinality;
import org.javai.springai.actions.sxl.meta.DslMetadata;
import org.javai.springai.actions.sxl.meta.EmbeddingConfig;
import org.javai.springai.actions.sxl.meta.IdentifierRule;
import org.javai.springai.actions.sxl.meta.LiteralDefinitions;
import org.javai.springai.actions.sxl.meta.ParameterDefinition;
import org.javai.springai.actions.sxl.meta.SymbolDefinition;
import org.javai.springai.actions.sxl.meta.SymbolKind;
import org.javai.springai.actions.sxl.meta.SxlGrammar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for DSL-aware parsing strategy.
 * Tests validation against meta-grammar rules including symbols, parameters, types, and constraints.
 */
class DslParsingStrategyTest {

	private SxlGrammar testGrammar;

	@BeforeEach
	void setUp() {
		// Create a simple test grammar
		testGrammar = createTestGrammar();
	}

	@Test
	void parseValidSymbol() {
		SxlTokenizer tokenizer = new SxlTokenizer("(FACTORY table alias)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, testGrammar);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		assertThat(nodes.getFirst().symbol()).isEqualTo("FACTORY");
		assertThat(nodes.getFirst().args()).hasSize(2);
	}

	@Test
	void parseUnknownSymbolThrowsException() {
		SxlTokenizer tokenizer = new SxlTokenizer("(UNKNOWN arg1)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, testGrammar);
		
		assertThatThrownBy(parser::parse)
			.isInstanceOf(SxlParseException.class)
			.hasMessageContaining("Unknown symbol")
			.hasMessageContaining("UNKNOWN");
	}

	@Test
	void parseWithMissingRequiredParameterThrowsException() {
		SxlTokenizer tokenizer = new SxlTokenizer("(FACTORY table)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, testGrammar);
		
		assertThatThrownBy(parser::parse)
			.isInstanceOf(SxlParseException.class)
			.hasMessageContaining("requires parameter")
			.hasMessageContaining("alias");
	}

	@Test
	void parseWithTooManyParametersThrowsException() {
		SxlTokenizer tokenizer = new SxlTokenizer("(FACTORY table alias extra)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, testGrammar);
		
		assertThatThrownBy(parser::parse)
			.isInstanceOf(SxlParseException.class)
			.hasMessageContaining("too many arguments");
	}

	@Test
	void parseWithOptionalParameter() {
		SxlTokenizer tokenizer = new SxlTokenizer("(SELECTOR col1)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, testGrammar);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		assertThat(nodes.getFirst().symbol()).isEqualTo("SELECTOR");
		assertThat(nodes.getFirst().args()).hasSize(1);
	}

	@Test
	void parseWithOptionalParameterProvided() {
		SxlTokenizer tokenizer = new SxlTokenizer("(SELECTOR col1 col2)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, testGrammar);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		assertThat(nodes.getFirst().args()).hasSize(2);
	}

	@Test
	void parseWithZeroOrMoreParameters() {
		SxlTokenizer tokenizer = new SxlTokenizer("(AGGREGATOR)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, testGrammar);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		assertThat(nodes.getFirst().args()).isEmpty();
	}

	@Test
	void parseWithMultipleZeroOrMoreParameters() {
		SxlTokenizer tokenizer = new SxlTokenizer("(AGGREGATOR col1 col2 col3)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, testGrammar);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		assertThat(nodes.getFirst().args()).hasSize(3);
	}

	@Test
	void parseWithOneOrMoreParameters() {
		SxlTokenizer tokenizer = new SxlTokenizer("(COMBINER col1)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, testGrammar);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		assertThat(nodes.getFirst().args()).hasSize(1);
	}

	@Test
	void parseWithMissingOneOrMoreParameterThrowsException() {
		SxlTokenizer tokenizer = new SxlTokenizer("(COMBINER)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, testGrammar);
		
		assertThatThrownBy(parser::parse)
			.isInstanceOf(SxlParseException.class)
			.hasMessageContaining("requires at least one occurrence");
	}

	@Test
	void parseWithAllowedSymbols() {
		SxlTokenizer tokenizer = new SxlTokenizer("(NESTED (FACTORY table alias))");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, testGrammar);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		SxlNode nested = nodes.getFirst();
		assertThat(nested.symbol()).isEqualTo("NESTED");
		assertThat(nested.args()).hasSize(1);
		assertThat(nested.args().getFirst().symbol()).isEqualTo("FACTORY");
	}

	@Test
	void parseWithDisallowedSymbolThrowsException() {
		SxlTokenizer tokenizer = new SxlTokenizer("(NESTED (SELECTOR col1))");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, testGrammar);
		
		assertThatThrownBy(parser::parse)
			.isInstanceOf(SxlParseException.class)
			.hasMessageContaining("expects one of")
			.hasMessageContaining("FACTORY");
	}

	@Test
	void parseWithLiteralTypeValidation() {
		SxlTokenizer tokenizer = new SxlTokenizer("(LIMITER 10)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, testGrammar);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		assertThat(nodes.getFirst().args().getFirst().isLiteral()).isTrue();
	}

	@Test
	void parseWithWrongLiteralTypeThrowsException() {
		SxlTokenizer tokenizer = new SxlTokenizer("(LIMITER 'string')");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, testGrammar);
		
		assertThatThrownBy(parser::parse)
			.isInstanceOf(SxlParseException.class)
			.hasMessageContaining("expects literal type")
			.hasMessageContaining("number");
	}

	@Test
	void parseWithStringLiteralType() {
		SxlTokenizer tokenizer = new SxlTokenizer("(FILTER 'hello')");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, testGrammar);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		assertThat(nodes.getFirst().args().getFirst().isLiteral()).isTrue();
	}

	@Test
	void parseComplexValidExpression() {
		SxlTokenizer tokenizer = new SxlTokenizer("(NESTED (FACTORY table alias) (LIMITER 10))");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, testGrammar);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		SxlNode nested = nodes.getFirst();
		assertThat(nested.symbol()).isEqualTo("NESTED");
		assertThat(nested.args()).hasSize(2);
	}

	@Test
	void parseWithGlobalConstraintMustHaveRoot() {
		SxlTokenizer tokenizer = new SxlTokenizer("(FACTORY table alias)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, testGrammar);
		
		// Should fail because root must be NESTED according to grammar constraints
		assertThatThrownBy(parser::parse)
			.isInstanceOf(SxlParseException.class)
			.hasMessageContaining("must have root symbol");
	}

	@Test
	void parseWithCorrectRootSymbol() {
		SxlTokenizer tokenizer = new SxlTokenizer("(NESTED (FACTORY table alias))");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, testGrammar);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		assertThat(nodes.getFirst().symbol()).isEqualTo("NESTED");
	}

	/**
	 * Creates a simple test grammar for testing DSL validation.
	 */
	private SxlGrammar createTestGrammar() {
		// FACTORY requires two identifiers (table and alias)
		ParameterDefinition factoryTable = new ParameterDefinition(
			"table", "Table name", "identifier", null, Cardinality.required, true, null);
		ParameterDefinition factoryAlias = new ParameterDefinition(
			"alias", "Table alias", "identifier", null, Cardinality.required, true, null);

		SymbolDefinition factory = new SymbolDefinition(
			"Factory symbol", SymbolKind.node, 
			List.of(factoryTable, factoryAlias), List.of(), List.of());

		// SELECTOR has one required and one optional identifier
		ParameterDefinition selectorRequired = new ParameterDefinition(
			"required", "Required column", "identifier", null, Cardinality.required, true, null);
		ParameterDefinition selectorOptional = new ParameterDefinition(
			"optional", "Optional column", "identifier", null, Cardinality.optional, true, null);

		SymbolDefinition selector = new SymbolDefinition(
			"Selector symbol", SymbolKind.node,
			List.of(selectorRequired, selectorOptional), List.of(), List.of());

		// AGGREGATOR has zeroOrMore identifiers
		ParameterDefinition aggregatorParam = new ParameterDefinition(
			"columns", "Columns to aggregate", "identifier", null, Cardinality.zeroOrMore, true, null);

		SymbolDefinition aggregator = new SymbolDefinition(
			"Aggregator symbol", SymbolKind.node,
			List.of(aggregatorParam), List.of(), List.of());

		// COMBINER has oneOrMore identifiers
		ParameterDefinition combinerParam = new ParameterDefinition(
			"columns", "Columns to combine", "identifier", null, Cardinality.oneOrMore, true, null);

		SymbolDefinition combiner = new SymbolDefinition(
			"Combiner symbol", SymbolKind.node,
			List.of(combinerParam), List.of(), List.of());

		// NESTED accepts FACTORY and LIMITER as children
		ParameterDefinition nestedChild = new ParameterDefinition(
			"child", "Child node", "node", List.of("FACTORY", "LIMITER"), 
			Cardinality.oneOrMore, true, null);

		SymbolDefinition nested = new SymbolDefinition(
			"Nested symbol", SymbolKind.node,
			List.of(nestedChild), List.of(), List.of());

		// LIMITER requires a number literal
		ParameterDefinition limiterValue = new ParameterDefinition(
			"value", "Limit value", "literal(number)", null, Cardinality.required, true, null);

		SymbolDefinition limiter = new SymbolDefinition(
			"Limiter symbol", SymbolKind.node,
			List.of(limiterValue), List.of(), List.of());

		// FILTER requires a string literal
		ParameterDefinition filterValue = new ParameterDefinition(
			"value", "Filter value", "literal(string)", null, Cardinality.required, true, null);

		SymbolDefinition filter = new SymbolDefinition(
			"Filter symbol", SymbolKind.node,
			List.of(filterValue), List.of(), List.of());

		Map<String, SymbolDefinition> symbols = Map.of(
			"FACTORY", factory,
			"SELECTOR", selector,
			"AGGREGATOR", aggregator,
			"COMBINER", combiner,
			"NESTED", nested,
			"LIMITER", limiter,
			"FILTER", filter
		);

		// Literal definitions
		LiteralDefinitions literals = new LiteralDefinitions(
			new org.javai.springai.actions.sxl.meta.LiteralRule("^\"?.*\"?$", null), // string
			new org.javai.springai.actions.sxl.meta.LiteralRule("^-?[0-9]+(\\.[0-9]+)?$", null), // number
			null, // boolean
			null  // null
		);

		IdentifierRule identifier = new IdentifierRule(
			"Identifier pattern", "^[A-Za-z_][A-Za-z0-9_\\.\\-]*$");

		// Global constraint: must have root symbol NESTED
		org.javai.springai.actions.sxl.meta.GlobalConstraint mustHaveRoot = 
			new org.javai.springai.actions.sxl.meta.GlobalConstraint(
				"must_have_root", null, "NESTED", null);

		return new SxlGrammar(
			"1.2",
			new DslMetadata("test-dsl", "Test DSL", "1.0"),
			symbols,
			literals,
			identifier,
			List.of(), // reserved symbols
			new EmbeddingConfig(false, null, false, List.of()), // embedding
			List.of(mustHaveRoot), // constraints
			null // llm specs
		);
	}
}

