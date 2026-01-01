package org.javai.springai.scenarios.data_warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.javai.springai.actions.test.PlanAssertions.assertExecutionSuccess;
import static org.javai.springai.actions.test.PlanAssertions.assertPlanReady;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
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
import org.javai.springai.actions.internal.instrument.InvocationEmitter;
import org.javai.springai.actions.internal.instrument.InvocationEvent;
import org.javai.springai.actions.internal.instrument.InvocationEventType;
import org.javai.springai.actions.internal.instrument.InvocationListener;
import org.javai.springai.actions.sql.InMemorySqlCatalog;
import org.javai.springai.actions.sql.SqlCatalogContextContributor;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

class DataWarehouseApplicationScenarioTest {

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
				.promptContributor(new SqlCatalogContextContributor(catalog))
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
		
		// FWK-WEAK-006: Verify correct action was invoked AND wrong actions were NOT invoked
		assertThat(dataWarehouseActions.showSqlQueryInvoked()).isTrue();
		assertThat(dataWarehouseActions.runSqlQueryInvoked())
				.as("runSqlQuery should NOT be invoked when user asks to 'show' a query")
				.isFalse();
		assertThat(dataWarehouseActions.aggregateOrderValueInvoked())
				.as("aggregateOrderValue should NOT be invoked for simple select query")
				.isFalse();
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
		
		// FWK-WEAK-006: Verify correct action was invoked AND wrong actions were NOT invoked
		assertThat(dataWarehouseActions.runSqlQueryInvoked()).isTrue();
		assertThat(dataWarehouseActions.showSqlQueryInvoked())
				.as("showSqlQuery should NOT be invoked when user asks to 'run' a query")
				.isFalse();
		assertThat(dataWarehouseActions.aggregateOrderValueInvoked())
				.as("aggregateOrderValue should NOT be invoked for simple select query")
				.isFalse();
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
		
		// FWK-WEAK-006: Verify correct action was invoked AND wrong actions were NOT invoked
		assertThat(dataWarehouseActions.aggregateOrderValueInvoked()).isTrue();
		assertThat(dataWarehouseActions.showSqlQueryInvoked())
				.as("showSqlQuery should NOT be invoked for aggregate request")
				.isFalse();
		assertThat(dataWarehouseActions.runSqlQueryInvoked())
				.as("runSqlQuery should NOT be invoked for aggregate request")
				.isFalse();

