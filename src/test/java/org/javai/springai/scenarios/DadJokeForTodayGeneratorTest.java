package org.javai.springai.scenarios;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.api.ActionParam;
import org.javai.springai.actions.api.ContextKey;
import org.javai.springai.actions.api.Mutability;
import org.javai.springai.actions.execution.DefaultPlanExecutor;
import org.javai.springai.actions.execution.ExecutablePlan;
import org.javai.springai.actions.execution.PlanExecutionException;
import org.javai.springai.actions.execution.PlanExecutor;
import org.javai.springai.actions.planning.PlanningChatClient;
import org.javai.springai.actions.planning.PlanningPromptSpec;
import org.javai.springai.actions.tuning.LlmTuningConfig;
import org.javai.springai.actions.tuning.PlanSupplier;
import org.javai.springai.actions.tuning.ScenarioPlanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.annotation.Tool;

	public class DadJokeForTodayGeneratorTest implements ScenarioPlanSupplier {

	private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");
	private static final boolean RUN_LLM_TESTS = "true".equalsIgnoreCase(System.getenv("RUN_LLM_TESTS"));
	private static final Random RANDOM = new Random();
	private static final List<String> NAMES = List.of("Mike", "Dave", "Martin", "Elke", "Helena");
	private static final ContextKey<String> EMAIL_TEXT_KEY = ContextKey.of("emailText", String.class);
	private static final String DAD_JOKE_TASK = """
			You are an expert in dad jokes. Try to make a dad joke using the user's input.
			You must call the randomName tool to obtain a name, which is to be included in the joke.
			You must call the localDateTime tool to obtain the current date and time.
			You must insert the retrieved random name and the retrieved date and time in the joke.
			Your response is an action plan to send the joke to michaelmannion@me.com from the sender joker@me.com.
			The joke must somehow be replated to the song Rocket Man by Elton John.
			""";

	private static final LlmTuningConfig BASELINE_CONFIG = new LlmTuningConfig(
			"You are an expert in dad jokes. Keep the humor light-hearted.",
			0.7,
			0.9
	);

	@Test
	void generateDadJokeForTodayPlan() throws PlanExecutionException {
		ExecutablePlan plan = planSupplier().get();
		assertThat(plan.executables())
				.hasSize(1);

		PlanExecutor executor = new DefaultPlanExecutor();
		ActionContext actionContext = executor.execute(plan);
		assertThat(actionContext.get(EMAIL_TEXT_KEY))
				.startsWith("to:");
	}

	@Tool(description = "Provide the current local date and time in the format 2011-12-03T10:15:30")
	public String localDateTime() {
		return LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
	}

	@Tool(description = "Provide a random name")
	public String randomName() {
		return NAMES.get(RANDOM.nextInt(NAMES.size()));
	}

	@Action(description = "Action to send an email",
			contextKey = "emailText",
			affinity = "email:{to}",
			mutability = Mutability.MUTATE)
	public String sendEmail(
			@ActionParam(description = "The email recipient") String to,
			@ActionParam(description = "The email sender") String from,
			@ActionParam(description = "The email subject") String subject,
			@ActionParam(description = "The email body") String body
	) {
		String email = """
				to: %s
				from: %s
				subject: %s
				body: %s
				""".formatted(to, from, subject, body);
		System.out.println("Sending email to\n" + email);
		return email;
	}

	@Override
	public String scenarioId() {
		return "dad-joke-generator";
	}

	@Override
	public String description() {
		return "Generate a dad joke related to the song 'Rocket Man' by Elton John. " +
				"The scenario must call the randomName tool to get a name to include in the joke, " +
				"and the localDateTime tool to get the current date and time. " +
				"Both the name and timestamp must be incorporated into the joke. " +
				"Finally, send the joke via email to michaelmannion@me.com from joker@me.com.";
	}

	@Override
	public LlmTuningConfig defaultConfig() {
		return BASELINE_CONFIG;
	}

	@Override
	public PlanSupplier planSupplier(LlmTuningConfig config) {
		LlmTuningConfig effective = config != null ? config : defaultConfig();
		return () -> createPlan(effective);
	}

	private ExecutablePlan createPlan(LlmTuningConfig config) {
		PlanningPromptSpec prompt = createPlanningClient(config).prompt();
		if (config.systemPrompt() != null && !config.systemPrompt().isBlank()) {
			prompt = prompt.system(config.systemPrompt());
		}
		return prompt
				.user(DAD_JOKE_TASK)
				.tools(this)
				.actions(this)
				.plan();
	}

	private PlanningChatClient createPlanningClient(LlmTuningConfig config) {
		ensureApiKeyPresent();
		OpenAiApi openAiApi = OpenAiApi.builder().apiKey(OPENAI_API_KEY).build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder().openAiApi(openAiApi).build();
		OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder().model("gpt-4.1-mini");
		if (config.temperature() != null) {
			optionsBuilder.temperature(config.temperature());
		}
		if (config.topP() != null) {
			optionsBuilder.topP(config.topP());
		}
		ChatClient springAiChatClient = ChatClient.builder(Objects.requireNonNull(chatModel))
				.defaultOptions(Objects.requireNonNull(optionsBuilder.build()))
				.build();
		return new PlanningChatClient(springAiChatClient);
	}

	private void ensureApiKeyPresent() {
		Assumptions.assumeTrue(RUN_LLM_TESTS, "Set RUN_LLM_TESTS=true to enable LLM integration tests");
		Assumptions.assumeTrue(OPENAI_API_KEY != null && !OPENAI_API_KEY.isBlank(),
				"Missing OPENAI_API_KEY environment variable. Please export OPENAI_API_KEY before running the tests.");
	}

}
