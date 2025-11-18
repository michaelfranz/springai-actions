package org.javai.springai.scenarios;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.api.ActionParam;
import org.javai.springai.actions.execution.DefaultPlanExecutor;
import org.javai.springai.actions.execution.ExecutablePlan;
import org.javai.springai.actions.execution.PlanExecutionException;
import org.javai.springai.actions.execution.PlanExecutor;
import org.javai.springai.actions.planning.PlanningChatClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.annotation.Tool;

public class DadJokeForTodayGeneratorTest {

	private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");
	private static final Random RANDOM = new Random();
	private static final List<String> NAMES = List.of("Mike", "Dave", "Martin", "Elke", "Helena");

	private PlanningChatClient chatClient;

	@BeforeEach
	void setUp() {
		if (OPENAI_API_KEY == null || OPENAI_API_KEY.isBlank()) {
			throw new IllegalStateException("Missing OPENAI_API_KEY environment variable. Please export OPENA_API_KEY before running the tests.");
		}
		OpenAiApi openAiApi = OpenAiApi.builder().apiKey(OPENAI_API_KEY).build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder().openAiApi(openAiApi).build();
		OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder().model("gpt-4.1-mini");
		optionsBuilder.temperature(0.7);

		// Build the Spring AI ChatClient using the OpenAI chat model and options
		ChatClient springAiChatClient = ChatClient.builder(chatModel)
				.defaultOptions(optionsBuilder.build())
				.build();

		// Wrap it with our PlanningChatClient facade used by the scenarios
		this.chatClient = new PlanningChatClient(springAiChatClient);
	}

	@Test
	void generateDadJokeForTodayPlan() throws PlanExecutionException {
		ExecutablePlan plan = chatClient
				.prompt()
				.user("""
						You are an expert in dad jokes. Try to make a dad joke using the user's input.
						You must call the randomName tool to obtain a name, which is to be included in the joke.
						You must call the localDateTime tool to obtain the current date and time.
						You must insert the retrieved random name and the retrieved date and time in the joke.
						Your response is an action plan to send the joke to michaelmannion@me.com from the sender joker@me.com.
						The joke must somehow be replated to the song Rocket Man by Elton John.
						""")
				.tools(this)
				.actions(this).plan();
		assertThat(plan.executables())
				.hasSize(1);

		PlanExecutor executor = new DefaultPlanExecutor();
		ActionContext actionContext = executor.execute(plan);
		assertThat(actionContext.get("emailText", String.class))
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

	@Action(description = "Action to send an email", contextKey = "emailText")
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
		return "Email sent";
	}

}
