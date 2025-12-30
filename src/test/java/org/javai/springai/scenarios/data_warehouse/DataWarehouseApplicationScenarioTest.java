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
import org.junit.jupiter.api.DisplayName;
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
		// Table and column synonyms allow informal names to be automatically
		// substituted with canonical names without LLM retry
		InMemorySqlCatalog catalog = new InMemorySqlCatalog()
				.addTable("fct_orders", "Fact table for orders", "fact")
				.withSynonyms("fct_orders", "orders", "order", "sales")
				.addColumn("fct_orders", "customer_id", "FK to dim_customer", "string",
						new String[] { "fk:dim_customer.id" }, null)
				.addColumn("fct_orders", "date_id", "FK to dim_date", "string",
						new String[] { "fk:dim_date.id" }, null)
				.addColumn("fct_orders", "order_value", "Order amount", "double",
						new String[] { "measure" }, null)
				.withColumnSynonyms("fct_orders", "order_value", "value", "amount", "total")
				.addTable("dim_customer", "Customer dimension", "dimension")
				.withSynonyms("dim_customer", "customers", "customer", "cust")
				.addColumn("dim_customer", "id", "PK", "string",
						new String[] { "pk" }, new String[] { "unique" })
				.addColumn("dim_customer", "customer_name", "Customer name", "string",
						new String[] { "attribute" }, null)
				.withColumnSynonyms("dim_customer", "customer_name", "name", "cust_name")
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

	// ========== Tokenization Unit Tests (Task 2.18) ==========
	// These tests don't require LLM access - they verify the tokenization infrastructure

	@org.junit.jupiter.api.Nested
	@DisplayName("Tokenization Infrastructure Tests")
	class TokenizationTests {

		private InMemorySqlCatalog tokenizedCatalog;

		@org.junit.jupiter.api.BeforeEach
		void setUp() {
			// Create a tokenized version of the data warehouse catalog
			tokenizedCatalog = new InMemorySqlCatalog()
					.withTokenization(true)
					.addTable("fct_orders", "Fact table for orders", "fact")
					.addColumn("fct_orders", "customer_id", "FK to dim_customer", "string",
							new String[]{"fk:dim_customer.id"}, null)
					.addColumn("fct_orders", "date_id", "FK to dim_date", "string",
							new String[]{"fk:dim_date.id"}, null)
					.addColumn("fct_orders", "order_value", "Order amount", "double",
							new String[]{"measure"}, null)
					.addTable("dim_customer", "Customer dimension", "dimension")
					.addColumn("dim_customer", "id", "PK", "string",
							new String[]{"pk"}, new String[]{"unique"})
					.addColumn("dim_customer", "customer_name", "Customer name", "string",
							new String[]{"attribute"}, null)
					.addTable("dim_date", "Date dimension", "dimension")
					.addColumn("dim_date", "id", "PK", "string",
							new String[]{"pk"}, new String[]{"unique"})
					.addColumn("dim_date", "date", "Calendar date", "date",
							new String[]{"attribute"}, null);
		}

		@Test
		@DisplayName("catalog generates correct token prefixes for DW schema")
		void catalogGeneratesCorrectTokenPrefixes() {
			// Fact tables should have ft_ prefix
			String ordersToken = tokenizedCatalog.getTableToken("fct_orders").orElseThrow();
			assertThat(ordersToken).startsWith("ft_");

			// Dimension tables should have dt_ prefix
			String customerToken = tokenizedCatalog.getTableToken("dim_customer").orElseThrow();
			assertThat(customerToken).startsWith("dt_");

			String dateToken = tokenizedCatalog.getTableToken("dim_date").orElseThrow();
			assertThat(dateToken).startsWith("dt_");
		}

		@Test
		@DisplayName("tokenized catalog context contributor hides real names")
		void tokenizedCatalogContributorHidesRealNames() {
			// Add synonyms to the tokenized catalog for this test
			// With synonym-based tokenization, first synonym becomes the displayed name
			InMemorySqlCatalog catalogWithSynonyms = new InMemorySqlCatalog()
					.withTokenization(true)
					.addTable("fct_orders", "Fact table for orders", "fact")
					.withSynonyms("fct_orders", "orders", "sales")  // "orders" is displayed name
					.addColumn("fct_orders", "customer_id", "FK to dim_customer", "string",
							new String[]{"fk:dim_customer.id"}, null)  // no synonym -> cryptic token
					.addColumn("fct_orders", "order_value", "Order amount", "double",
							new String[]{"measure"}, null)
					.withColumnSynonyms("fct_orders", "order_value", "value", "amount")  // "value" is displayed
					.addTable("dim_customer", "Customer dimension", "dimension")
					.withSynonyms("dim_customer", "customers", "cust")  // "customers" is displayed name
					.addColumn("dim_customer", "id", "PK", "string",
							new String[]{"pk"}, new String[]{"unique"})  // no synonym -> cryptic token
					.addColumn("dim_customer", "customer_name", "Customer name", "string",
							new String[]{"attribute"}, null)
					.withColumnSynonyms("dim_customer", "customer_name", "name", "cust_name");  // "name" is displayed

			SqlCatalogContextContributor contributor = new SqlCatalogContextContributor(catalogWithSynonyms);
			String prompt = contributor.contribute(null).orElseThrow();

			// LLM sees standard SQL CATALOG (unaware of tokenization)
			assertThat(prompt).contains("SQL CATALOG:");
			
			// Tables with synonyms: first synonym becomes the displayed name
			assertThat(prompt).contains("- orders:");  // fct_orders -> "orders"
			assertThat(prompt).contains("- customers:");  // dim_customer -> "customers"
			
			// Columns without synonyms: still use cryptic tokens (c_)
			assertThat(prompt).contains("c_");

			// Real database names should NOT appear as identifiers
			assertThat(prompt).doesNotContain("fct_orders:");
			assertThat(prompt).doesNotContain("dim_customer:");
			assertThat(prompt).doesNotContain("• customer_id");
			assertThat(prompt).doesNotContain("• order_value");

			// Descriptions should still be present (for LLM understanding)
			assertThat(prompt).contains("Fact table for orders");
			assertThat(prompt).contains("Customer dimension");
			assertThat(prompt).contains("Order amount");

			// Remaining synonyms shown as "also:" (first one is the displayed name)
			assertThat(prompt).contains("(also: sales)");  // "orders" is displayed, "sales" is remaining
			assertThat(prompt).contains("(also: cust)");   // "customers" is displayed, "cust" is remaining
			assertThat(prompt).contains("also=amount");    // "value" is displayed, "amount" is remaining
			assertThat(prompt).contains("also=cust_name"); // "name" is displayed, "cust_name" is remaining
		}

		@Test
		@DisplayName("de-tokenizes simple SELECT from tokenized SQL")
		void deTokenizesSimpleSelect() {
			String ordersToken = tokenizedCatalog.getTableToken("fct_orders").orElseThrow();
			String orderValueToken = tokenizedCatalog.getColumnToken("fct_orders", "order_value").orElseThrow();

			String tokenizedSql = "SELECT " + orderValueToken + " FROM " + ordersToken;
			org.javai.springai.actions.sql.Query query = org.javai.springai.actions.sql.Query.fromSql(tokenizedSql, tokenizedCatalog);

			String sql = query.sqlString().toUpperCase();
			assertThat(sql).contains("ORDER_VALUE");
			assertThat(sql).contains("FCT_ORDERS");
			assertThat(sql).doesNotContain(ordersToken.toUpperCase());
			assertThat(sql).doesNotContain(orderValueToken.toUpperCase());
		}

		@Test
		@DisplayName("de-tokenizes JOIN query from tokenized SQL")
		void deTokenizesJoinQuery() {
			String ordersToken = tokenizedCatalog.getTableToken("fct_orders").orElseThrow();
			String customerToken = tokenizedCatalog.getTableToken("dim_customer").orElseThrow();
			String orderValueToken = tokenizedCatalog.getColumnToken("fct_orders", "order_value").orElseThrow();
			String customerIdToken = tokenizedCatalog.getColumnToken("fct_orders", "customer_id").orElseThrow();
			String customerPkToken = tokenizedCatalog.getColumnToken("dim_customer", "id").orElseThrow();
			String customerNameToken = tokenizedCatalog.getColumnToken("dim_customer", "customer_name").orElseThrow();

			String tokenizedSql = "SELECT o." + orderValueToken + ", c." + customerNameToken 
					+ " FROM " + ordersToken + " o JOIN " + customerToken + " c ON o." + customerIdToken 
					+ " = c." + customerPkToken;

			org.javai.springai.actions.sql.Query query = org.javai.springai.actions.sql.Query.fromSql(tokenizedSql, tokenizedCatalog);
			String sql = query.sqlString().toUpperCase();

			assertThat(sql).contains("O.ORDER_VALUE");
			assertThat(sql).contains("C.CUSTOMER_NAME");
			assertThat(sql).contains("FCT_ORDERS O");
			assertThat(sql).contains("DIM_CUSTOMER C");
			assertThat(sql).contains("O.CUSTOMER_ID = C.ID");
		}

		@Test
		@DisplayName("tokenizedSql() converts real names back to tokens")
		void tokenizedSqlConvertsBackToTokens() {
			// Start with canonical SQL
			String canonicalSql = "SELECT order_value, customer_id FROM fct_orders";
			org.javai.springai.actions.sql.Query query = org.javai.springai.actions.sql.Query.fromSql(canonicalSql, tokenizedCatalog);

			String tokenized = query.tokenizedSql();

			// Should contain tokens
			String ordersToken = tokenizedCatalog.getTableToken("fct_orders").orElseThrow();
			String orderValueToken = tokenizedCatalog.getColumnToken("fct_orders", "order_value").orElseThrow();
			String customerIdToken = tokenizedCatalog.getColumnToken("fct_orders", "customer_id").orElseThrow();

			assertThat(tokenized).contains(ordersToken);
			assertThat(tokenized).contains(orderValueToken);
			assertThat(tokenized).contains(customerIdToken);

			// Real names should NOT appear
			assertThat(tokenized).doesNotContain("fct_orders");
			assertThat(tokenized).doesNotContain("order_value");
			assertThat(tokenized).doesNotContain("customer_id");
		}

		@Test
		@DisplayName("FK references in prompt are tokenized")
		void fkReferencesAreTokenized() {
			SqlCatalogContextContributor contributor = new SqlCatalogContextContributor(tokenizedCatalog);
			String prompt = contributor.contribute(null).orElseThrow();

			// FK reference should use tokens, not real names
			// e.g., "fk:dt_abc123.c_def456" instead of "fk:dim_customer.id"
			String customerToken = tokenizedCatalog.getTableToken("dim_customer").orElseThrow();
			String customerIdToken = tokenizedCatalog.getColumnToken("dim_customer", "id").orElseThrow();

			assertThat(prompt).contains("fk:" + customerToken + "." + customerIdToken);
			assertThat(prompt).doesNotContain("fk:dim_customer.id");
		}
	}
}
