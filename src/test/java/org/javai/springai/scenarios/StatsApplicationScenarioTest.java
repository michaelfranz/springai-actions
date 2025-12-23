package org.javai.springai.scenarios;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.Objects;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionParam;
import org.javai.springai.actions.tuning.LlmTuningConfig;
import org.javai.springai.actions.tuning.PlanSupplier;
import org.javai.springai.actions.tuning.ScenarioPlanSupplier;
import org.javai.springai.dsl.exec.DefaultPlanResolver;
import org.javai.springai.dsl.exec.PlanResolutionResult;
import org.javai.springai.dsl.exec.PlanResolver;
import org.javai.springai.dsl.plan.Plan;
import org.javai.springai.dsl.plan.PlanExecutionResult;
import org.javai.springai.dsl.plan.PlanStep;
import org.javai.springai.dsl.plan.Planner;
import org.javai.springai.sxl.grammar.SxlGrammar;
import org.javai.springai.sxl.grammar.SxlGrammarRegistry;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

public class StatsApplicationScenarioTest implements ScenarioPlanSupplier {

	private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");
	private static final boolean RUN_LLM_TESTS = "true".equalsIgnoreCase(System.getenv("RUN_LLM_TESTS"));

	private static final LlmTuningConfig BASELINE_CONFIG = new LlmTuningConfig(
			"You are an expert in dad jokes. Keep the humor light-hearted.",
			0.1,
			1.0
	);
	private static SxlGrammarRegistry grammarRegistry;
	private static SxlGrammar universalGrammar;
	private static SxlGrammar planGrammar;

	Planner planner;


	@BeforeAll
	static void setUpBeforeClass() throws Exception {
		ClassLoader loader = SxlGrammar.class.getClassLoader();
		grammarRegistry = SxlGrammarRegistry.create();
		universalGrammar = grammarRegistry.registerResource("sxl-meta-grammar-universal.yml", loader);
		planGrammar = grammarRegistry.registerResource("sxl-meta-grammar-plan.yml", loader);
	}

	@BeforeEach
	void setUp() {
		Assumptions.assumeTrue(RUN_LLM_TESTS, "Set RUN_LLM_TESTS=true to enable LLM integration tests");
		Assumptions.assumeTrue(OPENAI_API_KEY != null && !OPENAI_API_KEY.isBlank(),
				"OPENAI_API_KEY must be set for this integration test");

		OpenAiApi openAiApi = OpenAiApi.builder().apiKey(OPENAI_API_KEY).build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder().openAiApi(openAiApi).build();
		OpenAiChatOptions options = OpenAiChatOptions.builder()
				.model("gpt-4.1-mini")
				.temperature(0.1)
				.topP(1.0)
				.build();
		ChatClient chatClient = ChatClient.builder(Objects.requireNonNull(chatModel))
				.defaultOptions(Objects.requireNonNull(options))
				.build();

		planner = Planner.builder()
				.withChatClient(chatClient)
				.addGrammar(universalGrammar)
				.addGrammar(planGrammar)
				.addActions(new StatsActions())
				.build();
	}

	@Test
	void displayControlChartPlanTest() {
		PlanExecutionResult planResult = planner.planWithDetails("show me a control chart for displacement values in elasticity bundle A12345");
		Plan plan = planResult.plan();

		assertThat(plan).isNotNull();
		List<PlanStep> steps = plan.planSteps();
		assertThat(steps).hasSize(1);
		PlanStep step = steps.getFirst();
		assertThat(step).isInstanceOf(PlanStep.ActionStep.class);
		PlanStep.ActionStep action = (PlanStep.ActionStep) step;
		assertThat(action.actionId()).isEqualTo("displayControlChart");

		PlanResolver planResolver = new DefaultPlanResolver();
		PlanResolutionResult resolve = planResolver.resolve(plan, planResult.actionRegistry());
		assertThat(resolve.isSuccess()).isTrue();
	}

	@Test
	void exportToExcelTest() {
		PlanExecutionResult planResult = planner.planWithDetails("export a control chart to excel for displacement values in elasticity bundle A12345");
		Plan plan = planResult.plan();
		assertThat(plan).isNotNull();

		assertThat(plan).isNotNull();
		List<PlanStep> steps = plan.planSteps();
		assertThat(steps).hasSize(1);
		PlanStep step = steps.getFirst();
		assertThat(step).isInstanceOf(PlanStep.ActionStep.class);
		PlanStep.ActionStep action = (PlanStep.ActionStep) step;
		assertThat(action.actionId()).isEqualTo("exportControlChartToExcel");

		PlanResolver planResolver = new DefaultPlanResolver();
		PlanResolutionResult resolve = planResolver.resolve(plan, planResult.actionRegistry());
		assertThat(resolve.isSuccess()).isTrue();
	}

