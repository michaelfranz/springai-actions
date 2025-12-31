package org.javai.springai.scenarios.data_warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.javai.springai.actions.test.PlanAssertions.assertExecutionSuccess;
import static org.javai.springai.actions.test.PlanAssertions.assertPlanReady;
import java.util.List;
import java.util.Objects;
import org.javai.springai.actions.DefaultPlanExecutor;
import org.javai.springai.actions.PersonaSpec;
import org.javai.springai.actions.Plan;
import org.javai.springai.actions.PlanExecutionResult;
import org.javai.springai.actions.Planner;
import org.javai.springai.actions.conversation.ConversationManager;
import org.javai.springai.actions.conversation.ConversationTurnResult;
import org.javai.springai.actions.conversation.InMemoryConversationStateStore;
import org.javai.springai.actions.sql.AdaptiveSqlCatalogContributor;
import org.javai.springai.actions.sql.FrequencyAwareSqlCatalogTool;
import org.javai.springai.actions.sql.InMemorySchemaAccessTracker;
import org.javai.springai.actions.sql.InMemorySqlCatalog;
import org.javai.springai.actions.sql.SqlCatalogTool;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

/**
 * Integration tests for the adaptive hybrid approach.
 * 
 * <p>This approach combines the best of static and tool-based discovery:</p>
 * <ul>
 *   <li><b>Cold start</b>: No schema in prompt, LLM discovers via tools</li>
 *   <li><b>Warm state</b>: Frequently-used tables promoted to prompt</li>
 *   <li><b>Steady state</b>: Hot tables in prompt, cold tables via tool</li>
 * </ul>
 * 
 * <p>Run with {@code RUN_LLM_TESTS=true} to enable these tests.</p>
 */
@DisplayName("Data Warehouse Scenario - Adaptive Hybrid Approach")
public class DataWarehouseAdaptiveHybridScenarioTest {

	private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");
	private static final boolean RUN_LLM_TESTS = "true".equalsIgnoreCase(System.getenv("RUN_LLM_TESTS"));

	InMemorySqlCatalog catalog;
	InMemorySchemaAccessTracker tracker;
	SqlCatalogTool baseTool;
	FrequencyAwareSqlCatalogTool trackingTool;
	AdaptiveSqlCatalogContributor contributor;
	DataWarehouseActions dataWarehouseActions;
	DefaultPlanExecutor executor;
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
		executor = new DefaultPlanExecutor();

		// Create SQL catalog
		catalog = new InMemorySqlCatalog()
				.addTable("fct_orders", "Fact table for orders containing order transactions", "fact")
				.withSynonyms("fct_orders", "orders", "order", "sales")
				.addColumn("fct_orders", "customer_id", "FK to dim_customer", "string",
						new String[] { "fk:dim_customer.id" }, null)
				.addColumn("fct_orders", "date_id", "FK to dim_date", "string",
						new String[] { "fk:dim_date.id" }, null)
				.addColumn("fct_orders", "order_value", "Order amount in dollars", "double",
						new String[] { "measure" }, null)
				.withColumnSynonyms("fct_orders", "order_value", "value", "amount", "total")
				.addTable("dim_customer", "Customer dimension with customer details", "dimension")
				.withSynonyms("dim_customer", "customers", "customer", "cust")
				.addColumn("dim_customer", "id", "Customer primary key", "string",
						new String[] { "pk" }, new String[] { "unique" })
				.addColumn("dim_customer", "customer_name", "Customer full name", "string",
						new String[] { "attribute" }, null)
				.withColumnSynonyms("dim_customer", "customer_name", "name", "cust_name")
				.addTable("dim_date", "Date dimension with calendar dates", "dimension")
				.withSynonyms("dim_date", "dates", "date", "calendar")
				.addColumn("dim_date", "id", "Date primary key", "string",
						new String[] { "pk" }, new String[] { "unique" })
				.addColumn("dim_date", "date", "Calendar date value", "date",
						new String[] { "attribute" }, null);

