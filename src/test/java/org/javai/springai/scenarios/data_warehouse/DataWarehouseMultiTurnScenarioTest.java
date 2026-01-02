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
import org.javai.springai.actions.sql.SqlWorkingContextContributor;
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
				.name("SQLDataWarehouseAssistant")
				.role("Assistant for data warehouse query planning")
				.principles(List.of(
						"Understand what the user wants from a domain perspective",
						"Use exact table/column names from the SQL CATALOG",
						"When user says 'that' or 'those results', refer to the PREVIOUS QUERY CONTEXT",
						"For refinements like 'filter by X', modify the previous query"))
				.constraints(List.of(
						"Only use the available actions",
						"Only use table and column names from the catalog",
						"If any required parameter is unclear, use PENDING"))
				.build();
	}

	@Override
	protected void initializePlanner() {
		PersonaSpec persona = createDefaultPersona();

		planner = Planner.builder()
				.defaultChatClient(defaultChatClient)
				.persona(persona)
				.actions(dataWarehouseActions)
				.addPromptContext("sql", catalog)
				.promptContributor(new SqlCatalogContextContributor(catalog))
				.promptContributor(new SqlWorkingContextContributor())
				.build();

		// Set up blob-based conversation infrastructure
		typeRegistry = new PayloadTypeRegistry();
		typeRegistry.register(SqlQueryPayload.CONTEXT_TYPE, SqlQueryPayload.class);

		serializer = new JsonConversationStateSerializer();

		ConversationStateConfig config = ConversationStateConfig.builder()
				.maxHistorySize(5)
				.build();

		conversationManager = new ConversationManager(planner, serializer, typeRegistry, config);

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
			ConversationTurnResult result = conversationManager.converse(message, priorBlob);

			if (result.plan().status() == PlanStatus.READY) {
				executor.execute(result.plan());
				
				if (dataWarehouseActions.showSqlQueryInvoked() && dataWarehouseActions.lastQuery().isPresent()) {
					Query query = dataWarehouseActions.lastQuery().get();
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
			dataWarehouseActions.reset();

			ConversationTurnResult result = processUserMessage(sessionId,
					"show me order values with customer names");

			assertPlanReady(result.plan());
			assertThat(dataWarehouseActions.showSqlQueryInvoked()).isTrue();

			Query query = dataWarehouseActions.lastQuery().orElseThrow();
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
			dataWarehouseActions.reset();

			ConversationTurnResult turn1 = processUserMessage(sessionId,
					"show me order values with customer names");
			assertPlanReady(turn1.plan());
			
			String turn1Sql = dataWarehouseActions.lastQuery().orElseThrow().sqlString();
			log.info("Turn 1 SQL: {}", turn1Sql);

			dataWarehouseActions.reset();

			ConversationTurnResult turn2 = processUserMessage(sessionId,
					"now filter that by region = 'West'");

			assertPlanReady(turn2.plan());
			assertThat(dataWarehouseActions.showSqlQueryInvoked()).isTrue();

			String turn2Sql = dataWarehouseActions.lastQuery().orElseThrow().sqlString();
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
			dataWarehouseActions.reset();

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

			dataWarehouseActions.reset();

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
			ConversationTurnResult result = conversationManager.converse(message, priorBlob);

			if (result.plan().status() == PlanStatus.READY) {
				executor.execute(result.plan());
				
				if (dataWarehouseActions.showSqlQueryInvoked() && dataWarehouseActions.lastQuery().isPresent()) {
					Query query = dataWarehouseActions.lastQuery().orElseThrow();
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
			dataWarehouseActions.reset();

			ConversationTurnResult turn1 = processAndTrack(sessionId, "show me order values");
			assertPlanReady(turn1.plan());
			String sql1 = dataWarehouseActions.lastQuery().orElseThrow().sqlString();
			log.info("Turn 1 SQL: {}", sql1);

			dataWarehouseActions.reset();

			ConversationTurnResult turn2 = processAndTrack(sessionId, "now filter that by region = 'West'");
			assertPlanReady(turn2.plan());
			
			String sql2 = dataWarehouseActions.lastQuery().orElseThrow().sqlString();
			log.info("Turn 2 SQL: {}", sql2);

			assertThat(sql2.toUpperCase()).contains("WHERE");
			assertThat(sql2.toUpperCase()).contains("REGION");
			assertThat(sql2).contains("West");
		}

		@Test
		@DisplayName("Column addition - 'add customer name to those results'")
		void columnAddition() {
			String sessionId = "column-addition";
			dataWarehouseActions.reset();

			ConversationTurnResult turn1 = processAndTrack(sessionId, "show me order values from the orders table");
			assertPlanReady(turn1.plan());
			String sql1 = dataWarehouseActions.lastQuery().orElseThrow().sqlString();
			log.info("Turn 1 SQL: {}", sql1);

			dataWarehouseActions.reset();

			ConversationTurnResult turn2 = processAndTrack(sessionId, "add the customer name to those results");
			assertPlanReady(turn2.plan());
			
			String sql2 = dataWarehouseActions.lastQuery().orElseThrow().sqlString();
			log.info("Turn 2 SQL: {}", sql2);

			assertThat(sql2.toUpperCase()).contains("CUSTOMER_NAME");
			assertThat(sql2.toUpperCase()).contains("DIM_CUSTOMER");
		}

		@Test
		@DisplayName("Date substitution - 'show same query for 2023'")
		void dateSubstitution() {
			String sessionId = "date-substitution";
			dataWarehouseActions.reset();

			ConversationTurnResult turn1 = processAndTrack(sessionId, "show me order values for January 2024");
			assertPlanReady(turn1.plan());
			String sql1 = dataWarehouseActions.lastQuery().orElseThrow().sqlString();
			log.info("Turn 1 SQL: {}", sql1);

			dataWarehouseActions.reset();

			ConversationTurnResult turn2 = processAndTrack(sessionId, "show the same query but for January 2023");
			assertPlanReady(turn2.plan());
			
			String sql2 = dataWarehouseActions.lastQuery().orElseThrow().sqlString();
			log.info("Turn 2 SQL: {}", sql2);

			assertThat(sql2).contains("2023");
			assertThat(sql2).doesNotContain("2024");
		}

		@Test
		@DisplayName("Context persists across multiple turns")
		void contextPersistsAcrossTurns() {
			String sessionId = "multi-turn-persist";
			dataWarehouseActions.reset();

			ConversationTurnResult turn1 = processAndTrack(sessionId, "show me order values");
			assertPlanReady(turn1.plan());

			dataWarehouseActions.reset();
			ConversationTurnResult turn2 = processAndTrack(sessionId, "add customer names");
			assertPlanReady(turn2.plan());

			dataWarehouseActions.reset();
			ConversationTurnResult turn3 = processAndTrack(sessionId, "filter by region = 'East'");
			assertPlanReady(turn3.plan());

			String finalSql = dataWarehouseActions.lastQuery().orElseThrow().sqlString();
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
