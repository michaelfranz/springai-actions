package org.javai.springai.scenarios.data_warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.javai.springai.actions.test.PlanAssertions.assertPlanReady;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.javai.springai.actions.PersonaSpec;
import org.javai.springai.actions.PlanStatus;
import org.javai.springai.actions.Planner;
import org.javai.springai.actions.conversation.ConversationManager;
import org.javai.springai.actions.conversation.ConversationState;
import org.javai.springai.actions.conversation.ConversationStateConfig;
import org.javai.springai.actions.conversation.ConversationStateSerializer;
import org.javai.springai.actions.conversation.ConversationTurnResult;
import org.javai.springai.actions.conversation.JsonConversationStateSerializer;
import org.javai.springai.actions.conversation.PayloadTypeRegistry;
import org.javai.springai.actions.conversation.WorkingContext;
import org.javai.springai.actions.sql.InMemorySqlCatalog;
import org.javai.springai.actions.sql.Query;
import org.javai.springai.actions.sql.SqlCatalogContextContributor;
import org.javai.springai.actions.sql.SqlQueryPayload;
import org.javai.springai.actions.sql.SqlUserMessageAugmenter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Demonstrates multi-turn conversation patterns for data warehouse queries.
 * 
 * <h2>What This Test Demonstrates</h2>
 * <ul>
 *   <li>Blob-based conversation state persistence (application-owned)</li>
 *   <li>WorkingContext tracking across conversation turns</li>
 *   <li>Query refinement ("filter that by...", "add column...")</li>
 *   <li>Context expiry for "start over" scenarios</li>
 * </ul>
 * 
 * <h2>Application Pattern</h2>
 * <pre>{@code
 * // Application stores blobs in its own database
 * byte[] priorBlob = sessionRepository.getConversationBlob(sessionId);
 * 
 * // Process turn
 * ConversationTurnResult result = conversationManager.converse(userMessage, priorBlob);
 * 
 * // Execute plan if ready
 * if (result.status() == PlanStatus.READY) {
 *     executor.execute(result.plan());
 * }
 * 
 * // Store updated blob
 * sessionRepository.saveConversationBlob(sessionId, result.blob());
 * }</pre>
 */
@DisplayName("Data Warehouse Multi-Turn Conversation Scenario")
class DataWarehouseMultiTurnScenarioTest extends AbstractDataWarehouseScenarioTest {

	// Multi-turn specific infrastructure
	private PayloadTypeRegistry typeRegistry;
	private ConversationStateSerializer serializer;

	// SQL Explorer actions - focused action set for SQL exploration scenarios
	private SqlExplorerActions sqlExplorerActions;

	// Simulates application's session storage
	private Map<String, byte[]> applicationBlobStore;

	@Override
	protected InMemorySqlCatalog createDefaultCatalog() {
		// Extended catalog with region column for multi-turn tests
		return new InMemorySqlCatalog()
				.addTable("fct_orders", "Fact table containing order transactions", "fact")
				.withSynonyms("fct_orders", "orders", "order", "sales")
				.addColumn("fct_orders", "customer_id", "FK to dim_customer", "string",
						new String[] { "fk:dim_customer.id" }, null)
				.addColumn("fct_orders", "date_id", "FK to dim_date", "string",
						new String[] { "fk:dim_date.id" }, null)
				.addColumn("fct_orders", "order_value", "Order amount in dollars", "double",
						new String[] { "measure" }, null)
				.withColumnSynonyms("fct_orders", "order_value", "value", "amount", "total")
				.addColumn("fct_orders", "region", "Geographic region", "string",
						new String[] { "attribute" }, null)
				.addTable("dim_customer", "Customer dimension table", "dimension")
				.withSynonyms("dim_customer", "customers", "customer")
				.addColumn("dim_customer", "id", "Customer primary key", "string",
						new String[] { "pk" }, new String[] { "unique" })
				.addColumn("dim_customer", "customer_name", "Customer full name", "string",
						new String[] { "attribute" }, null)
				.withColumnSynonyms("dim_customer", "customer_name", "name", "cust_name")
				.addTable("dim_date", "Date dimension table", "dimension")
				.withSynonyms("dim_date", "dates", "date")
				.addColumn("dim_date", "id", "Date ID (YYYYMMDD)", "string",
						new String[] { "pk" }, new String[] { "unique" })
				.addColumn("dim_date", "date", "Calendar date", "date",
						new String[] { "attribute" }, null)
				.withDialect(Query.Dialect.ANSI);
	}

