package org.javai.springai.scenarios;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionParam;
import org.javai.springai.actions.tuning.LlmTuningConfig;
import org.javai.springai.actions.tuning.PlanSupplier;
import org.javai.springai.actions.tuning.ScenarioPlanSupplier;
import org.javai.springai.dsl.conversation.ConversationManager;
import org.javai.springai.dsl.conversation.ConversationTurnResult;
import org.javai.springai.dsl.conversation.InMemoryConversationStateStore;
import org.javai.springai.dsl.exec.DefaultPlanExecutor;
import org.javai.springai.dsl.exec.DefaultPlanResolver;
import org.javai.springai.dsl.exec.PlanExecutionResult;
import org.javai.springai.dsl.exec.PlanResolver;
import org.javai.springai.dsl.exec.ResolvedPlan;
import org.javai.springai.dsl.exec.ResolvedStep;
import org.javai.springai.dsl.plan.PlanStatus;
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
	PlanResolver resolver;
	DefaultPlanExecutor executor;
	ConversationManager conversationManager;
	StatsActions statsActions;


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

		statsActions = new StatsActions();

		planner = Planner.builder()
				.withChatClient(chatClient)
				.addGrammar(universalGrammar)
				.addGrammar(planGrammar)
				.addActions(statsActions)
				.build();
		resolver = new DefaultPlanResolver();
		executor = new DefaultPlanExecutor();
		conversationManager = new ConversationManager(planner, resolver, new InMemoryConversationStateStore());
	}

	@Test
	void displayControlChartPlanTest() {
		String request = "show me a control chart for displacement values in elasticity bundle A12345";
		ConversationTurnResult turn = conversationManager.converse(request, "display-session");
		ResolvedPlan resolvedPlan = turn.resolvedPlan();

		assertThat(resolvedPlan).isNotNull();
		assertThat(resolvedPlan.status()).isEqualTo(PlanStatus.READY);
		assertThat(resolvedPlan.steps()).hasSize(1);
		ResolvedStep step = resolvedPlan.steps().getFirst();
		assertThat(step).isInstanceOf(ResolvedStep.ActionStep.class);

		PlanExecutionResult executed = executor.execute(resolvedPlan);
		assertThat(executed.success()).isTrue();
		assertThat(statsActions.displayControlChartInvoked()).isTrue();
	}

	@Test
	void exportToExcelTest() {
		String request = "export a control chart to excel for bushing displacement values in elasticity bundle A12345";
		ConversationTurnResult turn = conversationManager.converse(request, "export-session");
		ResolvedPlan resolvedPlan = turn.resolvedPlan();
		assertThat(resolvedPlan).isNotNull();
		assertThat(resolvedPlan.status()).isEqualTo(PlanStatus.READY);
		assertThat(resolvedPlan.steps()).hasSize(1);
		ResolvedStep step = resolvedPlan.steps().getFirst();
		assertThat(step).isInstanceOf(ResolvedStep.ActionStep.class);

		PlanExecutionResult executed = executor.execute(resolvedPlan);
		assertThat(executed.success()).isTrue();
		assertThat(statsActions.exportControlChartToExcelInvoked()).isTrue();
	}

	@Test
	void evaluateSpcReadinessTest() {
		String request = "evaluate spc readiness for displacement values in bundle A12345";
		ConversationTurnResult turn = conversationManager.converse(request, "spc-session");
		ResolvedPlan resolvedPlan = turn.resolvedPlan();
		assertThat(resolvedPlan).isNotNull();
		assertThat(resolvedPlan.status()).isEqualTo(PlanStatus.READY);
		assertThat(resolvedPlan.steps()).hasSize(1);

		PlanExecutionResult executed = executor.execute(resolvedPlan);
		assertThat(executed.success()).isTrue();
		assertThat(statsActions.evaluateSpcReadinessInvoked()).isTrue();
	}

	@Test
	void unableToIdentifyActionTest() {
		// No action supports ANOVA
		String request = "perform a 2-way ANOVA on vehicle elasticity for bundle A12345";
		ConversationTurnResult turn = conversationManager.converse(request, "anova-session");
		ResolvedPlan resolvedPlan = turn.resolvedPlan();
		assertThat(resolvedPlan).isNotNull();
		assertThat(resolvedPlan.status()).isEqualTo(PlanStatus.ERROR);
		assertThat(resolvedPlan.steps()).hasSize(1);
		ResolvedStep step = resolvedPlan.steps().getFirst();
		assertThat(step).isInstanceOf(ResolvedStep.ErrorStep.class);
	}

	@Test
	void requireMoreInformationTest() {
		// Export to excel requires bundle ID
		String request = "export a control chart to excel for displacement values";
		ConversationTurnResult turn = conversationManager.converse(request, "pending-session");
		ResolvedPlan resolvedPlan = turn.resolvedPlan();
		assertThat(resolvedPlan).isNotNull();
		assertThat(resolvedPlan.status()).isEqualTo(PlanStatus.ERROR);
		assertThat(turn.pendingParams()).isNotEmpty();
		assertThat(turn.pendingParams().stream().map(PlanStep.PendingParam::name))
				.anyMatch(name -> name.equals("bundleId") || name.equals("domainEntity"));
	}

	@Test
	void requireMoreInformationFollowUpProvidesMissingBundleId() {
		String sessionId = "stats-session";

		// Turn 1: missing bundle id -> expect pending
		ConversationTurnResult firstTurn = conversationManager
				.converse("export a control chart to excel for displacement values", sessionId);
		ResolvedPlan firstResolved = firstTurn.resolvedPlan();
		assertThat(firstResolved).isNotNull();
		assertThat(firstResolved.status()).isEqualTo(PlanStatus.ERROR);
		assertThat(firstTurn.pendingParams()).isNotEmpty();

		// Turn 2: user supplies only the missing info; desired behavior is that
		// the system merges context and produces an executable step (documented scenario)
		ConversationTurnResult secondTurn = conversationManager
				.converse("the bundle id is A12345", sessionId);
		ResolvedPlan secondPlan = secondTurn.resolvedPlan();
		assertThat(secondPlan).isNotNull();
		// Ideal outcome after context merge: actionable step, no pending
		assertThat(secondPlan.steps().getFirst()).isInstanceOf(ResolvedStep.ActionStep.class);

		PlanExecutionResult executed = executor.execute(secondPlan);
		assertThat(executed.success()).isTrue();
		assertThat(statsActions.exportControlChartToExcelInvoked()).isTrue();
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
		private final AtomicBoolean displayControlChartInvoked = new AtomicBoolean(false);
		private final AtomicBoolean exportControlChartToExcelInvoked = new AtomicBoolean(false);
		private final AtomicBoolean evaluateSpcReadinessInvoked = new AtomicBoolean(false);

		@Action(description = """
				Use the user's input to derive the parameters necessary for the application to compute and display a
				control chart. Don't try to create or compute a control chart. Just provide the parameters.""")
		public void displayControlChart(
				@ActionParam(description = "The measurement concept to be charted e.g. force, displacement") String measurementConcept,
				@ActionParam(description = "Bundle ID") String bundleId) {
			displayControlChartInvoked.set(true);
		}

		@Action(description = """
				Use the user's input to derive the parameters necessary for the application to compute and export a
				control chart to Excel. Don't try to create or compute a control chart. Just provide the parameters.""")
		public void exportControlChartToExcel(
				@ActionParam(description = "The measurement concept to be charted e.g. force, displacement") String measurementConcept,
				@ActionParam(description = "Bundle ID") String bundleId) {
			exportControlChartToExcelInvoked.set(true);
		}

		@Action(description = """
				Use the user's input to derive the parameters necessary for the application to evaluate SPC readiness.
				 Don't try to compute SPC readiness. Just provide the parameters.""")
		public void evaluateSpcReadiness(
				@ActionParam(description = "The measurement concept to be charted e.g. force, displacement") String measurementConcept,
				@ActionParam(description = "Bundle ID") String bundleId) {
			evaluateSpcReadinessInvoked.set(true);
		}

		boolean displayControlChartInvoked() {
			return displayControlChartInvoked.get();
		}

		boolean exportControlChartToExcelInvoked() {
			return exportControlChartToExcelInvoked.get();
		}

		boolean evaluateSpcReadinessInvoked() {
			return evaluateSpcReadinessInvoked.get();
		}

	}
}
