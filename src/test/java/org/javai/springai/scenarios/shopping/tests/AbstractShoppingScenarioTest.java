package org.javai.springai.scenarios.shopping.tests;

import java.util.List;
import java.util.Objects;
import org.javai.springai.actions.DefaultPlanExecutor;
import org.javai.springai.actions.Plan;
import org.javai.springai.actions.PlanExecutionResult;
import org.javai.springai.actions.PlanStep;
import org.javai.springai.actions.Planner;
import org.javai.springai.actions.conversation.ConversationManager;
import org.javai.springai.actions.conversation.InMemoryConversationStateStore;
import org.javai.springai.scenarios.shopping.actions.ActionResult;
import org.javai.springai.scenarios.shopping.actions.InventoryAwareShoppingActions;
import org.javai.springai.scenarios.shopping.actions.Notification;
import org.javai.springai.scenarios.shopping.actions.ShoppingPersonaSpec;
import org.javai.springai.scenarios.shopping.store.MockStoreApi;
import org.javai.springai.scenarios.shopping.tools.EnhancedSpecialOfferTool;
import org.javai.springai.scenarios.shopping.tools.InventoryTool;
import org.javai.springai.scenarios.shopping.tools.PricingTool;
import org.javai.springai.scenarios.shopping.tools.ProductSearchTool;
import org.javai.springai.scenarios.shopping.tools.SkuFinderTool;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

/**
 * Abstract base class for shopping scenario tests.
 * <p>
 * Provides common setup for:
 * <ul>
 *   <li>Mock store infrastructure (inventory, pricing, offers)</li>
 *   <li>LLM components (ChatClient, Planner)</li>
 *   <li>Plan execution infrastructure</li>
 *   <li>Conversation management</li>
 * </ul>
 * 
 * <p>Tests are only run when {@code RUN_LLM_TESTS=true} and 
 * {@code OPENAI_API_KEY} is set.</p>
 */
public abstract class AbstractShoppingScenarioTest {

	protected static final Logger log = LoggerFactory.getLogger(AbstractShoppingScenarioTest.class);
	protected static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");
	protected static final boolean RUN_LLM_TESTS = "true".equalsIgnoreCase(System.getenv("RUN_LLM_TESTS"));

	// Store infrastructure
	protected MockStoreApi storeApi;
	protected InventoryAwareShoppingActions actions;
	protected EnhancedSpecialOfferTool offerTool;
	protected InventoryTool inventoryTool;
	protected PricingTool pricingTool;
	protected ProductSearchTool searchTool;
	protected SkuFinderTool skuFinderTool;

	// LLM infrastructure
	protected Planner planner;
	protected DefaultPlanExecutor executor;
	protected ConversationManager conversationManager;

	@BeforeEach
	void setUp() {
		Assumptions.assumeTrue(RUN_LLM_TESTS, "Set RUN_LLM_TESTS=true to enable LLM integration tests");
		Assumptions.assumeTrue(OPENAI_API_KEY != null && !OPENAI_API_KEY.isBlank(),
				"OPENAI_API_KEY must be set for this integration test");

		// Initialize mock store infrastructure
		storeApi = new MockStoreApi();
		actions = new InventoryAwareShoppingActions(storeApi);
		offerTool = new EnhancedSpecialOfferTool(storeApi);
		inventoryTool = new InventoryTool(storeApi);
		pricingTool = new PricingTool(storeApi);
		searchTool = new ProductSearchTool(storeApi);
		skuFinderTool = new SkuFinderTool(storeApi);

		// Initialize LLM components
		OpenAiApi openAiApi = OpenAiApi.builder().apiKey(OPENAI_API_KEY).build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder().openAiApi(openAiApi).build();
		OpenAiChatOptions options = OpenAiChatOptions.builder()
				.model("gpt-4.1-mini")
				.temperature(0.1)
				.topP(1.0)
				.build();
		ChatClient chatClient = ChatClient.builder(Objects.requireNonNull(chatModel))
				.defaultOptions(Objects.requireNonNull(options))
				.build();

		planner = Planner.builder()
				.withChatClient(chatClient)
				.persona(ShoppingPersonaSpec.standard())
				.tools(skuFinderTool, offerTool, inventoryTool, pricingTool, searchTool)
				.actions(actions)
				.build();
		
		executor = DefaultPlanExecutor.builder()
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
		
		conversationManager = new ConversationManager(planner, new InMemoryConversationStateStore());
	}

	/**
	 * Extract all notifications from a plan execution result.
	 */
	protected List<Notification> extractNotifications(PlanExecutionResult result) {
		return result.steps().stream()
				.map(step -> step.returnValue())
				.filter(rv -> rv instanceof ActionResult)
				.map(rv -> (ActionResult) rv)
				.flatMap(ar -> ar.notifications().stream())
				.toList();
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
}