	@Override
	protected PersonaSpec createDefaultPersona() {
		return PersonaSpec.builder()
				.name("SQLSelectQueryBuilder")
				.role("Expert at building SQL SELECT statements for data exploration. This is your ONLY role.")
				.principles(List.of(
						"You build SELECT queries - never INSERT, UPDATE, DELETE, or any other statement type",
						"'add X' or 'include X' means add column X to the SELECT clause",
						"'filter by X' means add a WHERE condition",
						"When CURRENT QUERY CONTEXT shows an existing query, modify it based on the user's request"))
				.constraints(List.of(
						"ONLY generate SELECT statements",
						"The query parameter format is: {\"query\": {\"sql\": \"SELECT ...\"}}",
						"Use exact table/column names from the SQL CATALOG"))
				.styleGuidance(List.of(
						"EXAMPLE - User says 'include customer_name' when CURRENT QUERY is 'SELECT order_value FROM fct_orders':",
						"Response: {\"message\":\"Added customer_name.\",\"steps\":[{\"actionId\":\"showSqlQuery\",\"description\":\"...\",\"parameters\":{\"query\":{\"sql\":\"SELECT order_value, customer_name FROM fct_orders JOIN dim_customer ON fct_orders.customer_id = dim_customer.id\"}}}]}"))
				.build();
	}

	@Override
	protected void initializePlanner() {
		PersonaSpec persona = createDefaultPersona();

		// Use focused SQL Explorer actions - no aggregateOrderValue to avoid semantic confusion
		sqlExplorerActions = new SqlExplorerActions();

		// Note: SqlWorkingContextContributor is deprecated. Working context is now
		// included in the user message via SqlUserMessageAugmenter (registered below).
		// This is more effective because LLMs pay more attention to user messages.
		planner = Planner.builder()
				.defaultChatClient(capableChatClient, 2, CAPABLE_CHAT_MODEL_VERSION)  // gpt-4o
				.fallbackChatClient(mostCapableChatClient, 2, MOST_CAPABLE_CHAT_MODEL_VERSION)
				.persona(persona)
				.actions(sqlExplorerActions)
				.addPromptContext("sql", catalog)
				.promptContributor(new SqlCatalogContextContributor(catalog))
				.promptContribution("""
					⚠️ CRITICAL RULES FOR SQL ACTIONS:
					For showSqlQuery and runSqlQuery, you MUST generate the SQL yourself.
					- The user message contains the current query (if any) and the modification request
					- Return: {"query": {"sql": "SELECT ..."}}
					- NEVER use PENDING for the query parameter
					- NEVER invent parameters like 'table' or 'columns' - only 'query' exists
					""")
				.build();

		// Set up blob-based conversation infrastructure
		typeRegistry = new PayloadTypeRegistry();
		typeRegistry.register(SqlQueryPayload.CONTEXT_TYPE, SqlQueryPayload.class);

		serializer = new JsonConversationStateSerializer();

		ConversationStateConfig config = ConversationStateConfig.builder()
				.maxHistorySize(5)
				.build();

		conversationManager = new ConversationManager(planner, serializer, typeRegistry, config)
				.registerAugmenter(new SqlUserMessageAugmenter());

		// Application's simulated blob store
		applicationBlobStore = new HashMap<>();
	}

