package org.javai.springai.sxl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import java.util.Map;
import org.javai.springai.sxl.ComplexDslValidator;
import org.javai.springai.sxl.DefaultValidatorRegistry;
import org.javai.springai.sxl.SxlNode;
import org.javai.springai.sxl.SxlParseException;
import org.javai.springai.sxl.SxlToken;
import org.javai.springai.sxl.SxlTokenizer;
import org.javai.springai.sxl.grammar.Cardinality;
import org.javai.springai.sxl.grammar.DslMetadata;
import org.javai.springai.sxl.grammar.IdentifierRule;
import org.javai.springai.sxl.grammar.LiteralDefinitions;
import org.javai.springai.sxl.grammar.ParameterDefinition;
import org.javai.springai.sxl.grammar.SymbolDefinition;
import org.javai.springai.sxl.grammar.SymbolKind;
import org.javai.springai.sxl.grammar.SxlGrammar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for DSL embedding functionality.
 * Tests EMBED node recognition, delegation to inner validators, and error handling.
 */
class DslEmbeddingTest {

	private SxlGrammar sqlGrammar;
	private SxlGrammar workflowGrammar;
	private DefaultValidatorRegistry registry;

	@BeforeEach
	void setUp() {
		// Create SQL grammar (inner DSL)
		sqlGrammar = createSqlGrammar();
		
		// Create Workflow grammar (outer DSL that can embed SQL)
		workflowGrammar = createWorkflowGrammar();
		
		// Create registry and register grammars
		registry = new DefaultValidatorRegistry();
		registry.addGrammar("sxl-sql", sqlGrammar);
		registry.addGrammar("sxl-workflow", workflowGrammar);
	}

	@Test
	void embedSingleSqlQueryInWorkflow() {
		String input = """
			(EMBED sxl-workflow
			  (WORKFLOW
			    (STEP load_data
			      (EMBED sxl-sql
			        (Q
			          (F fact_sales f)
			          (S
			            (AS f.id order_id)
			            (AS f.amount total)
			          )
			        )
			      )
			    )
			  )
			)
			""";
		
		SxlTokenizer tokenizer = new SxlTokenizer(input);
		List<SxlToken> tokens = tokenizer.tokenize();
		
		ComplexDslValidator validator = new ComplexDslValidator(registry);
		List<SxlNode> nodes = validator.parseAndValidate(tokens);
		
		assertThat(nodes).hasSize(1);
		assertThat(nodes.get(0).symbol()).isEqualTo("EMBED");
		// Extract WORKFLOW from EMBED payload
		SxlNode embed = nodes.get(0);
		assertThat(embed.args()).hasSize(2); // dsl-id + WORKFLOW
		SxlNode workflow = embed.args().get(1);
		assertThat(workflow.symbol()).isEqualTo("WORKFLOW");
	}

	@Test
	void embedMultipleSqlQueriesInWorkflow() {
		String input = """
			(EMBED sxl-workflow
			  (WORKFLOW
			    (STEP complex
			      (EMBED sxl-sql
			        (Q (F table1 t1) (S (AS t1.id id1)))
			        (Q (F table2 t2) (S (AS t2.id id2)))
			      )
			    )
			  )
			)
			""";
		
		SxlTokenizer tokenizer = new SxlTokenizer(input);
		List<SxlToken> tokens = tokenizer.tokenize();
		
		ComplexDslValidator validator = new ComplexDslValidator(registry);
		List<SxlNode> nodes = validator.parseAndValidate(tokens);
		
		assertThat(nodes).hasSize(1);
		assertThat(nodes.get(0).symbol()).isEqualTo("EMBED");
		SxlNode workflow = nodes.get(0).args().get(1);
		assertThat(workflow.symbol()).isEqualTo("WORKFLOW");
	}

	@Test
	void embedUnknownDslThrowsException() {
		String input = """
			(EMBED sxl-workflow
			  (WORKFLOW
			    (STEP load_data
			      (EMBED unknown-dsl
			        (SOME_SYMBOL)
			      )
			    )
			  )
			)
			""";
		
		SxlTokenizer tokenizer = new SxlTokenizer(input);
		List<SxlToken> tokens = tokenizer.tokenize();
		
		ComplexDslValidator validator = new ComplexDslValidator(registry);
		
		assertThatThrownBy(() -> validator.parseAndValidate(tokens))
			.isInstanceOf(SxlParseException.class)
			.hasMessageContaining("unknown DSL")
			.hasMessageContaining("unknown-dsl");
	}

	@Test
	void embedWithInvalidInnerDslSyntaxThrowsException() {
		String input = """
			(EMBED sxl-workflow
			  (WORKFLOW
			    (STEP load_data
			      (EMBED sxl-sql
			        (INVALID_SYMBOL)
			      )
			    )
			  )
			)
			""";
		
		SxlTokenizer tokenizer = new SxlTokenizer(input);
		List<SxlToken> tokens = tokenizer.tokenize();
		
		ComplexDslValidator validator = new ComplexDslValidator(registry);
		
		assertThatThrownBy(() -> validator.parseAndValidate(tokens))
			.isInstanceOf(SxlParseException.class)
			.hasMessageContaining("Unknown symbol")
			.hasMessageContaining("INVALID_SYMBOL");
	}

	@Test
	void embedWithMissingDslIdThrowsException() {
		String input = """
			(EMBED sxl-workflow
			  (WORKFLOW
			    (STEP load_data
			      (EMBED)
			    )
			  )
			)
			""";
		
		SxlTokenizer tokenizer = new SxlTokenizer(input);
		List<SxlToken> tokens = tokenizer.tokenize();
		
		ComplexDslValidator validator = new ComplexDslValidator(registry);
		
		assertThatThrownBy(() -> validator.parseAndValidate(tokens))
			.isInstanceOf(SxlParseException.class)
			.hasMessageContaining("EMBED requires at least one argument");
	}

