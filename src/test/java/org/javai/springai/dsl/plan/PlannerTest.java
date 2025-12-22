package org.javai.springai.dsl.plan;

import static org.assertj.core.api.Assertions.assertThat;

import org.javai.springai.actions.api.Action;
import org.javai.springai.sxl.grammar.SxlGrammar;
import org.javai.springai.sxl.grammar.SxlGrammarParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
				.anySatisfy(msg -> assertThat(msg).contains("Action id: demoAction"));
		assertThat(preview.userMessages()).contains("do something");
		assertThat(preview.grammarIds()).contains("sxl-plan");
		assertThat(preview.actionNames()).contains("demoAction");
	}

	@Test
	void dryRunReturnsEmptyPlanAndPreview() {
		Planner planner = Planner.builder()
				.addGrammar(planGrammar)
				.addActions(new DemoActions())
				.build();

		PlanExecutionResult result = planner.planWithDetails("dry run request", PlannerOptions.dryRunOptions());

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
}