	// ========================================================================
	// Unit Tests (No LLM)
	// ========================================================================

	@Nested
	@DisplayName("Blob Persistence Pattern (Unit Tests)")
	class BlobPersistencePattern {

		@Test
		@DisplayName("serializer round-trips conversation state")
		void serializerRoundTripsState() {
			PayloadTypeRegistry registry = new PayloadTypeRegistry();
			registry.register("test.context", String.class);

			ConversationStateSerializer testSerializer = new JsonConversationStateSerializer();

			// Create state with working context
			ConversationState state =
					ConversationState.initial("show me orders")
							.withWorkingContext(WorkingContext.of("test.context", "test payload"), 10);

			// Serialize
			byte[] blob = testSerializer.serialize(state, registry);
			assertThat(blob).isNotNull();
			assertThat(blob.length).isGreaterThan(10);

			// Deserialize
			var restored = testSerializer.deserialize(blob, registry);
			assertThat(restored.originalInstruction()).isEqualTo("show me orders");
			assertThat(restored.workingContext()).isNotNull();
			assertThat(restored.workingContext().contextType()).isEqualTo("test.context");
			assertThat(restored.workingContext().payload()).isEqualTo("test payload");
		}

		@Test
		@DisplayName("readable JSON shows state for debugging")
		void readableJsonShowsState() {
			PayloadTypeRegistry registry = new PayloadTypeRegistry();
			registry.register("test.context", String.class);

			ConversationStateSerializer testSerializer = new JsonConversationStateSerializer();

			var state = ConversationState.initial("test query")
					.withWorkingContext(WorkingContext.of("test.context", "payload"), 10);

			byte[] blob = testSerializer.serialize(state, registry);
			String json = testSerializer.toReadableJson(blob);

			assertThat(json).contains("originalInstruction");
			assertThat(json).contains("test query");
			assertThat(json).contains("workingContext");
			assertThat(json).contains("test.context");
		}

		@Test
		@DisplayName("expire() creates empty state with blob")
		void expireCreatesEmptyState() {
			ConversationTurnResult expired = conversationManager.expire();

			assertThat(expired.state().originalInstruction()).isNull();
			assertThat(expired.state().workingContext()).isNull();
			assertThat(expired.blob()).isNotNull();
		}

		@Test
		@DisplayName("history is capped at configured size")
		void historyCappedAtConfiguredSize() {
			PayloadTypeRegistry registry = new PayloadTypeRegistry();
			registry.register("test", String.class);

			var state = ConversationState.initial("start");

			// Add 7 contexts with max history of 5
			for (int i = 1; i <= 7; i++) {
				state = state.withWorkingContext(
						WorkingContext.of("test", "context-" + i), 5);
			}

			// Current context is 7, history has 5 (contexts 2-6)
			assertThat(state.workingContext().payload()).isEqualTo("context-7");
			assertThat(state.turnHistory()).hasSize(5);
			assertThat(state.turnHistory().get(0).payload()).isEqualTo("context-2");
			assertThat(state.turnHistory().get(4).payload()).isEqualTo("context-6");
		}

		@Test
		@DisplayName("config defaults have expected values")
		void configDefaultsHaveExpectedValues() {
			ConversationStateConfig config = ConversationStateConfig.defaults();
			
			assertThat(config.maxHistorySize()).isEqualTo(10);
			assertThat(config.augmentUserMessage()).isTrue();
			assertThat(config.contextPrefix()).isEqualTo("Current state:");
			assertThat(config.requestPrefix()).isEqualTo("User request:");
		}

		@Test
		@DisplayName("config builder allows custom values")
		void configBuilderAllowsCustomValues() {
			ConversationStateConfig config = ConversationStateConfig.builder()
					.maxHistorySize(20)
					.augmentUserMessage(false)
					.contextPrefix("Current query:")
					.requestPrefix("Modify:")
					.build();
			
			assertThat(config.maxHistorySize()).isEqualTo(20);
			assertThat(config.augmentUserMessage()).isFalse();
			assertThat(config.contextPrefix()).isEqualTo("Current query:");
			assertThat(config.requestPrefix()).isEqualTo("Modify:");
		}

