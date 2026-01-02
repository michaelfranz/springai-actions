package org.javai.springai.scenarios.data_warehouse;

import java.util.List;
import java.util.Objects;
import org.javai.springai.actions.DefaultPlanExecutor;
import org.javai.springai.actions.PersonaSpec;
import org.javai.springai.actions.Plan;
import org.javai.springai.actions.PlanExecutionResult;
import org.javai.springai.actions.PlanStep;
import org.javai.springai.actions.Planner;
import org.javai.springai.actions.conversation.ConversationManager;
import org.javai.springai.actions.conversation.InMemoryConversationStateStore;
import org.javai.springai.actions.sql.AdaptiveSqlCatalogContributor;
import org.javai.springai.actions.sql.FrequencyAwareSqlCatalogTool;
import org.javai.springai.actions.sql.InMemorySchemaAccessTracker;
import org.javai.springai.actions.sql.InMemorySqlCatalog;
import org.javai.springai.actions.sql.SqlCatalogContextContributor;
import org.javai.springai.actions.sql.SqlCatalogTool;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

/**
 * Abstract base class for data warehouse scenario tests.
 * <p>
 * Provides common setup for:
 * <ul>
 *   <li>SQL catalog with star schema (fact + dimension tables)</li>
 *   <li>LLM components (ChatClient, Planner)</li>
 *   <li>Plan execution infrastructure</li>
 *   <li>Conversation management</li>
 *   <li>Schema access tracking for adaptive scenarios</li>
 * </ul>
 * 
 * <p>Tests are only run when {@code RUN_LLM_TESTS=true} and 
 * {@code OPENAI_API_KEY} is set.</p>
 */
public abstract class AbstractDataWarehouseScenarioTest {

	protected static final Logger log = LoggerFactory.getLogger(AbstractDataWarehouseScenarioTest.class);
	protected static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");
	protected static final boolean RUN_LLM_TESTS = "true".equalsIgnoreCase(System.getenv("RUN_LLM_TESTS"));
	public static final String MODEST_CHAT_MODEL_VERSION = "gpt-4.1-mini";
	public static final String CAPABLE_CHAT_MODEL_VERSION = "gpt-4o";
	public static final String MOST_CAPABLE_CHAT_MODEL_VERSION = "gpt-4-turbo";

	// SQL infrastructure
	protected InMemorySqlCatalog catalog;
	protected InMemorySchemaAccessTracker tracker;
	protected SqlCatalogTool baseTool;
	protected FrequencyAwareSqlCatalogTool trackingTool;
	protected AdaptiveSqlCatalogContributor adaptiveContributor;

	// Actions
	protected DataWarehouseActions dataWarehouseActions;

	// LLM infrastructure
	protected ChatClient modestChatClient;
	protected ChatClient capableChatClient;
	protected ChatClient mostCapableChatClient;
	protected Planner planner;
	protected DefaultPlanExecutor executor;
	protected ConversationManager conversationManager;

	@BeforeEach
	void setUp() {
		Assumptions.assumeTrue(RUN_LLM_TESTS, "Set RUN_LLM_TESTS=true to enable LLM integration tests");
		Assumptions.assumeTrue(OPENAI_API_KEY != null && !OPENAI_API_KEY.isBlank(),
				"OPENAI_API_KEY must be set for this integration test");

		// Initialize LLM components
		OpenAiApi openAiApi = OpenAiApi.builder().apiKey(OPENAI_API_KEY).build();

		OpenAiChatModel chatModel = OpenAiChatModel.builder().openAiApi(openAiApi).build();

		modestChatClient = getChatClient(chatModel, MODEST_CHAT_MODEL_VERSION);
		capableChatClient = getChatClient(chatModel, CAPABLE_CHAT_MODEL_VERSION);
		mostCapableChatClient = getChatClient(chatModel, MOST_CAPABLE_CHAT_MODEL_VERSION);

		// Initialize actions
		dataWarehouseActions = new DataWarehouseActions();

		// Create SQL catalog with star schema
		catalog = createDefaultCatalog();

		// Create tracker and tools for adaptive scenarios
		tracker = new InMemorySchemaAccessTracker();
		baseTool = new SqlCatalogTool(catalog);
		trackingTool = new FrequencyAwareSqlCatalogTool(baseTool, tracker);

		// Initialize executor
		executor = createDefaultExecutor();

		// Allow subclasses to customize planner and conversation manager
		initializePlanner();
	}

