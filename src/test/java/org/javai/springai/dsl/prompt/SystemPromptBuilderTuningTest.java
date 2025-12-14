package org.javai.springai.dsl.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import org.yaml.snakeyaml.Yaml;

/**
 * Uses SystemPromptBuilder to generate the system prompt and validates LLM outputs
 * across the sql-samples.yml human prompts. Requires OPENAI_API_KEY; skips otherwise.
 * Expect initial failures until SystemPromptBuilder is aligned with the tuned prompt.
 */
class SystemPromptBuilderTuningTest {

	private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");
	private static final Path FAILURE_LOG = Path.of("build", "tmp", "system-prompt-builder-tuning-failures.txt");

	@Test
	void builderPromptProducesParsablePlans() {
		assumeTrue(OPENAI_API_KEY != null && !OPENAI_API_KEY.isBlank(),
				"OPENAI_API_KEY must be set for this tuning test");

		TypeFactoryBootstrap.registerBuiltIns();

		ActionRegistry registry = new ActionRegistry();
		registry.registerActions(new RunQueryAction());

		DslGuidanceProvider guidanceProvider = new GrammarBackedDslGuidanceProvider(
				List.of("sxl-meta-grammar-sql.yml", "sxl-meta-grammar-plan.yml"),
				getClass().getClassLoader());

		String systemPrompt = SystemPromptBuilder.build(
				registry,
				ActionSpecFilter.ALL,
				guidanceProvider,
				SystemPromptBuilder.Mode.SXL,
				"openai",
				"gpt-4.1-mini");

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
		humanPrompts = filterByPreviousFailures(humanPrompts);
		assertThat(humanPrompts).isNotEmpty();

		List<String> errors = new ArrayList<>();

		for (String human : humanPrompts) {
			String userPrompt = """
					User request: %s
					Create the plan to satisfy this request.
					""".formatted(human.replace("\n", " "));

			String response = client
					.prompt()
					.system(systemPrompt)
					.user(userPrompt)
					.call()
					.content();

			System.out.println("----");
			System.out.println(human);
			System.out.println(response);

			try {
				Plan plan = parsePlan(response);
				assertThat(plan.planSteps()).hasSizeGreaterThanOrEqualTo(1);
				PlanStep step = plan.planSteps().getFirst();
				assertThat(step).isInstanceOf(PlanStep.Action.class);
				PlanStep.Action action = (PlanStep.Action) step;
				assertThat(action.actionId()).isEqualTo("runQuery");
				assertThat(action.actionArguments()).hasSize(1);
				assertThat(action.actionArguments()[0]).isInstanceOf(Query.class);
			}
			catch (Exception ex) {
				errors.add("Prompt: " + human + "\nResponse:\n" + response + "\nError: " + ex.getMessage());
				if (errors.size() >= 3) {
					break;
				}
			}
		}

		if (!errors.isEmpty()) {
			persistFailures(errors);
			String message = "Encountered " + errors.size() + " parsing errors:\n\n" +
					String.join("\n\n----\n\n", errors);
			throw new AssertionError(message);
		}
		clearFailures();
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

	private List<String> filterByPreviousFailures(List<String> prompts) {
		try {
			if (Files.exists(FAILURE_LOG)) {
				List<String> failed = Files.readAllLines(FAILURE_LOG).stream()
						.filter(s -> !s.isBlank())
						.toList();
				if (!failed.isEmpty()) {
					return prompts.stream().filter(failed::contains).toList();
				}
			}
		} catch (Exception e) {
			// ignore and fall back
		}
		return prompts;
	}

	private void persistFailures(List<String> errors) {
		try {
			Files.createDirectories(FAILURE_LOG.getParent());
			List<String> failedPrompts = errors.stream()
					.map(err -> {
						int idx = err.indexOf("Prompt: ");
						if (idx >= 0) {
							int start = idx + "Prompt: ".length();
							int end = err.indexOf("\nResponse:", start);
							if (end > start) {
								return err.substring(start, end).trim();
							}
						}
						return "";
					})
					.filter(s -> !s.isBlank())
					.toList();
					Files.write(FAILURE_LOG, failedPrompts);
		} catch (Exception e) {
			// best effort
		}
	}

	private void clearFailures() {
		try {
			Files.deleteIfExists(FAILURE_LOG);
		} catch (Exception e) {
			// ignore
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

	private static class RunQueryAction {
		@Action(description = "Execute a SQL query and return rows for the user")
		public void runQuery(Query query) {
		}
	}
}