		@Test
		@DisplayName("SqlUserMessageAugmenter formats with config prefix")
		void sqlAugmenterFormatsWithConfigPrefix() {
			SqlUserMessageAugmenter augmenter = new SqlUserMessageAugmenter();
			SqlQueryPayload payload = SqlQueryPayload.fromModelSql("SELECT * FROM orders");
			WorkingContext<SqlQueryPayload> ctx = WorkingContext.of(SqlQueryPayload.CONTEXT_TYPE, payload);
			
			// With config prefix
			ConversationStateConfig config = ConversationStateConfig.builder()
					.contextPrefix("Active SQL:")
					.build();
			
			var formatted = augmenter.formatForUserMessage(ctx, config);
			assertThat(formatted).isPresent();
			assertThat(formatted.get()).isEqualTo("Active SQL: SELECT * FROM orders");
		}

		@Test
		@DisplayName("SqlUserMessageAugmenter uses constructor prefix without config")
		void sqlAugmenterUsesConstructorPrefixWithoutConfig() {
			SqlUserMessageAugmenter augmenter = new SqlUserMessageAugmenter("Base query:");
			SqlQueryPayload payload = SqlQueryPayload.fromModelSql("SELECT id FROM users");
			WorkingContext<SqlQueryPayload> ctx = WorkingContext.of(SqlQueryPayload.CONTEXT_TYPE, payload);
			
			var formatted = augmenter.formatForUserMessage(ctx);
			assertThat(formatted).isPresent();
			assertThat(formatted.get()).isEqualTo("Base query: SELECT id FROM users");
		}
	}

	// ========================================================================
	// LLM Integration Tests
	// ========================================================================

	@Nested
	@DisplayName("Multi-Turn Query Refinement")
	@EnabledIfEnvironmentVariable(named = "RUN_LLM_TESTS", matches = "true")
	class MultiTurnQueryRefinement {

		/**
		 * Simulates the application's session handling.
		 */
		private ConversationTurnResult processUserMessage(String sessionId, String message) {
			byte[] priorBlob = applicationBlobStore.get(sessionId);
			
			// Framework automatically augments user message with working context
			ConversationTurnResult result = conversationManager.converse(message, priorBlob);

			if (result.plan().status() == PlanStatus.READY) {
				executor.execute(result.plan());
				
				// Store working context if either SQL action was invoked
				boolean sqlActionInvoked = sqlExplorerActions.showSqlQueryInvoked() || sqlExplorerActions.runSqlQueryInvoked();
				if (sqlActionInvoked && sqlExplorerActions.lastQuery().isPresent()) {
					Query query = sqlExplorerActions.lastQuery().get();
					SqlQueryPayload payload = SqlQueryPayload.fromQuery(query);
					
					var updatedState = result.state().withWorkingContext(
							WorkingContext.of(SqlQueryPayload.CONTEXT_TYPE, payload),
							conversationManager.config().maxHistorySize());
					
					byte[] updatedBlob = serializer.serialize(updatedState, typeRegistry);
					result = new ConversationTurnResult(
							result.plan(), updatedState, updatedBlob,
							result.pendingParams(), result.providedParams());
				}
			}

			applicationBlobStore.put(sessionId, result.blob());
			return result;
		}