		Optional<OrderValueQuery> optionalQuery = dataWarehouseActions.lastOrderValueQuery();
		assertThat(optionalQuery).isPresent();
		OrderValueQuery query = optionalQuery.get();
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
		String sql = dataWarehouseActions.lastQuery().orElseThrow().sqlString().toUpperCase();
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
		String sql = dataWarehouseActions.lastQuery().orElseThrow().sqlString().toUpperCase();
		assertThat(sql).contains("JOIN");
		assertThat(sql).contains("DIM_CUSTOMER");
		assertThat(sql).containsAnyOf("WHERE", "CUSTOMER_NAME", "SMITH");
	}

	@Test
	void joinMultipleDimensions() {
		// Request requires joining fact table to multiple dimensions
		// Use explicit table references to avoid LLM inventing columns
		String request = "show me order values with customer names from dim_customer and date from dim_date";

		ConversationTurnResult turn = conversationManager.converse(request, "multi-join-session");
		Plan plan = turn.plan();

		assertThat(plan).isNotNull();
		assertPlanReady(plan);

		PlanExecutionResult executed = executor.execute(plan);
		assertExecutionSuccess(executed);

		// Verify the SQL contains JOINs to both dimension tables
		String sql = dataWarehouseActions.lastQuery().orElseThrow().sqlString().toUpperCase();
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

		String sql = dataWarehouseActions.lastQuery().orElseThrow().sqlString().toUpperCase();
		assertThat(sql).contains("JOIN");
		assertThat(sql).contains("CUSTOMER_NAME");
		assertThat(sql).contains("ORDER_VALUE");
	}

	// ========== PENDING Parameter Flow Tests ==========
	// Tests that the LLM correctly uses PENDING when required parameters are unclear.
	// These tests use a dedicated planner with explicit PENDING guidance to ensure
	// reliable behavior across LLM invocations.

	@Nested
	@DisplayName("PENDING Parameter Flow Tests")
	class PendingParameterFlowTests {

		private Planner pendingAwarePlanner;
		private ConversationManager pendingAwareConversationManager;
		private DataWarehouseActions pendingTestActions;

		@BeforeEach
		void setUpPendingTests() {
			pendingTestActions = new DataWarehouseActions();

			// Create SQL catalog (same as main setup)
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.addTable("fct_orders", "Fact table for orders", "fact")
					.withSynonyms("fct_orders", "orders", "order", "sales")
					.addColumn("fct_orders", "customer_id", "FK to dim_customer", "string",
							new String[] { "fk:dim_customer.id" }, null)
					.addColumn("fct_orders", "order_value", "Order amount", "double",
							new String[] { "measure" }, null)
					.addTable("dim_customer", "Customer dimension", "dimension")
					.withSynonyms("dim_customer", "customers", "customer", "cust")
					.addColumn("dim_customer", "id", "PK", "string",
							new String[] { "pk" }, new String[] { "unique" })
					.addColumn("dim_customer", "customer_name", "Customer name", "string",
							new String[] { "attribute" }, null);

			// Persona with EXPLICIT PENDING guidance (modeled after stats_app scenario)
			PersonaSpec pendingAwarePersona = PersonaSpec.builder()
					.name("SQLDataWarehouseAssistant")
					.role("Assistant for data warehouse query planning and order value analysis")
					.principles(List.of(
							"Understand what the user wants to accomplish from a domain perspective",
							"Select the action whose purpose best matches the user's intent",
							"CRITICAL: The aggregateOrderValue action REQUIRES a date range (period with start and end dates)",
							"If user asks for aggregate/total order value WITHOUT specifying dates, use PENDING for the period parameter"))
					.constraints(List.of(
							"Only use the available actions",
							"NEVER invent or guess date ranges - if no dates provided, emit PENDING for period",
							"Use PENDING format: (PENDING paramName \"what date range?\")"))
					.build();

			pendingAwarePlanner = Planner.builder()
					.withChatClient(chatClient)
					.persona(pendingAwarePersona)
					.actions(pendingTestActions)
					.promptContributor(new SqlCatalogContextContributor(catalog))
					.addPromptContext("sql", catalog)
					.build();

			pendingAwareConversationManager = new ConversationManager(
					pendingAwarePlanner, new InMemoryConversationStateStore());
		}

		@Test
		@DisplayName("LLM returns PENDING when required date range is missing")
		void pendingWhenDateRangeMissing() {
			// User asks for aggregate without specifying dates - should trigger PENDING
			String ambiguousRequest = "calculate total order value for Mike";

			ConversationTurnResult turn = pendingAwareConversationManager.converse(
					ambiguousRequest, "pending-test-session");
			Plan plan = turn.plan();

			assertThat(plan).isNotNull();

			// The LLM should recognize that the aggregateOrderValue action requires a period
			// and return PENDING since no date range was provided
			assertThat(plan.status())
					.as("Plan should be PENDING when required date range is missing")
					.isEqualTo(PlanStatus.PENDING);

			// Verify pending params include the missing period
			assertThat(plan.pendingParameterNames())
					.as("Missing 'period' parameter should be identified")
					.anyMatch(name -> name.toLowerCase().contains("period")
							|| name.toLowerCase().contains("date")
							|| name.toLowerCase().contains("start")
							|| name.toLowerCase().contains("end"));

			// Verify no actions were invoked (plan wasn't executed)
			assertThat(pendingTestActions.aggregateOrderValueInvoked()).isFalse();
			assertThat(pendingTestActions.showSqlQueryInvoked()).isFalse();
			assertThat(pendingTestActions.runSqlQueryInvoked()).isFalse();
		}

		@Test
		@DisplayName("conversation recovers from PENDING after user provides missing info")
		void conversationRecoversFromPending() {
			// Turn 1: Ambiguous request
			String ambiguousRequest = "calculate total order value for Mike";
			ConversationTurnResult turn1 = pendingAwareConversationManager.converse(
					ambiguousRequest, "recovery-session");

			assertThat(turn1.plan().status())
					.as("First turn should be PENDING")
					.isEqualTo(PlanStatus.PENDING);

			// Turn 2: User provides the missing date range
			String clarification = "from January 1 2024 to January 31 2024";
			ConversationTurnResult turn2 = pendingAwareConversationManager.converse(
					clarification, "recovery-session");

			Plan plan2 = turn2.plan();
			assertThat(plan2).isNotNull();

			// After clarification, plan should be READY
			assertThat(plan2.status())
					.as("Plan should be READY after user provides missing date range")
					.isEqualTo(PlanStatus.READY);

			// Execute the plan
			PlanExecutionResult executed = executor.execute(plan2);
			assertExecutionSuccess(executed);

			// Verify the aggregate action was invoked with correct parameters
			assertThat(pendingTestActions.aggregateOrderValueInvoked()).isTrue();
			OrderValueQuery query = pendingTestActions.lastOrderValueQuery().orElseThrow();
			assertThat(query.customer_name()).isEqualTo("Mike");
			assertThat(query.period().start()).isEqualTo(LocalDate.parse("2024-01-01"));
			assertThat(query.period().end()).isEqualTo(LocalDate.parse("2024-01-31"));
		}
	}

	// ========== Instrumentation Tests (FWK-WEAK-007) ==========
	// Tests that InvocationEmitter correctly captures execution events

	@Test
	@DisplayName("execution events are captured via InvocationEmitter")
	void executionEventsCapturedViaEmitter() {
		// Reset actions for clean state
		dataWarehouseActions.reset();
		
		// Create a listener that captures all events
		List<InvocationEvent> capturedEvents = new CopyOnWriteArrayList<>();
		InvocationListener testListener = capturedEvents::add;
		
		// Create an emitter with our test listener
		InvocationEmitter emitter = InvocationEmitter.of("test-correlation-id", testListener);
		
		// Create executor with instrumentation
		DefaultPlanExecutor instrumentedExecutor = DefaultPlanExecutor.builder()
				.withEmitter(emitter)
				.build();

		// Execute a plan
		String request = "run query: select order_value from fct_orders";
		ConversationTurnResult turn = conversationManager.converse(request, "instrumentation-session");
		Plan plan = turn.plan();
		
		assertPlanReady(plan);
		
		PlanExecutionResult executed = instrumentedExecutor.execute(plan);
		assertExecutionSuccess(executed);
		
		// Verify events were captured
		assertThat(capturedEvents)
				.as("At least REQUESTED, STARTED, and SUCCEEDED events should be captured")
				.hasSizeGreaterThanOrEqualTo(3);
		
		// Verify event types
		List<InvocationEventType> eventTypes = capturedEvents.stream()
				.map(InvocationEvent::type)
				.toList();
		
		assertThat(eventTypes).contains(
				InvocationEventType.REQUESTED,
				InvocationEventType.STARTED,
				InvocationEventType.SUCCEEDED
		);
		
		// Verify correlation ID is propagated
		assertThat(capturedEvents)
				.allMatch(event -> "test-correlation-id".equals(event.correlationId()));
		
		// Verify action name is captured
		InvocationEvent succeededEvent = capturedEvents.stream()
				.filter(e -> e.type() == InvocationEventType.SUCCEEDED)
				.findFirst()
				.orElseThrow();
		
		assertThat(succeededEvent.name()).isEqualTo("runSqlQuery");
		
		// Verify duration is recorded for SUCCEEDED event
		assertThat(succeededEvent.durationMs())
				.as("Duration should be recorded for completed actions")
				.isNotNull()
				.isGreaterThanOrEqualTo(0L);
		
		// Verify attributes contain action ID
		assertThat(succeededEvent.attributes())
				.containsEntry("actionId", "runSqlQuery");
	}

	@Test
	@DisplayName("execution timing is captured accurately")
	void executionTimingCaptured() {
		dataWarehouseActions.reset();
		
		List<InvocationEvent> events = new CopyOnWriteArrayList<>();
		InvocationEmitter emitter = InvocationEmitter.of("timing-test", events::add);
		
		DefaultPlanExecutor instrumentedExecutor = DefaultPlanExecutor.builder()
				.withEmitter(emitter)
				.build();

		String request = "show me a query for customer names from dim_customer";
		ConversationTurnResult turn = conversationManager.converse(request, "timing-session");
		
		assertPlanReady(turn.plan());
		instrumentedExecutor.execute(turn.plan());
		
		// Find the SUCCEEDED event
		InvocationEvent succeeded = events.stream()
				.filter(e -> e.type() == InvocationEventType.SUCCEEDED)
				.findFirst()
				.orElseThrow();
		
		// Timing should be reasonable (not negative, not absurdly long)
		assertThat(succeeded.durationMs())
				.isNotNull()
				.isBetween(0L, 5000L); // Action should complete in under 5 seconds
		
		// Verify timestamp is present
		assertThat(succeeded.timestamp()).isNotNull();
	}

	// ========== Tokenization Unit Tests (Task 2.18) ==========
	// These tests don't require LLM access - they verify the tokenization infrastructure

	@org.junit.jupiter.api.Nested
	@DisplayName("Model Name Mapping Tests")
	class ModelNameMappingTests {

		private InMemorySqlCatalog modelNameCatalog;

		@org.junit.jupiter.api.BeforeEach
		void setUp() {
			// Create a catalog with model name mapping enabled
			modelNameCatalog = new InMemorySqlCatalog()
					.withModelNames(true)
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
		@DisplayName("catalog generates correct model name prefixes for DW schema")
		void catalogGeneratesCorrectModelNamePrefixes() {
			// Fact tables should have ft_ prefix when no synonyms defined
			String ordersModelName = modelNameCatalog.getTableModelName("fct_orders").orElseThrow();
			assertThat(ordersModelName).startsWith("ft_");

			// Dimension tables should have dt_ prefix when no synonyms defined
			String customerModelName = modelNameCatalog.getTableModelName("dim_customer").orElseThrow();
			assertThat(customerModelName).startsWith("dt_");

			String dateModelName = modelNameCatalog.getTableModelName("dim_date").orElseThrow();
			assertThat(dateModelName).startsWith("dt_");
		}

		@Test
		@DisplayName("model name catalog contributor uses synonyms as model names")
		void modelNameCatalogContributorUsesSynonyms() {
			// With model name mapping, first synonym becomes the displayed name
			// If no synonym defined, a generated identifier is used
			InMemorySqlCatalog catalogWithSynonyms = new InMemorySqlCatalog()
					.withModelNames(true)
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
		@DisplayName("resolves simple SELECT from model SQL to canonical names")
		void resolvesSimpleSelect() {
			String ordersModelName = modelNameCatalog.getTableModelName("fct_orders").orElseThrow();
			String orderValueModelName = modelNameCatalog.getColumnModelName("fct_orders", "order_value").orElseThrow();

			String modelSql = "SELECT " + orderValueModelName + " FROM " + ordersModelName;
			org.javai.springai.actions.sql.Query query = org.javai.springai.actions.sql.Query.fromSql(modelSql, modelNameCatalog);

			String sql = query.sqlString().toUpperCase();
			assertThat(sql).contains("ORDER_VALUE");
			assertThat(sql).contains("FCT_ORDERS");
			assertThat(sql).doesNotContain(ordersModelName.toUpperCase());
			assertThat(sql).doesNotContain(orderValueModelName.toUpperCase());
		}

		@Test
		@DisplayName("resolves JOIN query from model SQL to canonical names")
		void resolvesJoinQuery() {
			String ordersModelName = modelNameCatalog.getTableModelName("fct_orders").orElseThrow();
			String customerModelName = modelNameCatalog.getTableModelName("dim_customer").orElseThrow();
			String orderValueModelName = modelNameCatalog.getColumnModelName("fct_orders", "order_value").orElseThrow();
			String customerIdModelName = modelNameCatalog.getColumnModelName("fct_orders", "customer_id").orElseThrow();
			String customerPkModelName = modelNameCatalog.getColumnModelName("dim_customer", "id").orElseThrow();
			String customerNameModelName = modelNameCatalog.getColumnModelName("dim_customer", "customer_name").orElseThrow();

			String modelSql = "SELECT o." + orderValueModelName + ", c." + customerNameModelName 
					+ " FROM " + ordersModelName + " o JOIN " + customerModelName + " c ON o." + customerIdModelName 
					+ " = c." + customerPkModelName;

			org.javai.springai.actions.sql.Query query = org.javai.springai.actions.sql.Query.fromSql(modelSql, modelNameCatalog);
			String sql = query.sqlString().toUpperCase();

			assertThat(sql).contains("O.ORDER_VALUE");
			assertThat(sql).contains("C.CUSTOMER_NAME");
			assertThat(sql).contains("FCT_ORDERS O");
			assertThat(sql).contains("DIM_CUSTOMER C");
			assertThat(sql).contains("O.CUSTOMER_ID = C.ID");
		}

		@Test
		@DisplayName("modelSql() converts canonical names to model names")
		void modelSqlConvertsToModelNames() {
			// Start with canonical SQL
			String canonicalSql = "SELECT order_value, customer_id FROM fct_orders";
			org.javai.springai.actions.sql.Query query = org.javai.springai.actions.sql.Query.fromSql(canonicalSql, modelNameCatalog);

			String modelSql = query.modelSql();

			// Should contain model names
			String ordersModelName = modelNameCatalog.getTableModelName("fct_orders").orElseThrow();
			String orderValueModelName = modelNameCatalog.getColumnModelName("fct_orders", "order_value").orElseThrow();
			String customerIdModelName = modelNameCatalog.getColumnModelName("fct_orders", "customer_id").orElseThrow();

			assertThat(modelSql).contains(ordersModelName);
			assertThat(modelSql).contains(orderValueModelName);
			assertThat(modelSql).contains(customerIdModelName);

			// Real/canonical names should NOT appear
			assertThat(modelSql).doesNotContain("fct_orders");
			assertThat(modelSql).doesNotContain("order_value");
			assertThat(modelSql).doesNotContain("customer_id");
		}

		@Test
		@DisplayName("FK references in prompt use model names")
		void fkReferencesUseModelNames() {
			SqlCatalogContextContributor contributor = new SqlCatalogContextContributor(modelNameCatalog);
			String prompt = contributor.contribute(null).orElseThrow();

			// FK reference should use model names, not real/canonical names
			// e.g., "fk:dt_abc123.c_def456" instead of "fk:dim_customer.id"
			String customerModelName = modelNameCatalog.getTableModelName("dim_customer").orElseThrow();
			String customerIdModelName = modelNameCatalog.getColumnModelName("dim_customer", "id").orElseThrow();

			assertThat(prompt).contains("fk:" + customerModelName + "." + customerIdModelName);
			assertThat(prompt).doesNotContain("fk:dim_customer.id");
		}
	}
}