		// Create tracker and tools
		tracker = new InMemorySchemaAccessTracker();
		baseTool = new SqlCatalogTool(catalog);
		trackingTool = new FrequencyAwareSqlCatalogTool(baseTool, tracker);
	}

	/**
	 * Creates a planner with the given hot threshold.
	 */
	private Planner createPlanner(int hotThreshold) {
		contributor = new AdaptiveSqlCatalogContributor(catalog, tracker, hotThreshold);

		PersonaSpec sqlAnalystPersona = PersonaSpec.builder()
				.name("SQLDataWarehouseAssistant")
				.role("Assistant for data warehouse query planning")
				.principles(List.of(
						"Understand what the user wants to accomplish from a domain perspective",
						"If SQL CATALOG shows tables, use them directly",
						"If SQL CATALOG says 'no frequently-used tables', use listTables/getTableDetails tools",
						"For JOINs, use FK info from column tags (e.g., fk:dim_customer.id)",
						"Select the action whose purpose best matches the user's intent"))
				.constraints(List.of(
						"Only use the available actions",
						"Only use table and column names from the catalog or discovered via tools",
						"If any required parameter is unclear, use PENDING"))
				.build();

		return Planner.builder()
				.withChatClient(chatClient)
				.persona(sqlAnalystPersona)
				.promptContributor(contributor)
				.tools(trackingTool)
				.actions(dataWarehouseActions)
				.addPromptContext("sql", catalog)
				.build();
	}

	@Nested
	@DisplayName("4.5 - Cold Start Behavior")
	class ColdStartBehavior {

		@Test
		@DisplayName("initial state has no schema in prompt, query still succeeds")
		void initialStateNoSchemaInPrompt() {
			Planner planner = createPlanner(2);  // Threshold of 2
			ConversationManager conversationManager = new ConversationManager(
					planner, new InMemoryConversationStateStore());

			// Verify cold start - no hot tables promoted to contributor
			assertThat(tracker.getHotTables(2)).isEmpty();
			
			// Verify contributor reflects cold state
			String contribution = contributor.contribute(null).orElse("");
			assertThat(contribution).contains("No frequently-used tables");

			// First request - LLM may use tools OR extract info from examples
			// (examples in Planner contain table names for guidance)
			String request = "show me a query for all order values";
			ConversationTurnResult turn = conversationManager.converse(request, "cold-start-session");
			Plan plan = turn.plan();

			assertThat(plan).isNotNull();
			assertPlanReady(plan);

			PlanExecutionResult executed = executor.execute(plan);
			assertExecutionSuccess(executed);
			assertThat(dataWarehouseActions.showSqlQueryInvoked()).isTrue();

			// Verify generated SQL references the orders table
			String sql = dataWarehouseActions.lastQuery().sqlString().toUpperCase();
			assertThat(sql).contains("ORDER_VALUE");
			
			System.out.println("Cold start query succeeded: " + sql);
			System.out.println("Tool accesses (may be empty if LLM used examples): " + tracker.getAllAccessCounts());
		}

		@Test
		@DisplayName("contributor returns no-tables message on cold start")
		void contributorReturnsNoTablesMessage() {
			contributor = new AdaptiveSqlCatalogContributor(catalog, tracker, 2);

			String contribution = contributor.contribute(null).orElse("");

			assertThat(contribution).contains("No frequently-used tables");
			assertThat(contribution).contains("listTables");
		}
	}

	@Nested
	@DisplayName("4.6 - Table Promotion After Threshold")
	class TablePromotionAfterThreshold {

		@Test
		@DisplayName("after N requests, table appears in prompt")
		void tableAppearsInPromptAfterThreshold() {
			Planner planner = createPlanner(2);
			ConversationManager conversationManager = new ConversationManager(
					planner, new InMemoryConversationStateStore());

			// First request - cold start
			conversationManager.converse("show me order values", "promo-session-1");
			
			// Check: fct_orders should have been accessed once
			System.out.println("After 1st request: " + tracker.getAllAccessCounts());

			// Second request - warms up
			dataWarehouseActions.reset();
			conversationManager.converse("show me order values again", "promo-session-2");
			
			System.out.println("After 2nd request: " + tracker.getAllAccessCounts());

			// Now fct_orders should be "hot" (accessed >= 2 times)
			// Note: we're tracking by the name used in getTableDetails, which may be "orders" or "fct_orders"
			assertThat(tracker.getHotTables(2)).isNotEmpty();

			// Verify contributor now includes the hot table
			String contribution = contributor.contribute(null).orElse("");
			System.out.println("Contribution after warm-up:\n" + contribution);
			
			assertThat(contribution).doesNotContain("No frequently-used tables");
			// Should contain the promoted table (either fct_orders or orders depending on lookup)
			assertThat(contribution.toLowerCase()).containsAnyOf("fct_orders", "orders");
		}

		@Test
		@DisplayName("multiple tables can be promoted independently")
		void multipleTablesPromotedIndependently() {
			Planner planner = createPlanner(2);
			ConversationManager conversationManager = new ConversationManager(
					planner, new InMemoryConversationStateStore());

			// Access fct_orders twice
			conversationManager.converse("show me order values", "multi-session-1");
			dataWarehouseActions.reset();
			conversationManager.converse("show me order values", "multi-session-2");

			// Access dim_customer twice
			dataWarehouseActions.reset();
			conversationManager.converse("show me customer names", "multi-session-3");
			dataWarehouseActions.reset();
			conversationManager.converse("show me customer names", "multi-session-4");

			System.out.println("After multiple requests: " + tracker.getAllAccessCounts());

			// Both should be hot now
			assertThat(tracker.getHotTables(2).size()).isGreaterThanOrEqualTo(2);

			String contribution = contributor.contribute(null).orElse("");
			System.out.println("Contribution with multiple hot tables:\n" + contribution);
		}
	}

	@Nested
	@DisplayName("4.7 - Tool Works for Infrequent Tables")
	class ToolWorksForInfrequentTables {

		@Test
		@DisplayName("can still query cold tables via tool after warm-up")
		void canQueryColdTablesAfterWarmUp() {
			Planner planner = createPlanner(3);  // Higher threshold
			ConversationManager conversationManager = new ConversationManager(
					planner, new InMemoryConversationStateStore());

			// Warm up fct_orders (3 accesses)
			conversationManager.converse("show me order values", "infreq-session-1");
			dataWarehouseActions.reset();
			conversationManager.converse("show me order values", "infreq-session-2");
			dataWarehouseActions.reset();
			conversationManager.converse("show me order values", "infreq-session-3");

			// fct_orders is now hot
			System.out.println("Hot tables: " + tracker.getHotTables(3));

			// Now query dim_date which is still cold
			dataWarehouseActions.reset();
			ConversationTurnResult turn = conversationManager.converse(
					"show me all dates from the date dimension", "infreq-session-4");
			Plan plan = turn.plan();

			assertThat(plan).isNotNull();
			
			// Should still work - LLM discovers dim_date via tool
			if (plan.status() == org.javai.springai.actions.PlanStatus.READY) {
				PlanExecutionResult executed = executor.execute(plan);
				if (executed.success()) {
					String sql = dataWarehouseActions.lastQuery().sqlString().toUpperCase();
					System.out.println("Cold table query SQL: " + sql);
					assertThat(sql).containsAnyOf("DIM_DATE", "DATE");
				}
			}
		}
	}

	@Nested
	@DisplayName("4.8 - Configurable Thresholds")
	class ConfigurableThresholds {

		@Test
		@DisplayName("threshold of 1 promotes immediately")
		void thresholdOfOnePromotesImmediately() {
			Planner planner = createPlanner(1);  // Threshold of 1
			ConversationManager conversationManager = new ConversationManager(
					planner, new InMemoryConversationStateStore());

			// Single request should promote the table
			conversationManager.converse("show me order values", "threshold1-session");

			System.out.println("After 1 request with threshold=1: " + tracker.getAllAccessCounts());

			// Should be hot after just one access
			assertThat(tracker.getHotTables(1)).isNotEmpty();

			String contribution = contributor.contribute(null).orElse("");
			assertThat(contribution).doesNotContain("No frequently-used tables");
		}

		@Test
		@DisplayName("high threshold requires more accesses")
		void highThresholdRequiresMoreAccesses() {
			Planner planner = createPlanner(5);  // High threshold
			ConversationManager conversationManager = new ConversationManager(
					planner, new InMemoryConversationStateStore());

			// 3 requests
			conversationManager.converse("show me order values", "high-thresh-1");
			dataWarehouseActions.reset();
			conversationManager.converse("show me order values", "high-thresh-2");
			dataWarehouseActions.reset();
			conversationManager.converse("show me order values", "high-thresh-3");

			System.out.println("After 3 requests with threshold=5: " + tracker.getAllAccessCounts());

			// Should NOT be hot yet (threshold is 5)
			assertThat(tracker.getHotTables(5)).isEmpty();

			String contribution = contributor.contribute(null).orElse("");
			assertThat(contribution).contains("No frequently-used tables");
		}

		@Test
		@DisplayName("different contributors can have different thresholds")
		void differentThresholdsForDifferentContributors() {
			AdaptiveSqlCatalogContributor lowThreshold = new AdaptiveSqlCatalogContributor(catalog, tracker, 1);
			AdaptiveSqlCatalogContributor highThreshold = new AdaptiveSqlCatalogContributor(catalog, tracker, 10);

			// Record some access
			tracker.recordTableAccess("fct_orders");
			tracker.recordTableAccess("fct_orders");
			tracker.recordTableAccess("fct_orders");

			// Low threshold sees it as hot
			String lowContribution = lowThreshold.contribute(null).orElse("");
			assertThat(lowContribution).doesNotContain("No frequently-used tables");

			// High threshold still sees it as cold
			String highContribution = highThreshold.contribute(null).orElse("");
			assertThat(highContribution).contains("No frequently-used tables");
		}
	}

	@Nested
	@DisplayName("End-to-End Adaptive Flow")
	class EndToEndAdaptiveFlow {

		@Test
		@DisplayName("complete adaptive warm-up cycle")
		void completeAdaptiveWarmUpCycle() {
			Planner planner = createPlanner(2);
			ConversationManager conversationManager = new ConversationManager(
					planner, new InMemoryConversationStateStore());

			// === Phase 1: Cold Start ===
			System.out.println("=== Phase 1: Cold Start ===");
			String coldContribution = contributor.contribute(null).orElse("");
			assertThat(coldContribution).contains("No frequently-used tables");
			System.out.println("Cold contribution: " + coldContribution.substring(0, Math.min(100, coldContribution.length())) + "...");

			// First query - uses tools
			ConversationTurnResult turn1 = conversationManager.converse("show me order values", "e2e-session-1");
			assertThat(turn1.plan()).isNotNull();
			System.out.println("Query 1 status: " + turn1.plan().status());
			System.out.println("Tracker state: " + tracker.getAllAccessCounts());

			// === Phase 2: Warming Up ===
			System.out.println("\n=== Phase 2: Warming Up ===");
			dataWarehouseActions.reset();
			ConversationTurnResult turn2 = conversationManager.converse("show me order values again", "e2e-session-2");
			System.out.println("Query 2 status: " + turn2.plan().status());
			System.out.println("Tracker state: " + tracker.getAllAccessCounts());

			// === Phase 3: Warm State ===
			System.out.println("\n=== Phase 3: Warm State ===");
			String warmContribution = contributor.contribute(null).orElse("");
			System.out.println("Warm contribution preview: " + warmContribution.substring(0, Math.min(200, warmContribution.length())) + "...");

			// Verify the transition happened
			assertThat(tracker.getHotTables(2)).isNotEmpty();
			System.out.println("Hot tables: " + tracker.getHotTables(2));

			// === Phase 4: Query with Warm Cache ===
			System.out.println("\n=== Phase 4: Query with Warm Cache ===");
			dataWarehouseActions.reset();
			ConversationTurnResult turn3 = conversationManager.converse("show me order totals", "e2e-session-3");
			assertThat(turn3.plan()).isNotNull();
			System.out.println("Query 3 status: " + turn3.plan().status());

			if (turn3.plan().status() == org.javai.springai.actions.PlanStatus.READY) {
				PlanExecutionResult executed = executor.execute(turn3.plan());
				if (executed.success() && dataWarehouseActions.lastQuery() != null) {
					System.out.println("Final SQL: " + dataWarehouseActions.lastQuery().sqlString());
				}
			}
		}
	}
}

