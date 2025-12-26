package org.javai.springai.scenarios;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDate;
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
import org.javai.springai.dsl.prompt.InMemorySqlCatalog;
import org.javai.springai.dsl.prompt.SqlCatalogContextContributor;
import org.javai.springai.dsl.sql.Query;
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
	PlanResolver resolver;
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
				.temperature(0.1)
				.topP(1.0)
				.build();
		chatClient = ChatClient.builder(Objects.requireNonNull(chatModel))
				.defaultOptions(Objects.requireNonNull(options))
				.build();

		dataWarehouseActions = new DataWarehouseActions();

		planner = Planner.builder()
				.withChatClient(chatClient)
				.actions(dataWarehouseActions)
				.build();
		resolver = new DefaultPlanResolver();
		executor = new DefaultPlanExecutor();
		conversationManager = new ConversationManager(planner, resolver, new InMemoryConversationStateStore());
	}

	@Test
	void selectWithoutDatabaseObjectConstraintsTest() {
		String request = "create a query to select and sum all displacement values from the elasticity table for bundle A12345";
		ConversationTurnResult turn = conversationManager.converse(request, "select-session");
		ResolvedPlan resolvedPlan = turn.resolvedPlan();

		assertThat(resolvedPlan).isNotNull();
		assertThat(resolvedPlan.status()).isEqualTo(PlanStatus.READY);
		assertThat(resolvedPlan.steps()).hasSize(1);
		ResolvedStep step = resolvedPlan.steps().getFirst();
		assertThat(step).isInstanceOf(ResolvedStep.ActionStep.class);

		PlanExecutionResult executed = executor.execute(resolvedPlan);
		assertThat(executed.success()).isTrue();
		assertThat(dataWarehouseActions.displaySqlQueryInvoked()).isTrue();
	}

	@Test
	void selectWithDatabaseObjectConstraintsTest() {
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

		Planner catalogAwarePlanner = Planner.builder()
				.withChatClient(chatClient)
				.actions(dataWarehouseActions)
				.addDslContextContributor(new SqlCatalogContextContributor(catalog))
				.addDslContext("sxl-sql", catalog)
				.build();
		ConversationManager catalogConversationManager = new ConversationManager(
				catalogAwarePlanner,
				resolver,
				new InMemoryConversationStateStore());

		String request = "execute a select and sum all orders' values for the customer who's name is 'Mike' in the last 30 days";
		ConversationTurnResult turn = catalogConversationManager.converse(request, "constrained-select-session");
		ResolvedPlan resolvedPlan = turn.resolvedPlan();
		assertThat(resolvedPlan).isNotNull();
		assertThat(resolvedPlan.status()).isEqualTo(PlanStatus.READY);
		assertThat(resolvedPlan.steps()).hasSize(1);
		ResolvedStep step = resolvedPlan.steps().getFirst();
		assertThat(step).isInstanceOf(ResolvedStep.ActionStep.class);

		PlanExecutionResult executed = executor.execute(resolvedPlan);
		assertThat(executed.success()).isTrue();
		assertThat(dataWarehouseActions.executeAndDisplaySqlQueryInvoked()).isTrue();
	}

	@Test
	void aggregateOrderValueWithJsonRecordParameters() {
		String request = """
				calculate the total order value for customer Mike between 2024-01-01 and 2024-01-31
				""";

		ConversationTurnResult turn = conversationManager.converse(request, "order-value-session");
		ResolvedPlan resolvedPlan = turn.resolvedPlan();

		assertThat(resolvedPlan).isNotNull();
		assertThat(resolvedPlan.status()).isEqualTo(PlanStatus.READY);
		assertThat(resolvedPlan.steps()).hasSize(1);
		assertThat(resolvedPlan.steps().getFirst()).isInstanceOf(ResolvedStep.ActionStep.class);

		PlanExecutionResult executed = executor.execute(resolvedPlan);

		assertThat(executed.success()).isTrue();
		assertThat(dataWarehouseActions.aggregateOrderValueInvoked()).isTrue();

		OrderValueQuery query = dataWarehouseActions.lastOrderValueQuery();
		assertThat(query).isNotNull();
		assertThat(query.customer_name()).isEqualTo("Mike");
		assertThat(query.period()).isNotNull();
		assertThat(query.period().start()).isEqualTo(LocalDate.parse("2024-01-01"));
		assertThat(query.period().end()).isEqualTo(LocalDate.parse("2024-01-31"));
	}

	public static class DataWarehouseActions {
		private final AtomicBoolean displaySqlQueryInvoked = new AtomicBoolean(false);
		private final AtomicBoolean executeAndDisplaySqlQueryInvoked = new AtomicBoolean(false);
		private final AtomicBoolean aggregateOrderValueInvoked = new AtomicBoolean(false);
		private OrderValueQuery lastOrderValueQuery;

		@Action(description = """
				Use the user's input to derive an sql query.""")
		public void displaySqlQuery(
				@ActionParam(description = "The query") Query query) {
			displaySqlQueryInvoked.set(true);
			System.out.println(query.sqlString(Query.Dialect.ANSI));
		}

		@Action(description = """
				Use the user's input to derive an sql query, execute it and display the result set.""")
		public void executeAndDisplaySqlQuery(
				@ActionParam(description = "The query to be executed") Query query) {
			executeAndDisplaySqlQueryInvoked.set(true);
			System.out.println(query.sqlString(Query.Dialect.ANSI));
		}

		@Action(description = """
				Use the user's input to derive a JSON order value query with a customer_name and ISO-8601 period start/end.
				Prefer JSON; do not emit an s-expression for this payload.""")
		public void aggregateOrderValue(
				@ActionParam(description = "Order value inputs as JSON with customer_name and nested period {start,end}")
				OrderValueQuery orderValueQuery) {
			aggregateOrderValueInvoked.set(true);
			lastOrderValueQuery = orderValueQuery;
			System.out.printf("Aggregating order value for %s from %s to %s%n",
					orderValueQuery.customer_name(),
					orderValueQuery.period().start(),
					orderValueQuery.period().end());
		}


		boolean displaySqlQueryInvoked() {
			return displaySqlQueryInvoked.get();
		}

		boolean executeAndDisplaySqlQueryInvoked() {
			return executeAndDisplaySqlQueryInvoked.get();
		}

		boolean aggregateOrderValueInvoked() {
			return aggregateOrderValueInvoked.get();
		}

		OrderValueQuery lastOrderValueQuery() {
			return lastOrderValueQuery;
		}

	}

	public record OrderValueQuery(String customer_name, Period period) {
	}

	public record Period(LocalDate start, LocalDate end) {
	}
}
