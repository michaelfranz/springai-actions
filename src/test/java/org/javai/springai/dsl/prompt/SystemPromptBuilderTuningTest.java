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
import org.javai.springai.dsl.act.ActionDescriptorFilter;
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
 * 
 * Error Collection Strategy:
 * - Collects all parsing errors encountered during test execution
 * - Terminates early if failure count reaches the ERROR_THRESHOLD (5 failures)
 * - Threshold triggers indicate a more serious systematic problem requiring prompt refinement
 * - Failed prompts are persisted to FAILURE_LOG for targeted iteration on subsequent runs
 */
class SystemPromptBuilderTuningTest {

	private static final boolean RUN_LLM_TESTS = "true".equalsIgnoreCase(System.getenv("RUN_LLM_TESTS"));
	private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");
	private static final Path FAILURE_LOG = Path.of("build", "tmp", "system-prompt-builder-tuning-failures.txt");
	
	/**
	 * Threshold for maximum number of parsing errors to collect before terminating the test.
	 * When this many failures are encountered, it indicates a systematic problem with the
	 * LLM prompt guidance rather than isolated edge cases, warranting early termination
	 * and prompt refinement rather than wasting API quota on further tests.
	 * 
	 * Rationale for value of 5:
	 * - 1-2 errors: Likely edge cases, test continues to collect more data
	 * - 3-4 errors: Pattern emerging, still reasonable to continue investigation
	 * - 5+ errors: Systematic failure indicating prompt needs significant revision
	 */
	private static final int ERROR_THRESHOLD = 5;
	
	/**
	 * System property to control whether to use previously failed prompts only.
	 * 
	 * Set via: -Dtest.use_failure_log=false
	 * 
	 * When true (default): Runs only previously failed prompts (fast iteration on known issues)
	 * When false: Runs fresh set of 50 prompts from sql-samples.yml (comprehensive testing)
	 */
	private static final boolean USE_FAILURE_LOG = !"false".equalsIgnoreCase(System.getProperty("test.use_failure_log", "true"));

	@Test
	void builderPromptProducesParsablePlans() {
		assumeTrue(RUN_LLM_TESTS, "Set RUN_LLM_TESTS=true to enable this tuning test");
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
				ActionDescriptorFilter.ALL,
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
				.temperature(0.2)
				.build();

		ChatClient client = ChatClient.builder(chatModel)
				.defaultOptions(options)
				.build();

		List<String> humanPrompts = loadHumanPrompts(50);
		
		// Control whether to use failure log via system property
		if (USE_FAILURE_LOG) {
			List<String> filtered = filterByPreviousFailures(humanPrompts);
			if (!filtered.isEmpty()) {
				System.out.printf("Failure log found. Running %d previously failed prompt(s).%n", filtered.size());
				humanPrompts = filtered;
			} else {
				System.out.println("No previous failures found. Running full suite of 50 prompts.");
			}
		} else {
			System.out.println("Failure log filtering DISABLED (via -Dtest.use_failure_log=false)");
			System.out.printf("Running fresh test with all %d prompts from sql-samples.yml%n", humanPrompts.size());
		}
		
		assertThat(humanPrompts).isNotEmpty();

		List<String> errors = new ArrayList<>();

		for (int i = 0; i < humanPrompts.size(); i++) {
			System.out.println("----");
			String human = humanPrompts.get(i);
			String userPrompt = """
					User request: %s
					Create the plan to satisfy this request.
					""".formatted(human.replace("\n", " "));
			System.out.printf("Case #%d: %s\n", i, userPrompt);

			String response = client
					.prompt()
					.system(systemPrompt)
					.user(userPrompt)
					.call()
					.content();

			System.out.println(response);

			try {
				Plan plan = parsePlan(response);
				assertThat(plan.planSteps()).hasSizeGreaterThanOrEqualTo(1);
				PlanStep step = plan.planSteps().getFirst();
				assertThat(step).isInstanceOf(PlanStep.ActionStep.class);
				PlanStep.ActionStep action = (PlanStep.ActionStep) step;
				assertThat(action.actionId()).isEqualTo("runQuery");
				assertThat(action.actionArguments()).hasSize(1);
				assertThat(action.actionArguments()[0]).isInstanceOf(Query.class);
			}
			catch (Exception ex) {
				errors.add("Prompt: " + human + "\nResponse:\n" + response + "\nError: " + ex.getMessage());
				System.err.printf("Error in case #%d: %s%n", i, ex.getMessage());
				
				// Check if we've hit the error threshold
				if (errors.size() >= ERROR_THRESHOLD) {
					System.err.printf("%nThreshold of %d errors reached. Terminating test early.%n", ERROR_THRESHOLD);
					System.err.println("This indicates a systematic problem requiring prompt refinement.");
					break;
				}
			}
		}
		
		System.out.printf("%nTest completed. Total errors collected: %d/%d cases%n", errors.size(), humanPrompts.size());

		if (!errors.isEmpty()) {
			persistFailures(errors);
			boolean thresholdReached = errors.size() >= ERROR_THRESHOLD;
			String severityMessage = thresholdReached
					? "SEVERITY: Threshold reached (" + ERROR_THRESHOLD + " failures) - systematic prompt problem detected"
					: "SEVERITY: Minor issues - continue tuning";
			String modeInfo = USE_FAILURE_LOG 
					? "Test Mode: Failure log enabled (use -Dtest.use_failure_log=false to disable)"
					: "Test Mode: Comprehensive testing (running full sql-samples.yml suite)";
			String message = "Encountered " + errors.size() + " parsing errors:\n" +
					severityMessage + "\n" +
					modeInfo + "\n\n" +
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
