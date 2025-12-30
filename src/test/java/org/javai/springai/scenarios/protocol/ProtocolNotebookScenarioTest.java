package org.javai.springai.scenarios.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.Objects;
import org.javai.springai.actions.conversation.ConversationManager;
import org.javai.springai.actions.conversation.ConversationTurnResult;
import org.javai.springai.actions.conversation.InMemoryConversationStateStore;
import org.javai.springai.actions.exec.DefaultPlanExecutor;
import org.javai.springai.actions.exec.PlanExecutionResult;
import org.javai.springai.actions.internal.instrument.InMemoryTokenStore;
import org.javai.springai.actions.internal.instrument.InvocationEmitter;
import org.javai.springai.actions.internal.instrument.InvocationEvent;
import org.javai.springai.actions.internal.instrument.InvocationEventType;
import org.javai.springai.actions.internal.instrument.InvocationKind;
import org.javai.springai.actions.internal.instrument.PayloadAugmentor;
import org.javai.springai.actions.internal.instrument.PiiTokenizingAugmentor;
import org.javai.springai.actions.internal.instrument.TokenStore;
import org.javai.springai.actions.plan.Plan;
import org.javai.springai.actions.plan.PlanStatus;
import org.javai.springai.actions.plan.Planner;
import org.javai.springai.actions.prompt.PersonaSpec;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

public class ProtocolNotebookScenarioTest {

	private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");
	private static final boolean RUN_LLM_TESTS = "true".equalsIgnoreCase(System.getenv("RUN_LLM_TESTS"));

	Planner planner;
	DefaultPlanExecutor executor;
	ConversationManager conversationManager;
	ProtocolNotebookActions protocolNotebookActions;
	ProtocolCatalogTool protocolCatalogTool;
	TestInvocationListener invocationListener;
	TokenStore tokenStore;
	InvocationEmitter emitter;

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

		protocolNotebookActions = new ProtocolNotebookActions();
		invocationListener = new TestInvocationListener();
		tokenStore = new InMemoryTokenStore();
		PayloadAugmentor augmentor = new PiiTokenizingAugmentor(tokenStore);
		emitter = InvocationEmitter.of("protocol-notebook-session", invocationListener);
		protocolCatalogTool = new ProtocolCatalogTool(emitter, augmentor);

		ChatClient chatClient = ChatClient.builder(Objects.requireNonNull(chatModel))
				.defaultOptions(Objects.requireNonNull(options))
				.build();

		PersonaSpec notebookDesignerPersona = PersonaSpec.builder()
				.name("ProtocolNotebookDesigner")
				.role("Planner for quality assurance notebook creation based on FDX statistical protocols")
				.principles(List.of(
						"ALWAYS call getProtocol to read the full protocol content before creating a plan.",
						"Match each protocol step to the action whose description best matches.",
						"Actions with 'Legacy' in the name are ONLY for the Legacy FDX v1 protocol."))
				.constraints(List.of(
						"Do not invent protocols or bundle IDs; use available tools to resolve ambiguities.",
						"Do NOT include legacy or experimental actions when following FDX 2024 standard protocol."))
				.build();

		planner = Planner.builder()
				.withChatClient(chatClient)
				.persona(notebookDesignerPersona)
				.tools(protocolCatalogTool)
				.actions(protocolNotebookActions)
				.build();
		executor = new DefaultPlanExecutor(emitter);
		conversationManager = new ConversationManager(planner, new InMemoryConversationStateStore());
	}

	@Test
	void generatesProtocolNotebookPlan() {
		String request = """
				Follow the standard FDX quality protocol for bushing displacement
				using bundle A12345. Produce a Marimo notebook with interactive
				charts and warehouse-backed dataframes.
				""";

		ConversationTurnResult turn = conversationManager.converse(request, "protocol-notebook-session");

		assertThat(protocolCatalogTool.listInvoked()).isTrue(); // A tool call was made
		assertThat(protocolCatalogTool.getInvoked()).isTrue(); // Protocol content was retrieved
		assertThat(protocolNotebookActions.invoked()).isFalse(); // Actions not called at this point

		Plan plan = turn.plan();

		plan.planSteps().forEach(step -> System.out.println(step.toString()));
		assertThat(plan).isNotNull();
		assertThat(plan.status()).isEqualTo(PlanStatus.READY);
		PlanExecutionResult executed = executor.execute(plan);
		executed.steps().forEach(step -> System.out.println(step.toString()));
		assertThat(executed.success()).isTrue();
		assertThat(protocolNotebookActions.invoked()).isTrue(); // Now an action was called

		assertThat(invocationListener.events).isNotEmpty();
		assertThat(invocationListener.events.stream().map(InvocationEvent::kind).distinct().toList())
				.contains(InvocationKind.ACTION)  // TEMPORARILY: Only checking ACTION events, not TOOL events
				.contains(InvocationKind.TOOL, InvocationKind.ACTION);
		assertThat(invocationListener.events.stream().map(InvocationEvent::type).toList())
				.contains(InvocationEventType.REQUESTED, InvocationEventType.SUCCEEDED);
		assertThat(protocolCatalogTool.lastMetadata()).isNotNull();
		assertThat(protocolCatalogTool.lastMetadata()).containsKey("piiTokens");

		var ctx = executed.context();
		assertThat(ctx).isNotNull();
		assertThat(ctx.contains("notebook")).isTrue();
		Notebook notebook = ctx.get("notebook", Notebook.class);
		assertThat(notebook).isNotNull();
		String content = notebook.content();
		assertThat(content).contains(
				"## Normality test",
				"## SPC readiness",
				"## Control chart"
		);
		assertThat(content).doesNotContain(
				"## Normality spot-check",
				"## Minimal SPC readiness checklist",
				"## Provisional thresholds",
				"## Lab-only data filter",
				"## Experimental distribution fit and residual analysis",
				"## Exploratory variance chart"
		);
	}
}
