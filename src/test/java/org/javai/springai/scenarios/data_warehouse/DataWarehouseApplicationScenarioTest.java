package org.javai.springai.scenarios.data_warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.javai.springai.actions.DefaultPlanExecutor;
import org.javai.springai.actions.PersonaSpec;
import org.javai.springai.actions.Plan;
import org.javai.springai.actions.PlanExecutionResult;
import org.javai.springai.actions.PlanStatus;
import org.javai.springai.actions.PlanStep;
import org.javai.springai.actions.Planner;
import org.javai.springai.actions.conversation.ConversationManager;
import org.javai.springai.actions.conversation.ConversationTurnResult;
import org.javai.springai.actions.conversation.InMemoryConversationStateStore;
import org.javai.springai.actions.sql.InMemorySqlCatalog;
import org.javai.springai.actions.sql.SqlCatalogContextContributor;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

public class DataWarehouseApplicationScenarioTest {

	private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");
	private static final boolean RUN_LLM_TESTS = "true".equalsIgnoreCase(System.getenv("RUN_LLM_TESTS"));

	Planner planner;
	DefaultPlanExecutor executor;
	ConversationManager conversationManager;
	DataWarehouseActions dataWarehouseActions;
	ChatClient chatClient;


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
		chatClient = ChatClient.builder(Objects.requireNonNull(chatModel))
				.defaultOptions(Objects.requireNonNull(options))
				.build();

		dataWarehouseActions = new DataWarehouseActions();

		// Create SQL catalog once in setUp - consistent context for all tests
		InMemorySqlCatalog catalog = new InMemorySqlCatalog()
				.addTable("fct_orders", "Fact table for orders", "fact")
				.addColumn("fct_orders", "customer_id", "FK to dim_customer", "string",
						new String[] { "fk:dim_customer.id" }, null)
				.addColumn("fct_orders", "date_id", "FK to dim_date", "string",
						new String[] { "fk:dim_date.id" }, null)
				.addColumn("fct_orders", "order_value", "Order amount", "double",
						new String[] { "measure" }, null)
				.addTable("dim_customer", "Customer dimension", "dimension")
				.addColumn("dim_customer", "id", "PK", "string",
						new String[] { "pk" }, new String[] { "unique" })
				.addColumn("dim_customer", "customer_name", "Customer name", "string",
						new String[] { "attribute" }, null)
				.addTable("dim_date", "Date dimension", "dimension")
				.addColumn("dim_date", "id", "PK", "string",
						new String[] { "pk" }, new String[] { "unique" })
				.addColumn("dim_date", "date", "Calendar date", "date",
						new String[] { "attribute" }, null);

		PersonaSpec sqlAnalystPersona = PersonaSpec.builder()
				.name("SQLDataWarehouseAssistant")
				.role("Assistant for data warehouse query planning and order value analysis")
				.principles(List.of(
						"Understand what the user wants to accomplish from a domain perspective",
						"Select the action whose purpose best matches the user's intent"))
				.constraints(List.of(
						"Only use the available actions",
						"If any required parameter is unclear, use PENDING"))
				.build();

		// Base planner with catalog context always available
		planner = Planner.builder()
				.withChatClient(chatClient)
				.persona(sqlAnalystPersona)
				.actions(dataWarehouseActions)
				.addPromptContributor(new SqlCatalogContextContributor(catalog))
				.addPromptContext("sql", catalog)
				.build();
		executor = new DefaultPlanExecutor();
		conversationManager = new ConversationManager(planner, new InMemoryConversationStateStore());
	}

	@Test
	void selectWithoutDatabaseObjectConstraintsTest() {
		String request = "show me a query for customer names from dim_customer";
		ConversationTurnResult turn = conversationManager.converse(request, "select-session");
		Plan plan = turn.plan();

		assertThat(plan).isNotNull();
		assertThat(plan.status()).isEqualTo(PlanStatus.READY);
		assertThat(plan.planSteps()).hasSize(1);
		PlanStep step = plan.planSteps().getFirst();
		assertThat(step).isInstanceOf(PlanStep.ActionStep.class);

		PlanExecutionResult executed = executor.execute(plan);
		assertThat(executed.success()).isTrue();
		assertThat(dataWarehouseActions.showSqlQueryInvoked()).isTrue();
	}

	@Test
	void selectWithDatabaseObjectConstraintsTest() {
		String request = "run query: select order_value from fct_orders";
		ConversationTurnResult turn = conversationManager.converse(request, "constrained-select-session");
		Plan plan = turn.plan();
		assertThat(plan).isNotNull();
		assertThat(plan.status()).isEqualTo(PlanStatus.READY);
		assertThat(plan.planSteps()).hasSize(1);
		PlanStep step = plan.planSteps().getFirst();
		assertThat(step).isInstanceOf(PlanStep.ActionStep.class);

		PlanExecutionResult executed = executor.execute(plan);
		assertThat(executed.success()).isTrue();
		assertThat(dataWarehouseActions.runSqlQueryInvoked()).isTrue();
	}

	@Test
	void aggregateOrderValueWithJsonRecordParameters() {
		String request = """
				calculate the total order value for customer Mike between 2024-01-01 and 2024-01-31
				""";

		ConversationTurnResult turn = conversationManager.converse(request, "order-value-session");
		Plan plan = turn.plan();

		assertThat(plan).isNotNull();
		assertThat(plan.status()).isEqualTo(PlanStatus.READY);
		assertThat(plan.planSteps()).hasSize(1);
		assertThat(plan.planSteps().getFirst()).isInstanceOf(PlanStep.ActionStep.class);

		PlanExecutionResult executed = executor.execute(plan);

		assertThat(executed.success()).isTrue();
		assertThat(dataWarehouseActions.aggregateOrderValueInvoked()).isTrue();

		OrderValueQuery query = dataWarehouseActions.lastOrderValueQuery();
		assertThat(query).isNotNull();
		assertThat(query.customer_name()).isEqualTo("Mike");
		assertThat(query.period()).isNotNull();
		assertThat(query.period().start()).isEqualTo(LocalDate.parse("2024-01-01"));
		assertThat(query.period().end()).isEqualTo(LocalDate.parse("2024-01-31"));
	}
}
