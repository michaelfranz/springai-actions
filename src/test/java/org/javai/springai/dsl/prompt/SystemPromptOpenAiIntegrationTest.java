package org.javai.springai.dsl.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.util.List;
import org.javai.springai.actions.api.Action;
import org.javai.springai.dsl.act.ActionRegistry;
import org.javai.springai.dsl.act.ActionSpecFilter;
import org.javai.springai.dsl.bind.TypeFactoryBootstrap;
import org.javai.springai.dsl.plan.Plan;
import org.javai.springai.dsl.plan.PlanNodeVisitor;
import org.javai.springai.dsl.plan.PlanStep;
import org.javai.springai.dsl.sql.Query;
import org.javai.springai.sxl.ComplexDslValidator;
import org.javai.springai.sxl.DefaultValidatorRegistry;
import org.javai.springai.sxl.DslParsingStrategy;
import org.javai.springai.sxl.SxlNode;
import org.javai.springai.sxl.SxlToken;
import org.javai.springai.sxl.SxlTokenizer;
import org.javai.springai.sxl.grammar.SxlGrammarParser;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

/**
 * Integration test that builds a full system prompt, sends it to OpenAI with a
 * plausible user request, and validates that the returned plan is executable
 * against the registered action(s). This test requires OPENAI_API_KEY.
 */
class SystemPromptOpenAiIntegrationTest {

	private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");
	// private static final boolean RUN_OPENAI_TESTS = Boolean.parseBoolean(System.getenv("RUN_OPENAI_TESTS"))
	// 		|| Boolean.getBoolean("RUN_OPENAI_TESTS");

	@Test
	void openAiProducesExecutablePlanForRegisteredActions() {
		// assumeTrue(RUN_OPENAI_TESTS, "Set RUN_OPENAI_TESTS=true to enable this integration test");
		assumeTrue(OPENAI_API_KEY != null && !OPENAI_API_KEY.isBlank(),
				"OPENAI_API_KEY must be set for this integration test");

		// Bootstrap DSL factories and guidance
		TypeFactoryBootstrap.registerBuiltIns();
		DslGuidanceProvider guidanceProvider = new GrammarBackedDslGuidanceProvider(
				List.of("sxl-meta-grammar-sql.yml", "sxl-meta-grammar-plan.yml"),
				getClass().getClassLoader());

		ActionRegistry registry = new ActionRegistry();
		registry.registerActions(new SqlActions());

		String systemPrompt = SystemPromptBuilder.build(
				registry,
				ActionSpecFilter.ALL,
				guidanceProvider,
				SystemPromptBuilder.Mode.SXL,
				"openai",
				"gpt-4.1");

		String userPrompt = """
				Generate a plan that uses the action runQuery to fetch order ids and totals
				from a SQL table called orders. Use SQL that selects the id and total columns.
				Respond ONLY with the canonical S-expression plan, no prose.
				""";

		OpenAiApi openAiApi = OpenAiApi.builder().apiKey(OPENAI_API_KEY).build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
				.openAiApi(openAiApi)
				.build();
		OpenAiChatOptions options = OpenAiChatOptions.builder()
				.model("gpt-4.1-mini")
				.temperature(0.1)
				.build();

		ChatClient client = ChatClient.builder(chatModel)
				.defaultOptions(options)
				.build();

		String response = client
				.prompt()
				.system(systemPrompt)
				.user(userPrompt)
				.call()
				.content();

		assertThat(response).isNotBlank();

		Plan plan = parsePlan(response);

		assertThat(plan.planSteps()).hasSizeGreaterThanOrEqualTo(1);
		PlanStep first = plan.planSteps().getFirst();
		assertThat(first).isInstanceOf(PlanStep.Action.class);

		PlanStep.Action action = (PlanStep.Action) first;
		assertThat(action.actionId()).isEqualTo("runQuery");
		assertThat(action.actionArguments()).hasSize(1);
		assertThat(action.actionArguments()[0]).isInstanceOf(Query.class);
	}

	private Plan parsePlan(String response) {
		System.out.println(response);

		SxlGrammarParser parser = new SxlGrammarParser();
		var planGrammar = parser.parse(
				getClass().getClassLoader().getResourceAsStream("sxl-meta-grammar-plan.yml"));
		var sqlGrammar = parser.parse(
				getClass().getClassLoader().getResourceAsStream("sxl-meta-grammar-sql.yml"));

		DefaultValidatorRegistry registry = new DefaultValidatorRegistry();
		registry.addGrammar("sxl-plan", planGrammar);
		registry.addGrammar("sxl-sql", sqlGrammar);

		SxlTokenizer tokenizer = new SxlTokenizer(response);
		List<SxlToken> tokens = tokenizer.tokenize();

		SxlNode planNode;
		if (response.trim().startsWith("(EMBED")) {
			// Multi-DSL validation path requiring EMBED wrapper
			List<SxlNode> nodes = new ComplexDslValidator(registry).parseAndValidate(tokens);
			assertThat(nodes).isNotEmpty();
			planNode = nodes.getFirst();
			if ("EMBED".equals(planNode.symbol()) && planNode.args().size() >= 2) {
				planNode = planNode.args().get(1);
			}
		} else {
			// Raw plan without EMBED wrapper: parse directly with plan grammar
			List<SxlNode> nodes = new DslParsingStrategy(planGrammar, registry).parse(tokens);
			assertThat(nodes).isNotEmpty();
			planNode = nodes.getFirst();
		}

		assertThat(planNode.symbol()).isEqualTo("P");
		return PlanNodeVisitor.generate(planNode);
	}

	private static class SqlActions {
		@Action(description = "Run a SQL query")
		public void runQuery(Query query) {
		}
	}
}
