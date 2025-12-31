package org.javai.springai.scenarios.data_warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.javai.springai.actions.test.PlanAssertions.assertPlanReady;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.javai.springai.actions.DefaultPlanExecutor;
import org.javai.springai.actions.PersonaSpec;
import org.javai.springai.actions.PlanExecutionResult;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

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
public class DataWarehouseMultiTurnScenarioTest {

	// Enable with: export RUN_LLM_TESTS=true
	private static final boolean RUN_LLM_TESTS = "true".equalsIgnoreCase(System.getenv("RUN_LLM_TESTS"));

	private DataWarehouseActions dataWarehouseActions;
	private InMemorySqlCatalog catalog;
	private Planner planner;
	private DefaultPlanExecutor executor;
	private ConversationManager conversationManager;
	private PayloadTypeRegistry typeRegistry;
	private ConversationStateSerializer serializer;

	// Simulates application's session storage
	private Map<String, byte[]> applicationBlobStore;

	@BeforeEach
	void setUp() {
		// Skip setup if LLM tests aren't enabled
		if (!RUN_LLM_TESTS) {
			return;
		}

		String apiKey = System.getenv("OPENAI_API_KEY");
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalStateException("OPENAI_API_KEY must be set");
		}

		OpenAiApi api = OpenAiApi.builder().apiKey(apiKey).build();
		OpenAiChatOptions options = OpenAiChatOptions.builder()
				.model("gpt-4.1-mini")
				.temperature(0.0)
				.build();
		var chatModel = OpenAiChatModel.builder().openAiApi(api).defaultOptions(options).build();
		var chatClient = ChatClient.builder(chatModel).defaultOptions(options).build();

		dataWarehouseActions = new DataWarehouseActions();

		// Build schema with synonyms for better LLM understanding
		catalog = new InMemorySqlCatalog()
				.addTable("fct_orders", "Fact table containing order transactions", "fact")
				.withSynonyms("fct_orders", "orders", "order", "sales")
				.addColumn("fct_orders", "customer_id", "FK to dim_customer", "string",
						new String[] { "fk:dim_customer.id" }, null)
				.addColumn("fct_orders", "date_id", "FK to dim_date", "string",
						new String[] { "fk:dim_date.id" }, null)
				.addColumn("fct_orders", "order_value", "Order amount in dollars", "double",
						new String[] { "measure" }, null)
				.addColumn("fct_orders", "region", "Geographic region", "string",
						new String[] { "attribute" }, null)
				.addTable("dim_customer", "Customer dimension table", "dimension")
				.withSynonyms("dim_customer", "customers", "customer")
				.addColumn("dim_customer", "id", "Customer primary key", "string",
						new String[] { "pk" }, new String[] { "unique" })
				.addColumn("dim_customer", "customer_name", "Customer full name", "string",
						new String[] { "attribute" }, null)
				.addTable("dim_date", "Date dimension table", "dimension")
				.withSynonyms("dim_date", "dates", "date")
				.addColumn("dim_date", "id", "Date ID (YYYYMMDD)", "string",
						new String[] { "pk" }, new String[] { "unique" })
				.addColumn("dim_date", "date", "Calendar date", "date",
						new String[] { "attribute" }, null)
				.withDialect(Query.Dialect.ANSI);

		PersonaSpec persona = PersonaSpec.builder()
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

		planner = Planner.builder()
				.withChatClient(chatClient)
				.persona(persona)
				.actions(dataWarehouseActions)
				.addPromptContext("sql", catalog)
				.promptContributor(new SqlCatalogContextContributor(catalog))
				.build();

		executor = new DefaultPlanExecutor();

		// Set up blob-based conversation infrastructure
		typeRegistry = new PayloadTypeRegistry();
		typeRegistry.register("sql.query", QueryContext.class);

		serializer = new JsonConversationStateSerializer();

		ConversationStateConfig config = ConversationStateConfig.builder()
				.maxHistorySize(5)
				.build();

		conversationManager = new ConversationManager(planner, serializer, typeRegistry, config);