		@Test
		@DisplayName("Turn 1: Initial query formulation")
		void turn1InitialQuery() {
			String sessionId = "multi-turn-session-1";
			sqlExplorerActions.reset();

			ConversationTurnResult result = processUserMessage(sessionId,
					"generate a SQL query to show order_value and customer_name from fct_orders joined with dim_customer");

			assertPlanReady(result.plan());
			// Either showSqlQuery or runSqlQuery is acceptable - both generate SQL
			assertThat(sqlExplorerActions.showSqlQueryInvoked() || sqlExplorerActions.runSqlQueryInvoked())
					.as("Expected either showSqlQuery or runSqlQuery to be invoked")
					.isTrue();

			Query query = sqlExplorerActions.lastQuery().orElseThrow();
			String sql = query.sqlString().toUpperCase();
			assertThat(sql).contains("FCT_ORDERS");
			assertThat(sql).contains("DIM_CUSTOMER");

			assertThat(applicationBlobStore.get(sessionId)).isNotNull();

			log.info("Turn 1 SQL: {}", query.sqlString());
			log.info("Turn 1 Blob (readable): {}", conversationManager.toReadableJson(result.blob()));
		}

		@Test
		@DisplayName("Two-turn conversation: query then refine")
		void twoTurnConversation() {
			String sessionId = "multi-turn-session-2";
			sqlExplorerActions.reset();

			// Turn 1: Explicit SQL generation request
			ConversationTurnResult turn1 = processUserMessage(sessionId,
					"generate a SQL query to show order_value and customer_name from fct_orders joined with dim_customer");
			assertPlanReady(turn1.plan());
			
			// Either showSqlQuery or runSqlQuery is acceptable - both generate SQL
			assertThat(sqlExplorerActions.showSqlQueryInvoked() || sqlExplorerActions.runSqlQueryInvoked())
					.as("Expected either showSqlQuery or runSqlQuery to be invoked")
					.isTrue();
			String turn1Sql = sqlExplorerActions.lastQuery().orElseThrow().sqlString();
			log.info("Turn 1 SQL: {}", turn1Sql);

			sqlExplorerActions.reset();

			// Turn 2: Modify the existing query
			ConversationTurnResult turn2 = processUserMessage(sessionId,
					"modify the query to add WHERE region = 'West'");

			assertPlanReady(turn2.plan());
			// Either showSqlQuery or runSqlQuery is acceptable - both generate SQL
			assertThat(sqlExplorerActions.showSqlQueryInvoked() || sqlExplorerActions.runSqlQueryInvoked())
					.as("Expected either showSqlQuery or runSqlQuery to be invoked for turn 2")
					.isTrue();

			String turn2Sql = sqlExplorerActions.lastQuery().orElseThrow().sqlString();
			log.info("Turn 2 SQL: {}", turn2Sql);

			assertThat(turn2Sql.toUpperCase()).contains("REGION");
			assertThat(turn2Sql.toUpperCase()).contains("WEST");

			var state = conversationManager.fromBlob(turn2.blob());
			log.info("Turn 2 State has working context: {}", state.hasWorkingContext());
			log.info("Turn 2 History size: {}", state.turnHistory().size());
		}

		@Test
		@DisplayName("Session expiry clears context")
		void sessionExpiryClearsContext() {
			String sessionId = "expiry-session";
			sqlExplorerActions.reset();

			processUserMessage(sessionId, "show me all orders");
			assertThat(applicationBlobStore.get(sessionId)).isNotNull();

			ConversationTurnResult expired = conversationManager.expire();
			applicationBlobStore.put(sessionId, expired.blob());

			var state = conversationManager.fromBlob(applicationBlobStore.get(sessionId));
			assertThat(state.originalInstruction()).isNull();
			assertThat(state.workingContext()).isNull();
			assertThat(state.turnHistory()).isEmpty();

			log.info("Expired state: {}", conversationManager.toReadableJson(expired.blob()));
		}
	}

	@Nested
	@DisplayName("Application Integration Pattern")
	@EnabledIfEnvironmentVariable(named = "RUN_LLM_TESTS", matches = "true")
	class ApplicationIntegrationPattern {

