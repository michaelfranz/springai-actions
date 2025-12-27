package org.javai.springai.scenarios;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.api.ActionParam;
import org.javai.springai.dsl.conversation.ConversationManager;
import org.javai.springai.dsl.conversation.ConversationTurnResult;
import org.javai.springai.dsl.conversation.InMemoryConversationStateStore;
import org.javai.springai.dsl.exec.DefaultPlanExecutor;
import org.javai.springai.dsl.exec.DefaultPlanResolver;
import org.javai.springai.dsl.exec.PlanExecutionResult;
import org.javai.springai.dsl.exec.PlanResolver;
import org.javai.springai.dsl.exec.ResolvedPlan;
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
				Follow the standard FDX quality protocol for bushing displacement
				using bundle A12345. Produce a Marimo notebook with interactive
				charts and warehouse-backed dataframes.
				""";

		ConversationTurnResult turn = conversationManager.converse(request, "protocol-notebook-session");
		ResolvedPlan resolvedPlan = turn.resolvedPlan();

		assertThat(resolvedPlan).isNotNull();
		assertThat(resolvedPlan.status()).isEqualTo(PlanStatus.READY);
		PlanExecutionResult executed = executor.execute(resolvedPlan);
		executed.steps().forEach(step -> System.out.println(step.toString()));
		assertThat(executed.success()).isTrue();
		assertThat(protocolNotebookActions.invoked()).isTrue();
		assertThat(protocolCatalogTool.listInvoked()).isTrue();

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
				"## Provisional control limits",
				"## Lab-only data filter",
				"## Experimental distribution fit and residual analysis",
				"## Exploratory control limits and lab-only variance chart"
		);
	}

	public static class ProtocolNotebookActions {
		private final AtomicBoolean invoked = new AtomicBoolean(false);

		@Action(description = "Add normality test to notebook.")
		public void addNormalityTest(
				@ActionParam(description = "The type of component e.g. bushing, piston") String component,
				@ActionParam(description = "The type of measurement e.g. displacement, force") String measurement,
				@ActionParam(description = "The ID of the data bundle containing the measurements") String bundleId,
				ActionContext context) {
			getOrCreateBuilder(context).addMarkdown("## Normality test");
		}

		@Action(description = "Add statistical readiness to notebook.")
		public void addSpcReadinessTest(
				@ActionParam(description = "The type of component e.g. bushing, piston") String component,
				@ActionParam(description = "The type of measurement e.g. displacement, force") String measurement,
				@ActionParam(description = "The ID of the data bundle containing the measurements") String bundleId,
				ActionContext context) {
			getOrCreateBuilder(context).addMarkdown("## SPC readiness");
		}

		@Action(description = "Add control chart to notebook.")
		public void addSpcControlChart(
				@ActionParam(description = "The type of component e.g. bushing, piston") String component,
				@ActionParam(description = "The type of measurement e.g. displacement, force") String measurement,
				@ActionParam(description = "The ID of the data bundle containing the measurements") String bundleId,
				ActionContext context) {
			getOrCreateBuilder(context).addMarkdown("## Control chart");
		}

		@Action(description = "Add normality spot-check from legacy protocol to notebook.")
		public void addLegacyNormalitySpotCheck(
				@ActionParam(description = "The type of component e.g. bushing, piston") String component,
				@ActionParam(description = "The type of measurement e.g. displacement, force") String measurement,
				@ActionParam(description = "The ID of the data bundle containing the measurements") String bundleId,
				ActionContext context) {
			getOrCreateBuilder(context).addMarkdown("## Normality spot-check");
		}

		@Action(description = "Add minimal SPC readiness checklist from legacy protocol to notebook.")
		public void addLegacySpcReadinessChecklist(
				@ActionParam(description = "The type of component e.g. bushing, piston") String component,
				@ActionParam(description = "The type of measurement e.g. displacement, force") String measurement,
				@ActionParam(description = "The ID of the data bundle containing the measurements") String bundleId,
				ActionContext context) {
			getOrCreateBuilder(context).addMarkdown("## Minimal SPC readiness checklist");
		}

		@Action(description = "Add provisional control limits from legacy protocol to notebook.")
		public void addLegacyProvisionalControlLimits(
				@ActionParam(description = "The type of component e.g. bushing, piston") String component,
				@ActionParam(description = "The type of measurement e.g. displacement, force") String measurement,
				@ActionParam(description = "The ID of the data bundle containing the measurements") String bundleId,
				ActionContext context) {
			getOrCreateBuilder(context).addMarkdown("## Provisional control limits");
		}

		@Action(description = "Add lab-only data filter from experimental protocol to notebook.")
		public void addExperimentalLabOnlyFilter(
				@ActionParam(description = "The type of component e.g. bushing, piston") String component,
				@ActionParam(description = "The type of measurement e.g. displacement, force") String measurement,
				@ActionParam(description = "The ID of the data bundle containing the measurements") String bundleId,
				ActionContext context) {
			getOrCreateBuilder(context).addMarkdown("## Lab-only data filter");
		}

		@Action(description = "Add experimental distribution fit and residual analysis to notebook.")
		public void addExperimentalDistributionFit(
				@ActionParam(description = "The type of component e.g. bushing, piston") String component,
				@ActionParam(description = "The type of measurement e.g. displacement, force") String measurement,
				@ActionParam(description = "The ID of the data bundle containing the measurements") String bundleId,
				ActionContext context) {
			getOrCreateBuilder(context).addMarkdown("## Experimental distribution fit and residual analysis");
		}

		@Action(description = "Add exploratory control limits and lab-only variance chart to notebook.")
		public void addExperimentalControlLimits(
				@ActionParam(description = "The type of component e.g. bushing, piston") String component,
				@ActionParam(description = "The type of measurement e.g. displacement, force") String measurement,
				@ActionParam(description = "The ID of the data bundle containing the measurements") String bundleId,
				ActionContext context) {
			getOrCreateBuilder(context).addMarkdown("## Exploratory control limits and lab-only variance chart");
		}

		@Action(description = "Write the notebook to a file.")
		public void writeNotebook(
// 				@ActionParam(description = "Protocol name") String protocolName,
//				@ActionParam(description = "The ID of the data bundle containing the measurements") String bundleId,
				ActionContext context) {
			NotebookBuilder builder =
					Optional.ofNullable(context.get("notebookBuilder", NotebookBuilder.class)).orElseThrow(() -> new IllegalStateException("No notebookBuilder in context"));
			Notebook notebook = builder.build();
			context.put("notebook", notebook);
		}

		boolean invoked() {
			return invoked.get();
		}

		private NotebookBuilder getOrCreateBuilder(ActionContext context) {
			NotebookBuilder builder;
			if (context.contains("notebookBuilder")) {
				builder = context.get("notebookBuilder", NotebookBuilder.class);
			}
			else {
				builder = new NotebookBuilder();
				context.put("notebookBuilder", builder);
			}
			invoked.set(true);
			return builder;
		}
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

	public static final class NotebookBuilder {
		private final StringBuilder content = new StringBuilder();

		public NotebookBuilder addMarkdown(String markdown) {
			content.append(markdown).append("\n\n");
			return this;
		}

		public Notebook build() {
			return new Notebook(content.toString());
		}
	}

	public record Notebook(String content) {}
}