		// Application's simulated blob store
		applicationBlobStore = new HashMap<>();
	}

	/**
	 * Payload for SQL working context.
	 * 
	 * <p>Stores the "model SQL" — the SQL using catalog synonyms as the LLM
	 * sees and generates it. This is the canonical form from which resolved
	 * variants (real table names, dialect-specific) are derived.</p>
	 */
	public record QueryContext(String modelSql) {
		
		/**
		 * Creates a QueryContext from a Query.
		 * 
		 * <p>Note: In a full implementation, we would capture the model SQL
		 * before resolution. For now, we use the resolved SQL as a placeholder.</p>
		 */
		public static QueryContext from(Query query) {
			// TODO: Capture model SQL before de-synonymization
			return new QueryContext(query.sqlString());
		}
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

			ConversationStateSerializer serializer = new JsonConversationStateSerializer();

			// Create state with working context
			ConversationState state =
					ConversationState.initial("show me orders")
							.withWorkingContext(WorkingContext.of("test.context", "test payload"), 10);

			// Serialize
			byte[] blob = serializer.serialize(state, registry);
			assertThat(blob).isNotNull();
			assertThat(blob.length).isGreaterThan(10);

			// Deserialize
			var restored = serializer.deserialize(blob, registry);
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

			ConversationStateSerializer serializer = new JsonConversationStateSerializer();

			var state = ConversationState.initial("test query")
					.withWorkingContext(WorkingContext.of("test.context", "payload"), 10);

			byte[] blob = serializer.serialize(state, registry);
			String json = serializer.toReadableJson(blob);

			assertThat(json).contains("originalInstruction");
			assertThat(json).contains("test query");
			assertThat(json).contains("workingContext");
			assertThat(json).contains("test.context");
		}

		@Test
		@DisplayName("expire() creates empty state with blob")
		void expireCreatesEmptyState() {
			if (!RUN_LLM_TESTS) return;

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
			// Load prior blob from "database"
			byte[] priorBlob = applicationBlobStore.get(sessionId);

			// Process the turn
			ConversationTurnResult result = conversationManager.converse(message, priorBlob);

			// Execute plan if ready and extract working context
			if (result.plan().status() == org.javai.springai.actions.PlanStatus.READY) {
				PlanExecutionResult execResult = executor.execute(result.plan());
				
				// If we got a Query, create a working context from it
				if (dataWarehouseActions.showSqlQueryInvoked() && dataWarehouseActions.lastQuery() != null) {
					Query query = dataWarehouseActions.lastQuery();
					QueryContext queryCtx = QueryContext.from(query);
					
					// Update state with working context and re-serialize
					var updatedState = result.state().withWorkingContext(
							WorkingContext.of("sql.query", queryCtx),
							conversationManager.config().maxHistorySize());
					
					byte[] updatedBlob = serializer.serialize(updatedState, typeRegistry);
					result = new ConversationTurnResult(
							result.plan(), updatedState, updatedBlob,
							result.pendingParams(), result.providedParams());
				}
			}

			// Store blob for next turn
			applicationBlobStore.put(sessionId, result.blob());

			return result;
		}

		@Test
		@DisplayName("Turn 1: Initial query formulation")
		void turn1InitialQuery() {
			String sessionId = "multi-turn-session-1";
			dataWarehouseActions.reset();

			// Turn 1: Initial query
			ConversationTurnResult result = processUserMessage(sessionId,
					"show me order values with customer names");

			assertPlanReady(result.plan());
			assertThat(dataWarehouseActions.showSqlQueryInvoked()).isTrue();

			Query query = dataWarehouseActions.lastQuery();
			String sql = query.sqlString().toUpperCase();
			assertThat(sql).contains("FCT_ORDERS");
			assertThat(sql).contains("DIM_CUSTOMER");

			// Blob should be stored
			assertThat(applicationBlobStore.get(sessionId)).isNotNull();

			// Log for inspection
			System.out.println("Turn 1 SQL: " + query.sqlString());
			System.out.println("Turn 1 Blob (readable):");
			System.out.println(conversationManager.toReadableJson(result.blob()));
		}

		@Test
		@DisplayName("Two-turn conversation: query then refine")
		void twoTurnConversation() {
			String sessionId = "multi-turn-session-2";
			dataWarehouseActions.reset();

			// Turn 1: Initial query
			ConversationTurnResult turn1 = processUserMessage(sessionId,
					"show me order values with customer names");
			assertPlanReady(turn1.plan());
			
			String turn1Sql = dataWarehouseActions.lastQuery().sqlString();
			System.out.println("Turn 1 SQL: " + turn1Sql);

			dataWarehouseActions.reset();

			// Turn 2: Refine the query
			// The blob contains the prior context, which the LLM should use
			ConversationTurnResult turn2 = processUserMessage(sessionId,
					"now filter that by region = 'West'");

			assertPlanReady(turn2.plan());
			assertThat(dataWarehouseActions.showSqlQueryInvoked()).isTrue();

			String turn2Sql = dataWarehouseActions.lastQuery().sqlString();
			System.out.println("Turn 2 SQL: " + turn2Sql);

			// The refined query should include the filter
			assertThat(turn2Sql.toUpperCase()).contains("REGION");
			assertThat(turn2Sql.toUpperCase()).contains("WEST");

			// Verify state has history
			var state = conversationManager.fromBlob(turn2.blob());
			System.out.println("Turn 2 State has working context: " + state.hasWorkingContext());
			System.out.println("Turn 2 History size: " + state.turnHistory().size());
		}

		@Test
		@DisplayName("Session expiry clears context")
		void sessionExpiryClearsContext() {
			String sessionId = "expiry-session";
			dataWarehouseActions.reset();

			// Establish a conversation
			processUserMessage(sessionId, "show me all orders");
			assertThat(applicationBlobStore.get(sessionId)).isNotNull();

			// Expire the session
			ConversationTurnResult expired = conversationManager.expire();
			applicationBlobStore.put(sessionId, expired.blob());

			// Verify the state is empty
			var state = conversationManager.fromBlob(applicationBlobStore.get(sessionId));
			assertThat(state.originalInstruction()).isNull();
			assertThat(state.workingContext()).isNull();
			assertThat(state.turnHistory()).isEmpty();

			System.out.println("Expired state:");
			System.out.println(conversationManager.toReadableJson(expired.blob()));
		}
	}

	@Nested
	@DisplayName("Application Integration Pattern")
	@EnabledIfEnvironmentVariable(named = "RUN_LLM_TESTS", matches = "true")
	class ApplicationIntegrationPattern {

		@Test
		@DisplayName("demonstrates full application workflow")
		void fullApplicationWorkflow() {
			// This test demonstrates the recommended pattern for applications

			// ========================================
			// SETUP (done once at application startup)
			// ========================================
			PayloadTypeRegistry registry = new PayloadTypeRegistry();
			registry.register("sql.query", QueryContext.class);

			ConversationStateSerializer serializer = new JsonConversationStateSerializer();
			ConversationStateConfig config = ConversationStateConfig.builder()
					.maxHistorySize(10)
					.build();

			ConversationManager manager = new ConversationManager(
					planner, serializer, registry, config);

			// ========================================
			// REQUEST 1: New conversation
			// ========================================
			String sessionId = "user-123";
			
			// Application loads blob (null for new session)
			byte[] blob1 = null;  // sessionRepository.getBlob(sessionId);

			// Process turn
			ConversationTurnResult result1 = manager.converse(
					"show me total order values", blob1);

			// Execute if ready
			if (result1.status() == org.javai.springai.actions.PlanStatus.READY) {
				executor.execute(result1.plan());
			}

			// Application stores blob
			// sessionRepository.saveBlob(sessionId, result1.blob());
			byte[] storedBlob = result1.blob();

			System.out.println("Request 1 complete. Blob size: " + storedBlob.length + " bytes");

			// ========================================
			// REQUEST 2: Follow-up (uses stored blob)
			// ========================================
			dataWarehouseActions.reset();

			// Application loads blob
			byte[] blob2 = storedBlob;  // sessionRepository.getBlob(sessionId);

			// Process turn with prior context
			ConversationTurnResult result2 = manager.converse(
					"group that by customer", blob2);

			if (result2.status() == org.javai.springai.actions.PlanStatus.READY) {
				executor.execute(result2.plan());
			}

			// Store updated blob
			storedBlob = result2.blob();

			System.out.println("Request 2 complete. Blob size: " + storedBlob.length + " bytes");

			// ========================================
			// DEBUG: Inspect state
			// ========================================
			String readable = manager.toReadableJson(storedBlob);
			System.out.println("Final state (readable JSON):");
			System.out.println(readable);

			// ========================================
			// USER CANCELS
			// ========================================
			ConversationTurnResult expired = manager.expire();
			// sessionRepository.saveBlob(sessionId, expired.blob());

			var finalState = manager.fromBlob(expired.blob());
			assertThat(finalState.workingContext()).isNull();
			System.out.println("Session cancelled/expired");
		}
	}
}