		@Test
		@DisplayName("demonstrates full application workflow")
		void fullApplicationWorkflow() {
			PayloadTypeRegistry registry = new PayloadTypeRegistry();
			registry.register(SqlQueryPayload.CONTEXT_TYPE, SqlQueryPayload.class);

			ConversationStateSerializer localSerializer = new JsonConversationStateSerializer();
			ConversationStateConfig config = ConversationStateConfig.builder()
					.maxHistorySize(10)
					.build();

			ConversationManager manager = new ConversationManager(
					planner, localSerializer, registry, config);

			byte[] blob1 = null;
			ConversationTurnResult result1 = manager.converse("show me total order values", blob1);

			if (result1.status() == PlanStatus.READY) {
				executor.execute(result1.plan());
			}

			byte[] storedBlob = result1.blob();
			log.info("Request 1 complete. Blob size: {} bytes", storedBlob.length);

			sqlExplorerActions.reset();

			byte[] blob2 = storedBlob;
			ConversationTurnResult result2 = manager.converse("group that by customer", blob2);

			if (result2.status() == PlanStatus.READY) {
				executor.execute(result2.plan());
			}

			storedBlob = result2.blob();
			log.info("Request 2 complete. Blob size: {} bytes", storedBlob.length);

			String readable = manager.toReadableJson(storedBlob);
			log.info("Final state (readable JSON): {}", readable);

			ConversationTurnResult expired = manager.expire();
			var finalState = manager.fromBlob(expired.blob());
			assertThat(finalState.workingContext()).isNull();
			log.info("Session cancelled/expired");
		}
	}

	// ========================================================================
	// Query Refinement Patterns
	// ========================================================================

	@Nested
	@DisplayName("Query Refinement Patterns")
	@EnabledIfEnvironmentVariable(named = "RUN_LLM_TESTS", matches = "true")
	class QueryRefinementPatterns {

		private ConversationTurnResult processAndTrack(String sessionId, String message) {
			byte[] priorBlob = applicationBlobStore.get(sessionId);
			
			// Framework automatically augments user message with working context
			ConversationTurnResult result = conversationManager.converse(message, priorBlob);

			if (result.plan().status() == PlanStatus.READY) {
				executor.execute(result.plan());
				
				// Store working context if either SQL action was invoked
				boolean sqlActionInvoked = sqlExplorerActions.showSqlQueryInvoked() || sqlExplorerActions.runSqlQueryInvoked();
				if (sqlActionInvoked && sqlExplorerActions.lastQuery().isPresent()) {
					Query query = sqlExplorerActions.lastQuery().orElseThrow();
					SqlQueryPayload payload = SqlQueryPayload.fromQuery(query);
					
					var updatedState = result.state().withWorkingContext(
							WorkingContext.of(SqlQueryPayload.CONTEXT_TYPE, payload),
							conversationManager.config().maxHistorySize());
					
					byte[] updatedBlob = serializer.serialize(updatedState, typeRegistry);
					result = new ConversationTurnResult(
							result.plan(), updatedState, updatedBlob,
							result.pendingParams(), result.providedParams());
				}
			}

			applicationBlobStore.put(sessionId, result.blob());
			return result;
		}

		@Test
		@DisplayName("Filter refinement - 'filter that by region = West'")
		void filterRefinement() {
			String sessionId = "filter-refinement";
			sqlExplorerActions.reset();

			// Turn 1: Explicit SQL generation
			ConversationTurnResult turn1 = processAndTrack(sessionId, 
					"generate a SQL query to show order_value from fct_orders");
			assertPlanReady(turn1.plan());
			String sql1 = sqlExplorerActions.lastQuery().orElseThrow().sqlString();
			log.info("Turn 1 SQL: {}", sql1);

			sqlExplorerActions.reset();

			// Turn 2: Modify with filter
			ConversationTurnResult turn2 = processAndTrack(sessionId, 
					"modify the query to add WHERE region = 'West'");
			assertPlanReady(turn2.plan());
			
			String sql2 = sqlExplorerActions.lastQuery().orElseThrow().sqlString();
			log.info("Turn 2 SQL: {}", sql2);

			assertThat(sql2.toUpperCase()).contains("WHERE");
			assertThat(sql2.toUpperCase()).contains("REGION");
			assertThat(sql2).contains("West");
		}

