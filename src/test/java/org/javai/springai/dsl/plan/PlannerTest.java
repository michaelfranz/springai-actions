package org.javai.springai.dsl.plan;

import static org.assertj.core.api.Assertions.assertThat;

import org.javai.springai.actions.api.Action;
import org.javai.springai.sxl.grammar.SxlGrammar;
import org.javai.springai.sxl.grammar.SxlGrammarParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.javai.springai.dsl.plan.PlanFormulationResult;

class PlannerTest {

	private SxlGrammar planGrammar;

	@BeforeEach
	void setup() {
		SxlGrammarParser parser = new SxlGrammarParser();
		planGrammar = parser.parse(
				PlannerTest.class.getClassLoader().getResourceAsStream("sxl-meta-grammar-plan.yml"));
	}

	@Test
	void previewIncludesGrammarAndActions() {
		Planner planner = Planner.builder()
				.addGrammar(planGrammar)
				.addPromptContribution("system-extra")
				.addActions(new DemoActions())
				.enablePromptCapture()
				.build();

		PromptPreview preview = planner.preview("do something");

		assertThat(preview.systemMessages())
				.anySatisfy(msg -> assertThat(msg).contains("DSL sxl-plan"));
		assertThat(preview.systemMessages())
				.anySatisfy(msg -> assertThat(msg).contains("demoAction"));
		assertThat(preview.userMessages()).contains("do something");
		assertThat(preview.grammarIds()).contains("sxl-plan");
		assertThat(preview.actionNames()).contains("demoAction");
	}

	@Test
	void systemPromptMatchesExpectedStructure() {
		Planner planner = Planner.builder()
				.addGrammar(planGrammar)
				.addPromptContribution("system-extra")
				.addActions(new DemoActions())
				.enablePromptCapture()
				.build();

		PromptPreview preview = planner.preview("do something");
		assertThat(preview.systemMessages()).hasSize(2);

		String system = normalize(preview.systemMessages().get(0));

		// Should combine DSL guidance + grammar summary + action catalog.
		assertThat(system).contains("DSL GUIDANCE");
		assertThat(system).contains("DSL sxl-plan");
		assertThat(system).contains("PLAN DSL root"); // guidance from llm_specs
		assertThat(system).contains("P") // grammar summary includes symbol names
				.contains("PS");
		assertThat(system).contains("ACTIONS");
		assertThat(system).contains("demoAction");

		assertThat(normalize(preview.systemMessages().get(1)))
				.isEqualTo("system-extra");
	}

	@Test
	void dryRunReturnsEmptyPlanAndPreview() {
		Planner planner = Planner.builder()
				.addGrammar(planGrammar)
				.addActions(new DemoActions())
				.build();

		PlanFormulationResult result = planner.planWithDetails("dry run request", PlannerOptions.dryRunOptions());

		Plan plan = result.plan();
		assertThat(result.dryRun()).isTrue();
		assertThat(plan).isNotNull();
		assertThat(plan.planSteps()).isEmpty();
		assertThat(result.promptPreview()).isNotNull();
		assertThat(result.promptPreview().userMessages()).contains("dry run request");
	}

	static class DemoActions {
		@Action
		public void demoAction(String input) {
			// no-op
		}
	}

	private static String normalize(String text) {
		return text == null ? "" : text.replaceAll("\\s+", " ").trim();
	}
}

