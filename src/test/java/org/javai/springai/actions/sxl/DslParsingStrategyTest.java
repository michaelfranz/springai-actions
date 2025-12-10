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
import org.junit.jupiter.api.Test;

/**
 * Tests for DSL-aware parsing strategy.
 * Tests validation against meta-grammar rules including symbols, parameters, types, and constraints.
 */
class DslParsingStrategyTest {

	@Test
	void parseValidSymbol() {
		// Test: FACTORY with two required parameters - no constraints needed
		SxlGrammar grammar = createGrammarWithFactory();
		
		SxlTokenizer tokenizer = new SxlTokenizer("(FACTORY table alias)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, grammar);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		assertThat(nodes.getFirst().symbol()).isEqualTo("FACTORY");
		assertThat(nodes.getFirst().args()).hasSize(2);
	}

	@Test
	void parseUnknownSymbolThrowsException() {
		// Test: Unknown symbol should fail - minimal grammar with one known symbol
		SxlGrammar grammar = createGrammarWithFactory();
		
		SxlTokenizer tokenizer = new SxlTokenizer("(UNKNOWN arg1)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, grammar);
		
		assertThatThrownBy(parser::parse)
			.isInstanceOf(SxlParseException.class)
			.hasMessageContaining("Unknown symbol")
			.hasMessageContaining("UNKNOWN");
	}

	@Test
	void parseWithMissingRequiredParameterThrowsException() {
		// Test: Missing required parameter - FACTORY needs two params
		SxlGrammar grammar = createGrammarWithFactory();
		
		SxlTokenizer tokenizer = new SxlTokenizer("(FACTORY table)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, grammar);
		
		assertThatThrownBy(parser::parse)
			.isInstanceOf(SxlParseException.class)
			.hasMessageContaining("requires parameter")
			.hasMessageContaining("alias");
	}

	@Test
	void parseWithTooManyParametersThrowsException() {
		// Test: Too many parameters - FACTORY only accepts 2
		SxlGrammar grammar = createGrammarWithFactory();
		
		SxlTokenizer tokenizer = new SxlTokenizer("(FACTORY table alias extra)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, grammar);
		
		assertThatThrownBy(parser::parse)
			.isInstanceOf(SxlParseException.class)
			.hasMessageContaining("too many arguments");
	}

	@Test
	void parseWithOptionalParameter() {
		// Test: Optional parameter not provided - SELECTOR with required only
		SxlGrammar grammar = createGrammarWithSelector();
		
		SxlTokenizer tokenizer = new SxlTokenizer("(SELECTOR col1)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, grammar);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		assertThat(nodes.getFirst().symbol()).isEqualTo("SELECTOR");
		assertThat(nodes.getFirst().args()).hasSize(1);
	}

	@Test
	void parseWithOptionalParameterProvided() {
		// Test: Optional parameter provided - SELECTOR with both params
		SxlGrammar grammar = createGrammarWithSelector();
		
		SxlTokenizer tokenizer = new SxlTokenizer("(SELECTOR col1 col2)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, grammar);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		assertThat(nodes.getFirst().args()).hasSize(2);
	}

	@Test
	void parseWithZeroOrMoreParameters() {
		// Test: zeroOrMore with zero args - AGGREGATOR accepts 0+
		SxlGrammar grammar = createGrammarWithAggregator();
		
		SxlTokenizer tokenizer = new SxlTokenizer("(AGGREGATOR)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, grammar);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		assertThat(nodes.getFirst().args()).isEmpty();
	}

	@Test
	void parseWithMultipleZeroOrMoreParameters() {
		// Test: zeroOrMore with multiple args - AGGREGATOR accepts 0+
		SxlGrammar grammar = createGrammarWithAggregator();
		
		SxlTokenizer tokenizer = new SxlTokenizer("(AGGREGATOR col1 col2 col3)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, grammar);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		assertThat(nodes.getFirst().args()).hasSize(3);
	}

	@Test
	void parseWithOneOrMoreParameters() {
		// Test: oneOrMore with one arg - COMBINER requires 1+
		SxlGrammar grammar = createGrammarWithCombiner();
		
		SxlTokenizer tokenizer = new SxlTokenizer("(COMBINER col1)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, grammar);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		assertThat(nodes.getFirst().args()).hasSize(1);
	}

	@Test
	void parseWithMissingOneOrMoreParameterThrowsException() {
		// Test: oneOrMore with zero args should fail - COMBINER requires 1+
		SxlGrammar grammar = createGrammarWithCombiner();
		
		SxlTokenizer tokenizer = new SxlTokenizer("(COMBINER)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, grammar);
		
		assertThatThrownBy(parser::parse)
			.isInstanceOf(SxlParseException.class)
			.hasMessageContaining("requires at least one occurrence");
	}

	@Test
	void parseWithAllowedSymbols() {
		// Test: NESTED accepts FACTORY as child - no constraints needed
		SxlGrammar grammar = createGrammarWithNestedAndFactory();
		
		SxlTokenizer tokenizer = new SxlTokenizer("(NESTED (FACTORY table alias))");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, grammar);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		SxlNode nested = nodes.getFirst();
		assertThat(nested.symbol()).isEqualTo("NESTED");
		assertThat(nested.args()).hasSize(1);
		assertThat(nested.args().getFirst().symbol()).isEqualTo("FACTORY");
	}

	@Test
	void parseWithDisallowedSymbolThrowsException() {
		// Test: NESTED rejects SELECTOR (only allows FACTORY, LIMITER)
		SxlGrammar grammar = createGrammarWithNestedAndSelector();
		
		SxlTokenizer tokenizer = new SxlTokenizer("(NESTED (SELECTOR col1))");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, grammar);
		
		assertThatThrownBy(parser::parse)
			.isInstanceOf(SxlParseException.class)
			.hasMessageContaining("expects one of")
			.hasMessageContaining("FACTORY");
	}

	@Test
	void parseWithLiteralTypeValidation() {
		// Test: LIMITER accepts number literal
		SxlGrammar grammar = createGrammarWithLimiter();
		
		SxlTokenizer tokenizer = new SxlTokenizer("(LIMITER 10)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, grammar);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		assertThat(nodes.getFirst().args().getFirst().isLiteral()).isTrue();
	}

	@Test
	void parseWithWrongLiteralTypeThrowsException() {
		// Test: LIMITER rejects string (requires number)
		SxlGrammar grammar = createGrammarWithLimiter();
		
		SxlTokenizer tokenizer = new SxlTokenizer("(LIMITER 'string')");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, grammar);
		
		assertThatThrownBy(parser::parse)
			.isInstanceOf(SxlParseException.class)
			.hasMessageContaining("expects literal type")
			.hasMessageContaining("number");
	}

	@Test
	void parseWithStringLiteralType() {
		// Test: FILTER accepts string literal
		SxlGrammar grammar = createGrammarWithFilter();
		
		SxlTokenizer tokenizer = new SxlTokenizer("(FILTER 'hello')");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, grammar);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		assertThat(nodes.getFirst().args().getFirst().isLiteral()).isTrue();
	}

	@Test
	void parseComplexValidExpression() {
		// Test: NESTED with multiple children - no constraints needed
		SxlGrammar grammar = createGrammarWithNestedFactoryAndLimiter();
		
		SxlTokenizer tokenizer = new SxlTokenizer("(NESTED (FACTORY table alias) (LIMITER 10))");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, grammar);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		SxlNode nested = nodes.getFirst();
		assertThat(nested.symbol()).isEqualTo("NESTED");
		assertThat(nested.args()).hasSize(2);
	}

	@Test
	void parseWithGlobalConstraintMustHaveRoot() {
		// Test: Global constraint requires NESTED as root - FACTORY should fail
		SxlGrammar grammar = createGrammarWithFactoryAndRootConstraint("NESTED");
		
		SxlTokenizer tokenizer = new SxlTokenizer("(FACTORY table alias)");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, grammar);
		
		assertThatThrownBy(parser::parse)
			.isInstanceOf(SxlParseException.class)
			.hasMessageContaining("must have root symbol");
	}

	@Test
	void parseWithCorrectRootSymbol() {
		// Test: Global constraint requires NESTED as root - should succeed
		SxlGrammar grammar = createGrammarWithNestedFactoryAndRootConstraint();
		
		SxlTokenizer tokenizer = new SxlTokenizer("(NESTED (FACTORY table alias))");
		List<SxlToken> tokens = tokenizer.tokenize();
		
		SxlParser parser = new SxlParser(tokens, grammar);
		List<SxlNode> nodes = parser.parse();
		
		assertThat(nodes).hasSize(1);
		assertThat(nodes.getFirst().symbol()).isEqualTo("NESTED");
	}

	// Minimal grammar factory methods - each focused on specific test needs

	private SxlGrammar createGrammarWithFactory() {
		ParameterDefinition factoryTable = new ParameterDefinition(
			"table", "Table name", "identifier", null, Cardinality.required, true, null);
		ParameterDefinition factoryAlias = new ParameterDefinition(
			"alias", "Table alias", "identifier", null, Cardinality.required, true, null);
		SymbolDefinition factory = new SymbolDefinition(
			"Factory symbol", SymbolKind.node, 
			List.of(factoryTable, factoryAlias), List.of(), List.of());

		return createBaseGrammar(Map.of("FACTORY", factory));
	}

	private SxlGrammar createGrammarWithSelector() {
		ParameterDefinition selectorRequired = new ParameterDefinition(
			"required", "Required column", "identifier", null, Cardinality.required, true, null);
		ParameterDefinition selectorOptional = new ParameterDefinition(
			"optional", "Optional column", "identifier", null, Cardinality.optional, true, null);
		SymbolDefinition selector = new SymbolDefinition(
			"Selector symbol", SymbolKind.node,
			List.of(selectorRequired, selectorOptional), List.of(), List.of());

		return createBaseGrammar(Map.of("SELECTOR", selector));
	}

	private SxlGrammar createGrammarWithAggregator() {
		ParameterDefinition aggregatorParam = new ParameterDefinition(
			"columns", "Columns to aggregate", "identifier", null, Cardinality.zeroOrMore, true, null);
		SymbolDefinition aggregator = new SymbolDefinition(
			"Aggregator symbol", SymbolKind.node,
			List.of(aggregatorParam), List.of(), List.of());

		return createBaseGrammar(Map.of("AGGREGATOR", aggregator));
	}

	private SxlGrammar createGrammarWithCombiner() {
		ParameterDefinition combinerParam = new ParameterDefinition(
			"columns", "Columns to combine", "identifier", null, Cardinality.oneOrMore, true, null);
		SymbolDefinition combiner = new SymbolDefinition(
			"Combiner symbol", SymbolKind.node,
			List.of(combinerParam), List.of(), List.of());

		return createBaseGrammar(Map.of("COMBINER", combiner));
	}

	private SxlGrammar createGrammarWithNestedAndFactory() {
		ParameterDefinition factoryTable = new ParameterDefinition(
			"table", "Table name", "identifier", null, Cardinality.required, true, null);
		ParameterDefinition factoryAlias = new ParameterDefinition(
			"alias", "Table alias", "identifier", null, Cardinality.required, true, null);
		SymbolDefinition factory = new SymbolDefinition(
			"Factory symbol", SymbolKind.node, 
			List.of(factoryTable, factoryAlias), List.of(), List.of());

		ParameterDefinition nestedChild = new ParameterDefinition(
			"child", "Child node", "node", List.of("FACTORY"), 
			Cardinality.oneOrMore, true, null);
		SymbolDefinition nested = new SymbolDefinition(
			"Nested symbol", SymbolKind.node,
			List.of(nestedChild), List.of(), List.of());

		return createBaseGrammar(Map.of("FACTORY", factory, "NESTED", nested));
	}

	private SxlGrammar createGrammarWithNestedAndSelector() {
		ParameterDefinition selectorRequired = new ParameterDefinition(
			"required", "Required column", "identifier", null, Cardinality.required, true, null);
		SymbolDefinition selector = new SymbolDefinition(
			"Selector symbol", SymbolKind.node,
			List.of(selectorRequired), List.of(), List.of());

		ParameterDefinition factoryTable = new ParameterDefinition(
			"table", "Table name", "identifier", null, Cardinality.required, true, null);
		ParameterDefinition factoryAlias = new ParameterDefinition(
			"alias", "Table alias", "identifier", null, Cardinality.required, true, null);
		SymbolDefinition factory = new SymbolDefinition(
			"Factory symbol", SymbolKind.node, 
			List.of(factoryTable, factoryAlias), List.of(), List.of());

		ParameterDefinition nestedChild = new ParameterDefinition(
			"child", "Child node", "node", List.of("FACTORY"), 
			Cardinality.oneOrMore, true, null);
		SymbolDefinition nested = new SymbolDefinition(
			"Nested symbol", SymbolKind.node,
			List.of(nestedChild), List.of(), List.of());

		return createBaseGrammar(Map.of("SELECTOR", selector, "FACTORY", factory, "NESTED", nested));
	}

	private SxlGrammar createGrammarWithLimiter() {
		ParameterDefinition limiterValue = new ParameterDefinition(
			"value", "Limit value", "literal(number)", null, Cardinality.required, true, null);
		SymbolDefinition limiter = new SymbolDefinition(
			"Limiter symbol", SymbolKind.node,
			List.of(limiterValue), List.of(), List.of());

		return createBaseGrammar(Map.of("LIMITER", limiter));
	}

	private SxlGrammar createGrammarWithFilter() {
		ParameterDefinition filterValue = new ParameterDefinition(
			"value", "Filter value", "literal(string)", null, Cardinality.required, true, null);
		SymbolDefinition filter = new SymbolDefinition(
			"Filter symbol", SymbolKind.node,
			List.of(filterValue), List.of(), List.of());

		return createBaseGrammar(Map.of("FILTER", filter));
	}

	private SxlGrammar createGrammarWithNestedFactoryAndLimiter() {
		ParameterDefinition factoryTable = new ParameterDefinition(
			"table", "Table name", "identifier", null, Cardinality.required, true, null);
		ParameterDefinition factoryAlias = new ParameterDefinition(
			"alias", "Table alias", "identifier", null, Cardinality.required, true, null);
		SymbolDefinition factory = new SymbolDefinition(
			"Factory symbol", SymbolKind.node, 
			List.of(factoryTable, factoryAlias), List.of(), List.of());

		ParameterDefinition limiterValue = new ParameterDefinition(
			"value", "Limit value", "literal(number)", null, Cardinality.required, true, null);
		SymbolDefinition limiter = new SymbolDefinition(
			"Limiter symbol", SymbolKind.node,
			List.of(limiterValue), List.of(), List.of());

		ParameterDefinition nestedChild = new ParameterDefinition(
			"child", "Child node", "node", List.of("FACTORY", "LIMITER"), 
			Cardinality.oneOrMore, true, null);
		SymbolDefinition nested = new SymbolDefinition(
			"Nested symbol", SymbolKind.node,
			List.of(nestedChild), List.of(), List.of());

		return createBaseGrammar(Map.of("FACTORY", factory, "LIMITER", limiter, "NESTED", nested));
	}

	private SxlGrammar createGrammarWithFactoryAndRootConstraint(String rootSymbol) {
		SxlGrammar base = createGrammarWithFactory();
		org.javai.springai.actions.sxl.meta.GlobalConstraint constraint = 
			new org.javai.springai.actions.sxl.meta.GlobalConstraint(
				"must_have_root", null, rootSymbol, null);
		
		return new SxlGrammar(
			base.metaGrammarVersion(), base.dsl(), base.symbols(), base.literals(), base.identifier(),
			base.reservedSymbols(), base.embedding(), List.of(constraint), base.llmSpecs());
	}

	private SxlGrammar createGrammarWithNestedFactoryAndRootConstraint() {
		SxlGrammar base = createGrammarWithNestedAndFactory();
		org.javai.springai.actions.sxl.meta.GlobalConstraint constraint = 
			new org.javai.springai.actions.sxl.meta.GlobalConstraint(
				"must_have_root", null, "NESTED", null);
		
		return new SxlGrammar(
			base.metaGrammarVersion(), base.dsl(), base.symbols(), base.literals(), base.identifier(),
			base.reservedSymbols(), base.embedding(), List.of(constraint), base.llmSpecs());
	}

	// Base grammar factory - creates grammar with common settings, no constraints
	private SxlGrammar createBaseGrammar(Map<String, SymbolDefinition> symbols) {
		LiteralDefinitions literals = new LiteralDefinitions(
			new org.javai.springai.actions.sxl.meta.LiteralRule("^\"?.*\"?$", null), // string
			new org.javai.springai.actions.sxl.meta.LiteralRule("^-?[0-9]+(\\.[0-9]+)?$", null), // number
			null, // boolean
			null  // null
		);

		IdentifierRule identifier = new IdentifierRule(
			"Identifier pattern", "^[A-Za-z_][A-Za-z0-9_\\.\\-]*$");

		return new SxlGrammar(
			"1.2",
			new DslMetadata("test-dsl", "Test DSL", "1.0"),
			symbols,
			literals,
			identifier,
			List.of(), // reserved symbols
			new EmbeddingConfig(false, null, false, List.of()), // embedding
			List.of(), // no constraints by default
			null // llm specs
		);
	}
}

