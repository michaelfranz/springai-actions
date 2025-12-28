package org.javai.springai.scenarios.stats_app;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.Objects;
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
import org.javai.springai.dsl.prompt.PersonaSpec;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

public class StatsApplicationScenarioTest {

	private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");
	private static final boolean RUN_LLM_TESTS = "true".equalsIgnoreCase(System.getenv("RUN_LLM_TESTS"));

	Planner planner;
	PlanResolver resolver;
	DefaultPlanExecutor executor;
	ConversationManager conversationManager;
	StatsActions statsActions;


	@BeforeEach
	void setUp() {
		Assumptions.assumeTrue(RUN_LLM_TESTS, "Set RUN_LLM_TESTS=true to enable LLM integration tests");
		Assumptions.assumeTrue(OPENAI_API_KEY != null && !OPENAI_API_KEY.isBlank(),
				"OPENAI_API_KEY must be set for this integration test");

	OpenAiApi openAiApi = OpenAiApi.builder().apiKey(OPENAI_API_KEY).build();
	OpenAiChatModel chatModel = OpenAiChatModel.builder().openAiApi(openAiApi).build();
	OpenAiChatOptions options = OpenAiChatOptions.builder()
			.model("gpt-4.1-mini")
			.temperature(0.0)
			.topP(1.0)
			.build();
		ChatClient chatClient = ChatClient.builder(Objects.requireNonNull(chatModel))
				.defaultOptions(Objects.requireNonNull(options))
				.build();

	statsActions = new StatsActions();

	PersonaSpec spcAssistantPersona = PersonaSpec.builder()
			.name("StatisticalProcessControlAssistant")
			.role("Assistant for statistical process control analysis and charting")
			.principles(List.of(
					"Understand SPC concepts: control charts, readiness evaluation",
					"Map user requests to appropriate actions based on measurement parameters",
					"Extract measurement concept and bundle ID from user requests",
					"CRITICAL: If any required parameter is missing or unclear, use PENDING instead of guessing"))
			.constraints(List.of(
					"Required parameters: measurementConcept (e.g., 'displacement', 'force'), bundleId (e.g., 'A12345')",
					"NEVER invent or guess values; if bundleId is not provided, emit (PENDING bundleId \"what is the bundle ID?\") instead",
					"Only use available actions: displayControlChart, exportControlChartToExcel, evaluateSpcReadiness"))
			.styleGuidance(List.of(
					"Emit S-expression plans only, no prose",
					"Example PENDING: (P \"desc\" (PS action (PENDING paramName \"what is param?\") (PA other \"value\")))",
					"Use PENDING for any required parameter not clearly provided by user"))
			.build();

	planner = Planner.builder()
			.withChatClient(chatClient)
			.persona(spcAssistantPersona)
			.actions(statsActions)
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
		String request = "export a displacement control chart to excel for bundle A12345";
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
		assertThat(resolvedPlan.status()).isEqualTo(PlanStatus.PENDING);
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
		assertThat(firstResolved.status()).isEqualTo(PlanStatus.PENDING);
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
}

