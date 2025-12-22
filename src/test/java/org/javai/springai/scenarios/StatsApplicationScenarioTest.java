package org.javai.springai.scenarios;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.tuning.LlmTuningConfig;
import org.javai.springai.actions.tuning.PlanSupplier;
import org.javai.springai.actions.tuning.ScenarioPlanSupplier;
import org.javai.springai.dsl.plan.Plan;
import org.javai.springai.dsl.plan.PlanStep;
import org.javai.springai.dsl.plan.Planner;
import org.javai.springai.sxl.grammar.SxlGrammar;
import org.javai.springai.sxl.grammar.SxlGrammarRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class StatsApplicationScenarioTest implements ScenarioPlanSupplier {

	private static final LlmTuningConfig BASELINE_CONFIG = new LlmTuningConfig(
			"You are an expert in dad jokes. Keep the humor light-hearted.",
			0.1,
			1.0
	);
	private static SxlGrammarRegistry grammarRegistry;
	private static SxlGrammar sqlGrammar;
	private static SxlGrammar planGrammar;

	Planner planner;


	@BeforeAll
	static void setUpBeforeClass() throws Exception {
		ClassLoader loader = SxlGrammar.class.getClassLoader();
		grammarRegistry = SxlGrammarRegistry.create();
		sqlGrammar = grammarRegistry.registerResource("sxl-meta-grammar-sql.yml", loader);
		planGrammar = grammarRegistry.registerResource("sxl-meta-grammar-plan.yml", loader);
	}

	@BeforeEach
	void setUp() {
		planner = Planner.builder()
				.withModel("gpt-4.1-mini")
				.withTemperature(0.1)
				.withTopP(1.0)
				.addGrammar(planGrammar)
				.addGrammar(sqlGrammar)
				.addPromptContribution(
						"""
								You are an application assistent that converts natural language instructions into actions\
								that an application can perform.""")
				.addActions(this)
				.build();
	}

	@Test
	void displayControlChartPlanTest() {
		Plan plan = planner.planActions("show me a control chart for displacement values in elasticity bundle A12345");

		assertThat(plan).isNotNull();
		List<PlanStep> steps = plan.planSteps();
		assertThat(steps).hasSize(1);
		PlanStep step = steps.getFirst();
		assertThat(step).isInstanceOf(PlanStep.Action.class);
		PlanStep.Action action = (PlanStep.Action) step;
		assertThat(action.actionId()).isEqualTo("displayControlChart");
	}

	@Test
	void exportToExcelTest() {
		Plan plan = planner.planActions("export elasticity bundle A12345 to excel");
		assertThat(plan).isNotNull();
	}

	@Test
	void evaluateSpcReadinessTest() {
		Plan plan = planner.planActions("evaluate spc readiness for bundle A12345");
		assertThat(plan).isNotNull();
	}


	@Action
	public void displayControlChart(String bundleId) {

	}

	@Action
	public void exportToExcel(String bundleId) {

	}

	@Action
	public void evaluateSpcReadiness(String bundleId) {

	}


	@Override
	public String scenarioId() {
		return "stats-application";
	}

	@Override
	public String description() {
		return "Simulates the kind of DSL that one might find in an application with statistical features";
	}

	@Override
	public LlmTuningConfig defaultConfig() {
		return BASELINE_CONFIG;
	}

	@Override
	public PlanSupplier planSupplier(LlmTuningConfig config) {
		return () -> null;
	}
}
