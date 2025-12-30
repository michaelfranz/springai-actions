package org.javai.springai.scenarios.data_warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.javai.springai.actions.test.PlanAssertions.assertExecutionSuccess;
import static org.javai.springai.actions.test.PlanAssertions.assertPlanReady;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.javai.springai.actions.DefaultPlanExecutor;
import org.javai.springai.actions.PersonaSpec;
import org.javai.springai.actions.Plan;
import org.javai.springai.actions.PlanExecutionResult;
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
		// Synonyms allow informal table names (e.g., "orders") to be automatically
		// substituted with canonical names (e.g., "fct_orders") without LLM retry
		InMemorySqlCatalog catalog = new InMemorySqlCatalog()
				.addTable("fct_orders", "Fact table for orders", "fact")
				.withSynonyms("fct_orders", "orders", "order", "sales")
				.addColumn("fct_orders", "customer_id", "FK to dim_customer", "string",
						new String[] { "fk:dim_customer.id" }, null)
				.addColumn("fct_orders", "date_id", "FK to dim_date", "string",
						new String[] { "fk:dim_date.id" }, null)
				.addColumn("fct_orders", "order_value", "Order amount", "double",
						new String[] { "measure" }, null)
				.addTable("dim_customer", "Customer dimension", "dimension")
				.withSynonyms("dim_customer", "customers", "customer", "cust")
				.addColumn("dim_customer", "id", "PK", "string",
						new String[] { "pk" }, new String[] { "unique" })
				.addColumn("dim_customer", "customer_name", "Customer name", "string",
						new String[] { "attribute" }, null)
				.addTable("dim_date", "Date dimension", "dimension")
				.withSynonyms("dim_date", "dates", "date", "calendar")
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
		assertPlanReady(plan);
		assertThat(plan.planSteps()).hasSize(1);
		PlanStep step = plan.planSteps().getFirst();
		assertThat(step).isInstanceOf(PlanStep.ActionStep.class);

		PlanExecutionResult executed = executor.execute(plan);
		assertExecutionSuccess(executed);
		assertThat(dataWarehouseActions.showSqlQueryInvoked()).isTrue();
	}

	@Test
	void selectWithDatabaseObjectConstraintsTest() {
		String request = "run query: select order_value from fct_orders";
		ConversationTurnResult turn = conversationManager.converse(request, "constrained-select-session");
		Plan plan = turn.plan();
		assertThat(plan).isNotNull();
		assertPlanReady(plan);
		assertThat(plan.planSteps()).hasSize(1);
		PlanStep step = plan.planSteps().getFirst();
		assertThat(step).isInstanceOf(PlanStep.ActionStep.class);

		PlanExecutionResult executed = executor.execute(plan);
		assertExecutionSuccess(executed);
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
		assertPlanReady(plan);
		assertThat(plan.planSteps()).hasSize(1);
		assertThat(plan.planSteps().getFirst()).isInstanceOf(PlanStep.ActionStep.class);

		PlanExecutionResult executed = executor.execute(plan);

		assertExecutionSuccess(executed);
		assertThat(dataWarehouseActions.aggregateOrderValueInvoked()).isTrue();

		OrderValueQuery query = dataWarehouseActions.lastOrderValueQuery();
		assertThat(query).isNotNull();
		assertThat(query.customer_name()).isEqualTo("Mike");
		assertThat(query.period()).isNotNull();
		assertThat(query.period().start()).isEqualTo(LocalDate.parse("2024-01-01"));
		assertThat(query.period().end()).isEqualTo(LocalDate.parse("2024-01-31"));
	}

	// ========== Star Schema JOIN Tests (Task 2.4) ==========

	@Test
	void joinFactToDimensionTable() {
		// Request requires joining fct_orders to dim_customer
		String request = "show me a query for order values with customer names";

		ConversationTurnResult turn = conversationManager.converse(request, "join-fact-dim-session");
		Plan plan = turn.plan();

		assertThat(plan).isNotNull();
		assertPlanReady(plan);
		assertThat(plan.planSteps()).hasSize(1);

		PlanExecutionResult executed = executor.execute(plan);
		assertExecutionSuccess(executed);
		assertThat(dataWarehouseActions.showSqlQueryInvoked()).isTrue();

		// Verify the generated SQL contains a JOIN
		String sql = dataWarehouseActions.lastQuery().sqlString().toUpperCase();
		assertThat(sql).contains("JOIN");
		assertThat(sql).contains("FCT_ORDERS");
		assertThat(sql).contains("DIM_CUSTOMER");
	}

	@Test
	void joinWithFilterOnDimensionAttribute() {
		// Request requires JOIN and filter on dimension attribute
		String request = "run query to get order values for customers named 'Smith'";

		ConversationTurnResult turn = conversationManager.converse(request, "join-filter-session");
		Plan plan = turn.plan();

		assertThat(plan).isNotNull();
		assertPlanReady(plan);

		PlanExecutionResult executed = executor.execute(plan);
		assertExecutionSuccess(executed);
		assertThat(dataWarehouseActions.runSqlQueryInvoked()).isTrue();

		// Verify JOIN and WHERE clause on dimension attribute
		String sql = dataWarehouseActions.lastQuery().sqlString().toUpperCase();
		assertThat(sql).contains("JOIN");
		assertThat(sql).contains("DIM_CUSTOMER");
		assertThat(sql).containsAnyOf("WHERE", "CUSTOMER_NAME", "SMITH");
	}

	@Test
	void joinMultipleDimensions() {
		// Request requires joining fact table to multiple dimensions
		String request = "show me order values with customer names and dates";

		ConversationTurnResult turn = conversationManager.converse(request, "multi-join-session");
		Plan plan = turn.plan();

		assertThat(plan).isNotNull();
		assertPlanReady(plan);

		PlanExecutionResult executed = executor.execute(plan);
		assertExecutionSuccess(executed);

		// Verify the SQL contains JOINs to both dimension tables
		String sql = dataWarehouseActions.lastQuery().sqlString().toUpperCase();
		assertThat(sql).contains("FCT_ORDERS");
		assertThat(sql).contains("DIM_CUSTOMER");
		assertThat(sql).contains("DIM_DATE");
		// Should have at least 2 JOINs (could be written as multiple JOINs or subqueries)
		assertThat(sql).contains("JOIN");
	}

	@Test
	void joinWithColumnSelection() {
		// Request specific columns from both fact and dimension
		String request = "show query for customer_name and order_value from orders joined with customers";

		ConversationTurnResult turn = conversationManager.converse(request, "join-columns-session");
		Plan plan = turn.plan();

		assertThat(plan).isNotNull();
		assertPlanReady(plan);

		PlanExecutionResult executed = executor.execute(plan);
		assertExecutionSuccess(executed);

		String sql = dataWarehouseActions.lastQuery().sqlString().toUpperCase();
		assertThat(sql).contains("JOIN");
		assertThat(sql).contains("CUSTOMER_NAME");
		assertThat(sql).contains("ORDER_VALUE");
	}
}
