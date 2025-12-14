package org.javai.springai.dsl.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.yaml.snakeyaml.Yaml;

/**
 * Prompt tuning harness: uses a fixed, hand-authored system prompt and user prompt
 * (multi-step plan with embedded SQL) to iterate LLM behavior without touching the
 * main prompt builder. Requires OPENAI_API_KEY; skips otherwise.
 */
@Disabled("LLM prompt tuning harness; run manually when iterating prompts")
class SystemPromptTuningTest {

	private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");

	private static final String SYSTEM_PROMPT = """
			You are a planner. Output ONLY canonical S-expressions for plans.

			Available action:
			  - runQuery: Execute a SQL query and return rows for the user.

			Plan DSL (sxl-plan):
			  Form: (P ["description"] (PS action-id (EMBED sxl-sql <query>)))
			  Symbols: P, PS, EMBED. Use action-id runQuery for the step.

			SQL DSL (sxl-sql) clause legend (order matters):
			  Q = (F table alias) [J|J_LEFT|J_RIGHT|J_FULL ...] (S select-items...) [W where...] [G group-by...] [H having...] [O order-by...] [L limit] [D]
			  Use symbols, not English words. Operators: EQ/NE/GT/LT/GE/LE/AND/OR/etc. Use NE (not NEQ or &lt;&gt;). Aliases via AS. Order-by uses O with ASC/DESC, e.g., (O (ASC expr) (DESC expr)).
			  Date parts: use EXTRACT with literal part strings, e.g., (EXTRACT "YEAR" expr) or (EXTRACT "MONTH" expr); DATE_TRUNC is allowed. Do NOT use YEAR(), MONTHNAME(), or bare YEAR/MONTH symbols.
			  Do NOT use '*' or table.*; always list select items explicitly (optionally with AS).
			  Use only symbols listed above; do not invent new function names.

			Neutral examples (non-overlapping with user intent):
			  (P "Example"
			    (PS listProducts (EMBED sxl-sql (Q (F products p) (S (AS p.sku sku) (AS p.price price))))))
			  (P "JoinExample"
			    (PS joinData (EMBED sxl-sql (Q (F sales s) (J customers c (EQ s.customer_id c.id)) (S (AS s.id id) (AS c.region region)) (W (EQ c.status "ACTIVE"))))))
			  (P "GroupExample"
			    (PS regionalTotals (EMBED sxl-sql (Q (F facts f) (S (AS f.region region) (AS (SUM f.amount) total)) (G f.region) (H (GT (SUM f.amount) 1000)) (O (ASC f.region)) (L 100)))))

			Output only the S-expression plan. No prose.
			""";

	@Test
	void tunedPromptProducesParsablePlan() {
		assumeTrue(OPENAI_API_KEY != null && !OPENAI_API_KEY.isBlank(),
				"OPENAI_API_KEY must be set for this tuning test");

		TypeFactoryBootstrap.registerBuiltIns();

		OpenAiApi openAiApi = OpenAiApi.builder().apiKey(OPENAI_API_KEY).build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
				.openAiApi(openAiApi)
				.build();
		OpenAiChatOptions options = OpenAiChatOptions.builder()
				.model("gpt-4.1-mini")
				.temperature(0.0)
				.build();

		ChatClient client = ChatClient.builder(chatModel)
				.defaultOptions(options)
				.build();

		List<String> humanPrompts = loadHumanPrompts(50);
		assertThat(humanPrompts).isNotEmpty();

		for (String human : humanPrompts) {
			String userPrompt = """
					User request: %s
					Create the plan to satisfy this request.
					""".formatted(human.replace("\n", " "));

			String response = client
					.prompt()
					.system(SYSTEM_PROMPT)
					.user(userPrompt)
					.call()
					.content();

			System.out.println("----");
			System.out.println(human);
			System.out.println(response);

			Plan plan = parsePlan(response);
			assertThat(plan.planSteps()).hasSizeGreaterThanOrEqualTo(1);
			PlanStep step = plan.planSteps().getFirst();
			assertThat(step).isInstanceOf(PlanStep.Action.class);
			PlanStep.Action action = (PlanStep.Action) step;
			assertThat(action.actionId()).isEqualTo("runQuery");
			assertThat(action.actionArguments()).hasSize(1);
			assertThat(action.actionArguments()[0]).isInstanceOf(Query.class);
		}
	}

	@SuppressWarnings("unchecked")
	private List<String> loadHumanPrompts(int limit) {
		Yaml yaml = new Yaml();
		try (var in = getClass().getClassLoader().getResourceAsStream("sql-samples.yml")) {
			if (in == null) {
				throw new IllegalStateException("sql-samples.yml not found");
			}
			Map<String, Object> root = yaml.load(in);
			List<Map<String, Object>> samples = (List<Map<String, Object>>) root.get("sample-queries");
			return samples.stream()
					.flatMap(entry -> {
						List<String> human = (List<String>) entry.get("human");
						return human == null ? Stream.<String>empty() : human.stream();
					})
					.limit(limit)
					.collect(Collectors.toList());
		} catch (Exception e) {
			throw new IllegalStateException("Failed to load sql-samples.yml", e);
		}
	}

	private Plan parsePlan(String response) {
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
			List<SxlNode> nodes = new ComplexDslValidator(registry).parseAndValidate(tokens);
			assertThat(nodes).isNotEmpty();
			planNode = nodes.getFirst();
			if ("EMBED".equals(planNode.symbol()) && planNode.args().size() >= 2) {
				planNode = planNode.args().get(1);
			}
		} else {
			List<SxlNode> nodes = new DslParsingStrategy(planGrammar, registry).parse(tokens);
			assertThat(nodes).isNotEmpty();
			planNode = nodes.getFirst();
		}

		assertThat(planNode.symbol()).isEqualTo("P");
		return PlanNodeVisitor.generate(planNode);
	}
}
