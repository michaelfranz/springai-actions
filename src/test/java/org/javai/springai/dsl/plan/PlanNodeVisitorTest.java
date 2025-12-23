package org.javai.springai.dsl.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import org.javai.springai.dsl.bind.TypeFactoryBootstrap;
import org.javai.springai.dsl.sql.Query;
import org.javai.springai.sxl.DefaultValidatorRegistry;
import org.javai.springai.sxl.DslParsingStrategy;
import org.javai.springai.sxl.ParsingStrategy;
import org.javai.springai.sxl.SxlNode;
import org.javai.springai.sxl.SxlParseException;
import org.javai.springai.sxl.SxlParser;
import org.javai.springai.sxl.SxlToken;
import org.javai.springai.sxl.SxlTokenizer;
import org.javai.springai.sxl.grammar.SxlGrammar;
import org.javai.springai.sxl.grammar.SxlGrammarParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlanNodeVisitorTest {

	private static SxlGrammar planGrammar;
	private static SxlGrammar sqlGrammar;

	@BeforeAll
	static void setUpBeforeClass() {
		SxlGrammarParser grammarParser = new SxlGrammarParser();
		planGrammar = grammarParser.parse(SxlGrammar.class.getClassLoader().getResourceAsStream("sxl-meta-grammar-plan.yml"));
		sqlGrammar = grammarParser.parse(SxlGrammar.class.getClassLoader().getResourceAsStream("sxl-meta-grammar-sql.yml"));
		TypeFactoryBootstrap.registerBuiltIns();
	}


	@Test
	void generateMinimalPlan() {
		String zeroStepPlan = """
				(P "Minimal plan")
				""";
		SxlNode planNode = parse(zeroStepPlan);
		Plan plan = PlanNodeVisitor.generate(planNode);
		assertThat(plan).isNotNull();
		assertThat(plan.assistantMessage()).isEqualTo("Minimal plan");
		assertThat(plan.planSteps()).isEmpty();
	}

	@Test
	void generatePlanWithEmbeddedSql() {
		String planText = """
				(P "Query plan"
				  (PS fetchOrders (EMBED sxl-sql (Q (S 1))))
				)
				""";
		SxlNode planNode = parse(planText);
		Plan plan = PlanNodeVisitor.generate(planNode);
		assertThat(plan).isNotNull();
		assertThat(plan.assistantMessage()).isEqualTo("Query plan");
		assertThat(plan.planSteps()).hasSize(1);
		PlanStep ps1 = plan.planSteps().getFirst();
		assertThat(ps1).isInstanceOf(PlanStep.ActionStep.class);
		PlanStep.ActionStep a1 = (PlanStep.ActionStep) ps1;
		assertThat(a1.assistantMessage()).isEmpty();
		assertThat(a1.actionId()).isEqualTo("fetchOrders");
		assertThat(a1.actionArguments()).hasSize(1);
		assertThat(a1.actionArguments()[0]).isInstanceOf(Query.class);
	}

	@Test
	void generatePlanWithErrorStep() {
		String planText = """
				(P "Plan with error"
				  (ERROR "LLM timeout")
				)
				""";
		SxlNode planNode = parse(planText);
		Plan plan = PlanNodeVisitor.generate(planNode);
		assertThat(plan.planSteps()).hasSize(1);
		assertThat(plan.planSteps().getFirst()).isInstanceOf(PlanStep.ErrorStep.class);
		PlanStep.ErrorStep error = (PlanStep.ErrorStep) plan.planSteps().getFirst();
		assertThat(error.assistantMessage()).isEqualTo("LLM timeout");
	}

	@Test
	void generatePlanWithErrorSteps() {
		String planText = """
				(P "Plan with error"
				  (ERROR "LLM timeout")
				  (ERROR "Out of tokens")
				)
				""";
		SxlNode planNode = parse(planText);
		Plan plan = PlanNodeVisitor.generate(planNode);
		assertThat(plan.planSteps()).hasSize(2);
		assertThat(plan.planSteps().getFirst()).isInstanceOf(PlanStep.ErrorStep.class);
		PlanStep.ErrorStep error0 = (PlanStep.ErrorStep) plan.planSteps().getFirst();
		assertThat(error0.assistantMessage()).isEqualTo("LLM timeout");
		assertThat(plan.planSteps().get(1)).isInstanceOf(PlanStep.ErrorStep.class);
		PlanStep.ErrorStep error1 = (PlanStep.ErrorStep) plan.planSteps().get(1);
		assertThat(error1.assistantMessage()).isEqualTo("Out of tokens");
	}

	@Test
	void rejectsNonLiteralPlanDescription() {
		String planText = """
				(P (Q (S 1)))
				""";
		assertThatThrownBy(() -> parse(planText))
				.isInstanceOf(SxlParseException.class);
	}

	@Test
	void rejectsNonLiteralActionId() {
		String planText = """
				(P "desc"
				  (PS "notIdentifier" (EMBED sxl-sql (Q (S 1))))
				)
				""";
		assertThatThrownBy(() -> parse(planText))
				.isInstanceOf(SxlParseException.class);
	}

	@Test
	void rejectsUnknownEmbeddedDsl() {
		String planText = """
				(P "desc"
				  (PS actionId (EMBED sxl-unknown (Q (S 1))))
				)
				""";
		assertThatThrownBy(() -> parse(planText))
				.isInstanceOf(SxlParseException.class);
	}

	@Test
	void generatePlanWithIdentifierParams() {
		String planText = """
				(P "Param-only plan"
				  (PS displayControlChart
				    (PA domainEntity "elasticity bundle")
				    (PA measurementConcept "displacement")
				    (PA bundleId "A12345")
				  )
				)
				""";
		SxlNode planNode = parse(planText);
		Plan plan = PlanNodeVisitor.generate(planNode);
		assertThat(plan.planSteps()).hasSize(1);
		PlanStep.ActionStep step = (PlanStep.ActionStep) plan.planSteps().getFirst();
		assertThat(step.actionArguments()).containsExactly(
				"elasticity bundle",
				"displacement",
				"A12345"
		);
	}

	@Test
	void generatePlanWithMixedParamsAndEmbed() {
		String planText = """
				(P "Mixed plan"
				  (PS displayControlChart
				    (EMBED sxl-sql (Q (S 1)))
				    (PA bundleId "A12345")
				  )
				)
				""";
		SxlNode planNode = parse(planText);
		Plan plan = PlanNodeVisitor.generate(planNode);
		assertThat(plan.planSteps()).hasSize(1);
		PlanStep.ActionStep step = (PlanStep.ActionStep) plan.planSteps().getFirst();
		assertThat(step.actionArguments()).hasSize(2);
		assertThat(step.actionArguments()[1]).isEqualTo("A12345");
	}

	@Test
	void generatePlanWithPendingParameters() {
		String planText = """
				(P "Export requires missing info"
				  (PS exportControlChartToExcel
				    (PENDING bundleId "Provide bundle id to export control chart")
				    (PENDING measurementConcept "Measurement concept is invalid: ???")
				    (PA domainEntity "displacement")
				  )
				)
				""";

		SxlNode planNode = parse(planText);
		Plan plan = PlanNodeVisitor.generate(planNode);

		assertThat(plan.planSteps()).hasSize(1);
		Object pendingStep = plan.planSteps().getFirst();
		assertThat(pendingStep.getClass().getSimpleName()).isEqualTo("PendingActionStep");
	}

	private SxlNode parse(String sxl) {
		SxlTokenizer tokenizer = new SxlTokenizer(sxl);
		List<SxlToken> tokens = tokenizer.tokenize();
		DefaultValidatorRegistry validatorRegistry = new DefaultValidatorRegistry();
		validatorRegistry.addGrammar("sxl-plan", planGrammar);
		validatorRegistry.addGrammar("sxl-sql", sqlGrammar);
		ParsingStrategy parsingStrategy = new DslParsingStrategy(planGrammar, validatorRegistry);
		SxlParser parser = new SxlParser(tokens, parsingStrategy);
		List<SxlNode> nodes = parser.parse();
		return nodes.getFirst();
	}

}