	protected static ChatClient getChatClient(OpenAiChatModel chatModel, String chatModelVersion) {
		return ChatClient.builder(Objects.requireNonNull(chatModel))
				.defaultOptions(Objects.requireNonNull(OpenAiChatOptions.builder()
						.model(chatModelVersion)
						.temperature(0.0)
						.topP(1.0)
						.build()))
				.build();
	}

	/**
	 * Create the default SQL catalog with star schema.
	 * Subclasses can override to customize the schema.
	 */
	protected InMemorySqlCatalog createDefaultCatalog() {
		return new InMemorySqlCatalog()
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
	}

	/**
	 * Create the default plan executor.
	 * Subclasses can override to customize executor behavior.
	 */
	protected DefaultPlanExecutor createDefaultExecutor() {
		return DefaultPlanExecutor.builder()
				.onPending((plan, context) -> {
					log.info("Plan pending - missing params: {}", plan.pendingParameterNames());
					return PlanExecutionResult.notExecuted(plan, context,
							"Awaiting input: " + plan.pendingParameterNames());
				})
				.onError((plan, context) -> {
					String errorMsg = plan.planSteps().stream()
							.filter(s -> s instanceof PlanStep.ErrorStep)
							.map(s -> ((PlanStep.ErrorStep) s).reason())
							.findFirst()
							.orElse("Unknown error");
					log.warn("Plan error: {}", errorMsg);
					return PlanExecutionResult.notExecuted(plan, context, errorMsg);
				})
				.onNoAction((plan, context, message) -> {
					log.info("No action identified: {}", message);
					return PlanExecutionResult.notExecuted(plan, context, message);
				})
				.build();
	}

	/**
	 * Initialize the planner and conversation manager.
	 * Subclasses should override this to customize planner configuration.
	 */
	protected void initializePlanner() {
		PersonaSpec sqlAnalystPersona = createDefaultPersona();

		planner = Planner.builder()
				.defaultChatClient(modestChatClient, 2)
				.fallbackChatClient(capableChatClient, 2)
				.fallbackChatClient(mostCapableChatClient, 2)
				.persona(sqlAnalystPersona)
				.actions(dataWarehouseActions)
				.promptContributor(new SqlCatalogContextContributor(catalog))
				.addPromptContext("sql", catalog)
				.build();

		conversationManager = new ConversationManager(planner, new InMemoryConversationStateStore());
	}

	/**
	 * Create the default persona for SQL analyst.
	 * Subclasses can override to customize the persona.
	 */
	protected PersonaSpec createDefaultPersona() {
		return PersonaSpec.builder()
				.name("SQLDataWarehouseAssistant")
				.role("Assistant for data warehouse query planning and order value analysis")
				.principles(List.of(
						"Understand what the user wants to accomplish from a domain perspective",
						"Select the action whose purpose best matches the user's intent"))
				.constraints(List.of(
						"Only use the available actions",
						"If any required parameter is unclear, use PENDING"))
				.build();
	}

	/**
	 * Execute a conversation turn and return the result.
	 * Convenience method for tests.
	 */
	protected PlanExecutionResult executeConversation(String message, String sessionId,
			org.javai.springai.actions.api.ActionContext context) {
		Plan plan = conversationManager.converse(message, sessionId).plan();
		return executor.execute(plan, context);
	}

	/**
	 * Execute a conversation turn and return the result using the default context.
	 * Convenience method for tests.
	 */
	protected PlanExecutionResult executeConversation(String message, String sessionId) {
		Plan plan = conversationManager.converse(message, sessionId).plan();
		return executor.execute(plan);
	}
}