	@Test
	void embedWithMissingPayloadThrowsException() {
		String input = """
			(EMBED sxl-workflow
			  (WORKFLOW
			    (STEP load_data
			      (EMBED sxl-sql)
			    )
			  )
			)
			""";
		
		SxlTokenizer tokenizer = new SxlTokenizer(input);
		List<SxlToken> tokens = tokenizer.tokenize();
		
		ComplexDslValidator validator = new ComplexDslValidator(registry);
		
		assertThatThrownBy(() -> validator.parseAndValidate(tokens))
			.isInstanceOf(SxlParseException.class)
			.hasMessageContaining("EMBED requires at least one payload node");
	}

	@Test
	void embedWithInvalidDslIdTypeThrowsException() {
		String input = """
			(EMBED sxl-workflow
			  (WORKFLOW
			    (STEP load_data
			      (EMBED "sxl-sql"
			        (Q (F table t) (S t.id))
			      )
			    )
			  )
			)
			""";
		
		SxlTokenizer tokenizer = new SxlTokenizer(input);
		List<SxlToken> tokens = tokenizer.tokenize();
		
		ComplexDslValidator validator = new ComplexDslValidator(registry);
		
		assertThatThrownBy(() -> validator.parseAndValidate(tokens))
			.isInstanceOf(SxlParseException.class)
			.hasMessageContaining("EMBED first argument must be a DSL identifier");
	}

	@Test
	void rootDslNotRegisteredThrowsException() {
		String input = "(EMBED unknown-dsl (WORKFLOW (STEP test)))";
		
		SxlTokenizer tokenizer = new SxlTokenizer(input);
		List<SxlToken> tokens = tokenizer.tokenize();
		
		ComplexDslValidator validator = new ComplexDslValidator(registry);
		
		assertThatThrownBy(() -> validator.parseAndValidate(tokens))
			.isInstanceOf(SxlParseException.class)
			.hasMessageContaining("unknown DSL")
			.hasMessageContaining("unknown-dsl");
	}

	// Helper methods to create test grammars

	private SxlGrammar createSqlGrammar() {
		// Simple SQL grammar with Q (query) symbol
		SymbolDefinition qSymbol = new SymbolDefinition(
			"Query",
			SymbolKind.node,
			List.of(
				new ParameterDefinition("from", "FROM clause", "node", List.of("F"), Cardinality.optional, true, null),
				new ParameterDefinition("select", "SELECT clause", "node", List.of("S"), Cardinality.required, true, null)
			),
			List.of(),
			List.of()
		);
		
		SymbolDefinition fSymbol = new SymbolDefinition(
			"FROM clause",
			SymbolKind.node,
			List.of(
				new ParameterDefinition("table", "Table name", "identifier", List.of(), Cardinality.required, true, null),
				new ParameterDefinition("alias", "Table alias", "identifier", List.of(), Cardinality.required, true, null)
			),
			List.of(),
			List.of()
		);
		
		SymbolDefinition sSymbol = new SymbolDefinition(
			"SELECT clause",
			SymbolKind.node,
			List.of(
				new ParameterDefinition("items", "Select items", "node", List.of("AS"), Cardinality.oneOrMore, true, null)
			),
			List.of(),
			List.of()
		);
		
		SymbolDefinition asSymbol = new SymbolDefinition(
			"AS clause",
			SymbolKind.node,
			List.of(
				new ParameterDefinition("expr", "Expression", "any", List.of(), Cardinality.required, true, null),
				new ParameterDefinition("alias", "Alias name", "identifier", List.of(), Cardinality.required, true, null)
			),
			List.of(),
			List.of()
		);
		
		return new SxlGrammar(
			"1.2",
			new DslMetadata("sxl-sql", "SQL DSL", "1.0"),
			Map.of("Q", qSymbol, "F", fSymbol, "S", sSymbol, "AS", asSymbol),
			new LiteralDefinitions(null, null, null, null),
			new IdentifierRule("Identifier", "^[a-z_][a-z0-9_.]*$"),
			List.of(),
			null,
			List.of(),
			null
		);
	}

	private SxlGrammar createWorkflowGrammar() {
		// Workflow grammar with WORKFLOW and STEP symbols
		// STEP can contain EMBED nodes
		SymbolDefinition workflowSymbol = new SymbolDefinition(
			"Workflow",
			SymbolKind.node,
			List.of(
				new ParameterDefinition("steps", "Workflow steps", "node", List.of("STEP"), Cardinality.oneOrMore, true, null)
			),
			List.of(),
			List.of()
		);
		
		SymbolDefinition stepSymbol = new SymbolDefinition(
			"Workflow step",
			SymbolKind.node,
			List.of(
				new ParameterDefinition("name", "Step name", "identifier", List.of(), Cardinality.required, true, null),
				new ParameterDefinition("body", "Step body", "node", List.of("EMBED"), Cardinality.required, true, null)
			),
			List.of(),
			List.of()
		);
		
		return new SxlGrammar(
			"1.2",
			new DslMetadata("sxl-workflow", "Workflow DSL", "1.0"),
			Map.of("WORKFLOW", workflowSymbol, "STEP", stepSymbol),
			new LiteralDefinitions(null, null, null, null),
			new IdentifierRule("Identifier", "^[a-z_][a-z0-9_]*$"),
			List.of(),
			null,
			List.of(),
			null
		);
	}
}