	@Test
	void evaluateSpcReadinessTest() {
		PlanExecutionResult planResult = planner.planWithDetails("evaluate spc readiness for displacement values in bundle A12345");
		Plan plan = planResult.plan();
		assertThat(plan).isNotNull();

		assertThat(plan).isNotNull();
		List<PlanStep> steps = plan.planSteps();
		assertThat(steps).hasSize(1);
		PlanStep step = steps.getFirst();
		assertThat(step).isInstanceOf(PlanStep.ActionStep.class);
		PlanStep.ActionStep action = (PlanStep.ActionStep) step;
		assertThat(action.actionId()).isEqualTo("evaluateSpcReadiness");

		PlanResolver planResolver = new DefaultPlanResolver();
		PlanResolutionResult resolve = planResolver.resolve(plan, planResult.actionRegistry());
		assertThat(resolve.isSuccess()).isTrue();
	}

	@Test
	void unableToIdentifyActionTest() {
		// No action supports ANOVA
		PlanExecutionResult planResult = planner.planWithDetails("perform a 2-way ANOVA on vehicle elasticity for bundle A12345");
		Plan plan = planResult.plan();
		assertThat(plan).isNotNull();

		assertThat(plan).isNotNull();
		List<PlanStep> steps = plan.planSteps();
		assertThat(steps).hasSize(1);
		PlanStep step = steps.getFirst();
		assertThat(step).isInstanceOf(PlanStep.ErrorStep.class);
		PlanStep.ErrorStep error = (PlanStep.ErrorStep) step;
		assertThat(error.assistantMessage()).isNotNull().isNotBlank();

		PlanResolver planResolver = new DefaultPlanResolver();
		PlanResolutionResult resolve = planResolver.resolve(plan, planResult.actionRegistry());
		assertThat(resolve.isSuccess()).isTrue();
	}

	@Test
	void requireMoreInformationTest() {
		// Export to excel requires bundle ID
		PlanExecutionResult planResult = planner.planWithDetails("export a control chart to excel for displacement values");
		Plan plan = planResult.plan();
		assertThat(plan).isNotNull();

		assertThat(plan).isNotNull();
		List<PlanStep> steps = plan.planSteps();
		assertThat(steps).hasSize(1);
		PlanStep step = steps.getFirst();
		assertThat(step).isInstanceOf(PlanStep.PendingActionStep.class);
		PlanStep.PendingActionStep pending = (PlanStep.PendingActionStep) step;
		PlanStep.PendingParam[] pendingParams = pending.pendingParams();
		assertThat(pendingParams.length).isGreaterThan(0);
		for (PlanStep.PendingParam param : pendingParams) {
			assertThat(param.name()).isIn("bundleId", "domainEntity");
		}
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

	public static class StatsActions {
		@Action(description = """
				Use the user's input to derive the parameters necessary for the application to compute and display a
				control chart. Don't try to create or compute a control chart. Just provide the parameters.""")
		public void displayControlChart(
				@ActionParam(description = "Entity or component being measured e.g. bushing, screw, piston") String domainEntity,
				@ActionParam(description = "The measurement concept to be charted e.g. force, displacement") String measurementConcept,
				@ActionParam(description = "Bundle ID") String bundleId) {
		}

		@Action(description = """
				Use the user's input to derive the parameters necessary for the application to compute and export a
				control chart to Excel. Don't try to create or compute a control chart. Just provide the parameters.""")
		public void exportControlChartToExcel(
				@ActionParam(description = "Entity or component being measured e.g. bushing, screw, piston") String domainEntity,
				@ActionParam(description = "The measurement concept to be charted e.g. force, displacement") String measurementConcept,
				@ActionParam(description = "Bundle ID") String bundleId) {
		}

		@Action(description = """
				Use the user's input to derive the parameters necessary for the application to evaluate SPC readiness.
				 Don't try to compute SPC readiness. Just provide the parameters.""")
		public void evaluateSpcReadiness(
				@ActionParam(description = "The measurement concept to be charted e.g. force, displacement") String measurementConcept,
				@ActionParam(description = "Bundle ID") String bundleId) {
		}

	}
}
