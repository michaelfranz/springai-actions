package org.javai.springai.scenarios;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionParam;
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
import org.javai.springai.dsl.plan.Planner;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.annotation.Tool;

public class ProtocolNotebookScenarioTest {

	private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");
	private static final boolean RUN_LLM_TESTS = "true".equalsIgnoreCase(System.getenv("RUN_LLM_TESTS"));

	Planner planner;
	PlanResolver resolver;
	DefaultPlanExecutor executor;
	ConversationManager conversationManager;
	ProtocolNotebookActions protocolNotebookActions;
	ProtocolCatalogTool protocolCatalogTool;

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

		protocolNotebookActions = new ProtocolNotebookActions();
		protocolCatalogTool = new ProtocolCatalogTool();

		planner = Planner.builder()
				.withChatClient(chatClient)
				.tools(protocolCatalogTool)
				.actions(protocolNotebookActions)
				.build();
		resolver = new DefaultPlanResolver();
		executor = new DefaultPlanExecutor();
		conversationManager = new ConversationManager(planner, resolver, new InMemoryConversationStateStore());
	}

	@Test
	void generatesProtocolNotebookPlan() {
		String request = """
				Follow the current FDX quality protocol for bushing displacement
				using bundle A12345. Produce a Marimo notebook with interactive
				charts and warehouse-backed dataframes.
				""";

		ConversationTurnResult turn = conversationManager.converse(request, "protocol-notebook-session");
		ResolvedPlan resolvedPlan = turn.resolvedPlan();

		assertThat(resolvedPlan).isNotNull();
		assertThat(resolvedPlan.status()).isEqualTo(PlanStatus.READY);
		assertThat(resolvedPlan.steps()).hasSize(1);
		ResolvedStep step = resolvedPlan.steps().getFirst();
		assertThat(step).isInstanceOf(ResolvedStep.ActionStep.class);

		PlanExecutionResult executed = executor.execute(resolvedPlan);
		assertThat(executed.success()).isTrue();
		assertThat(protocolNotebookActions.invoked()).isTrue();
		assertThat(protocolCatalogTool.listInvoked()).isTrue();

		ProtocolNotebookRequest lastRequest = protocolNotebookActions.lastRequest();
		assertThat(lastRequest).isNotNull();
		assertThat(lastRequest.protocolName()).containsIgnoringCase("fdx");
		assertThat(lastRequest.componentName()).containsIgnoringCase("bushing");
		assertThat(lastRequest.datasetId()).isEqualTo("A12345");
		assertThat(lastRequest.notebookTitle()).containsIgnoringCase("notebook");
		assertThat(lastRequest.protocolPath()).isEqualTo("/protocols/fdx-2024-standard.md");
	}

	public static class ProtocolNotebookActions {
		private final AtomicBoolean invoked = new AtomicBoolean(false);
		private ProtocolNotebookRequest lastRequest;

		@Action(description = """
				Create a plan to produce a Marimo notebook that follows a statistical quality protocol.
				Derive the required statistical checks, data pulls, and chart scaffolding from the protocol
				and component context. Do not run the tests or build the notebook; just provide the parameters
				so the runtime can execute them locally with data warehouse connectivity.""")
		public void buildProtocolNotebook(
				@ActionParam(description = "Protocol name or identifier") String protocolName,
				@ActionParam(description = "Protocol prose listing the required statistical checks") String protocolNarrative,
				@ActionParam(description = "The component or system under analysis") String componentName,
				@ActionParam(description = "Data warehouse bundle or dataset identifier", allowedRegex = "[A-Za-z0-9_-]+")
				String datasetId,
				@ActionParam(description = "Notebook title to render") String notebookTitle,
				@ActionParam(description = "Resolved protocol file path to execute") String protocolPath) {
			invoked.set(true);
			lastRequest = new ProtocolNotebookRequest(protocolName, protocolNarrative, componentName, datasetId,
					notebookTitle, protocolPath);
		}

		@Action(description = "Add normality test to notebook.")
		public void addNormalityTest(
				@ActionParam(description = "The type of component e.g. bushing, piston") String component,
				@ActionParam(description = "The type of measurement e.g. displacement, force") String measurememt,
				@ActionParam(description = "The ID of the data bundle containing the measurements") String bundleId) {
			// TODO normality test to notebook
		}

		@Action(description = "Add statistical readiness to notebook.")
		public void addSpcReadinessTest(
				@ActionParam(description = "The type of component e.g. bushing, piston") String component,
				@ActionParam(description = "The type of measurement e.g. displacement, force") String measurememt,
				@ActionParam(description = "The ID of the data bundle containing the measurements") String bundleId) {
			// TODO add SPC readiness test to notebook
		}

		@Action(description = "Add control chart to notebook.")
		public void addSpcControlChart(
				@ActionParam(description = "The type of component e.g. bushing, piston") String component,
				@ActionParam(description = "The type of measurement e.g. displacement, force") String measurememt,
				@ActionParam(description = "The ID of the data bundle containing the measurements") String bundleI) {
			// TODO add control chart to notebook
		}

		@Action(description = "Add normality spot-check from legacy protocol to notebook.")
		public void addLegacyNormalitySpotCheck(
				@ActionParam(description = "The type of component e.g. bushing, piston") String component,
				@ActionParam(description = "The type of measurement e.g. displacement, force") String measurement,
				@ActionParam(description = "The ID of the data bundle containing the measurements") String bundleId) {
			// TODO add legacy normality spot-check to notebook
		}

		@Action(description = "Add minimal SPC readiness checklist from legacy protocol to notebook.")
		public void addLegacySpcReadinessChecklist(
				@ActionParam(description = "The type of component e.g. bushing, piston") String component,
				@ActionParam(description = "The type of measurement e.g. displacement, force") String measurement,
				@ActionParam(description = "The ID of the data bundle containing the measurements") String bundleId) {
			// TODO add legacy SPC readiness checklist to notebook
		}

		@Action(description = "Add provisional control limits from legacy protocol to notebook.")
		public void addLegacyProvisionalControlLimits(
				@ActionParam(description = "The type of component e.g. bushing, piston") String component,
				@ActionParam(description = "The type of measurement e.g. displacement, force") String measurement,
				@ActionParam(description = "The ID of the data bundle containing the measurements") String bundleId) {
			// TODO add legacy provisional control limits to notebook
		}

		@Action(description = "Add lab-only data filter from experimental protocol to notebook.")
		public void addExperimentalLabOnlyFilter(
				@ActionParam(description = "The type of component e.g. bushing, piston") String component,
				@ActionParam(description = "The type of measurement e.g. displacement, force") String measurement,
				@ActionParam(description = "The ID of the data bundle containing the measurements") String bundleId) {
			// TODO add lab-only data filter to notebook
		}

		@Action(description = "Add experimental distribution fit and residual analysis to notebook.")
		public void addExperimentalDistributionFit(
				@ActionParam(description = "The type of component e.g. bushing, piston") String component,
				@ActionParam(description = "The type of measurement e.g. displacement, force") String measurement,
				@ActionParam(description = "The ID of the data bundle containing the measurements") String bundleId) {
			// TODO add experimental distribution fit and residual analysis to notebook
		}

		@Action(description = "Add exploratory control limits and lab-only variance chart to notebook.")
		public void addExperimentalControlLimits(
				@ActionParam(description = "The type of component e.g. bushing, piston") String component,
				@ActionParam(description = "The type of measurement e.g. displacement, force") String measurement,
				@ActionParam(description = "The ID of the data bundle containing the measurements") String bundleId) {
			// TODO add experimental control limits and variance chart to notebook
		}

		boolean invoked() {
			return invoked.get();
		}

		ProtocolNotebookRequest lastRequest() {
			return lastRequest;
		}
	}

	public record ProtocolNotebookRequest(String protocolName, String protocolNarrative, String componentName,
										  String datasetId, String notebookTitle, String protocolPath) {
	}

	public static class ProtocolCatalogTool {
		private final AtomicBoolean listInvoked = new AtomicBoolean(false);
		private final AtomicBoolean getInvoked = new AtomicBoolean(false);
		private String lastPath;
		private String lastContent;

		@Tool(name = "listProtocols", description = """
				List available statistical quality protocols with their file paths and short descriptions so the model
				can choose the most appropriate one or report that none apply.""")
		public ProtocolCatalog listProtocols() {
			listInvoked.set(true);
			return new ProtocolCatalog(new ProtocolEntry[] {
					new ProtocolEntry("/protocols/fdx-2024-standard.md",
							"FDX 2024 standard protocol for SPC readiness, normality check, control limits"),
					new ProtocolEntry("/protocols/fdx-legacy-v1.md", "Legacy FDX v1 protocol (deprecated)"),
					new ProtocolEntry("/protocols/experimental-lab-protocol.md", "Experimental lab-only protocol")
			});
		}

		@Tool(name = "getProtocol", description = "Retrieve the protocol content for a given path.")
		public String getProtocol(String path) {
			getInvoked.set(true);
			lastPath = path;
			String resourcePath = path.startsWith("/") ? path : "/" + path;
			try (var stream = ProtocolCatalogTool.class.getResourceAsStream(resourcePath)) {
				if (stream == null) {
					return "Protocol not found: " + path;
				}
				lastContent = new String(stream.readAllBytes());
				return lastContent;
			} catch (Exception ex) {
				return "Error reading protocol " + path + ": " + ex.getMessage();
			}
		}

		boolean listInvoked() {
			return listInvoked.get();
		}

		boolean getInvoked() {
			return getInvoked.get();
		}

		String lastPath() {
			return lastPath;
		}

		String lastContent() {
			return lastContent;
		}
	}

	public record ProtocolCatalog(ProtocolEntry[] protocols) {
	}

	public record ProtocolEntry(String path, String description) {
	}
}