		@Test
		@DisplayName("Column addition - 'also include customer_name column'")
		void columnAddition() {
			String sessionId = "column-addition";
			sqlExplorerActions.reset();

			// Turn 1: Explicit SQL generation
			ConversationTurnResult turn1 = processAndTrack(sessionId, 
					"generate a SQL query to show order_value from fct_orders");
			assertPlanReady(turn1.plan());
			String sql1 = sqlExplorerActions.lastQuery().orElseThrow().sqlString();
			log.info("Turn 1 SQL: {}", sql1);

			sqlExplorerActions.reset();

			// Turn 2: Add column by modifying query
			ConversationTurnResult turn2 = processAndTrack(sessionId, 
					"modify the query to also select customer_name by joining to dim_customer");
			assertPlanReady(turn2.plan());
			
			String sql2 = sqlExplorerActions.lastQuery().orElseThrow().sqlString();
			log.info("Turn 2 SQL: {}", sql2);

			assertThat(sql2.toUpperCase()).contains("CUSTOMER_NAME");
			assertThat(sql2.toUpperCase()).contains("DIM_CUSTOMER");
		}

		@Test
		@DisplayName("Date substitution - 'show same query for 2023'")
		void dateSubstitution() {
			String sessionId = "date-substitution";
			sqlExplorerActions.reset();

			ConversationTurnResult turn1 = processAndTrack(sessionId, "show me order values for January 2024");
			assertPlanReady(turn1.plan());
			String sql1 = sqlExplorerActions.lastQuery().orElseThrow().sqlString();
			log.info("Turn 1 SQL: {}", sql1);

			sqlExplorerActions.reset();

			ConversationTurnResult turn2 = processAndTrack(sessionId, "show the same query but for January 2023");
			assertPlanReady(turn2.plan());
			
			String sql2 = sqlExplorerActions.lastQuery().orElseThrow().sqlString();
			log.info("Turn 2 SQL: {}", sql2);

			assertThat(sql2).contains("2023");
			assertThat(sql2).doesNotContain("2024");
		}

		@Test
		@DisplayName("Context persists across multiple turns")
		void contextPersistsAcrossTurns() {
			String sessionId = "multi-turn-persist";
			sqlExplorerActions.reset();

			// Turn 1: Explicitly ask for SQL query generation
			ConversationTurnResult turn1 = processAndTrack(sessionId, 
					"generate a SQL query to show order_value from fct_orders");
			assertPlanReady(turn1.plan());

			sqlExplorerActions.reset();
			// Turn 2: Modify the existing query
			ConversationTurnResult turn2 = processAndTrack(sessionId, 
					"modify the query to also select customer_name by joining to dim_customer");
			assertPlanReady(turn2.plan());

			sqlExplorerActions.reset();
			// Turn 3: Add a filter
			ConversationTurnResult turn3 = processAndTrack(sessionId, 
					"modify the query to add WHERE region = 'East'");
			assertPlanReady(turn3.plan());

			String finalSql = sqlExplorerActions.lastQuery().orElseThrow().sqlString();
			log.info("Final SQL: {}", finalSql);

			assertThat(finalSql.toUpperCase()).contains("ORDER_VALUE");
			assertThat(finalSql.toUpperCase()).contains("CUSTOMER_NAME");
			assertThat(finalSql.toUpperCase()).contains("REGION");
			assertThat(finalSql).contains("East");

			var state = conversationManager.fromBlob(turn3.blob());
			assertThat(state.hasWorkingContext()).isTrue();
			assertThat(state.turnHistory()).hasSizeGreaterThanOrEqualTo(1);
			
			log.info("History size: {}", state.turnHistory().size());
		}
	}
}
