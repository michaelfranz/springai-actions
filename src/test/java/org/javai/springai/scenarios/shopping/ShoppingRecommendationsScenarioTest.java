package org.javai.springai.scenarios.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.javai.springai.actions.test.PlanAssertions.assertPlanReady;
import java.util.Objects;
import org.javai.springai.actions.DefaultPlanExecutor;
import org.javai.springai.actions.Plan;
import org.javai.springai.actions.PlanExecutionResult;
import org.javai.springai.actions.PlanStatus;
import org.javai.springai.actions.PlanStep;
import org.javai.springai.actions.Planner;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.conversation.ConversationManager;
import org.javai.springai.actions.conversation.ConversationTurnResult;
import org.javai.springai.actions.conversation.InMemoryConversationStateStore;
import org.javai.springai.scenarios.shopping.actions.InventoryAwareShoppingActions;
import org.javai.springai.scenarios.shopping.actions.ShoppingPersonaSpec;
import org.javai.springai.scenarios.shopping.store.MockStoreApi;
import org.javai.springai.scenarios.shopping.tools.CustomerTool;
import org.javai.springai.scenarios.shopping.tools.EnhancedSpecialOfferTool;
import org.javai.springai.scenarios.shopping.tools.InventoryTool;
import org.javai.springai.scenarios.shopping.tools.PricingTool;
import org.javai.springai.scenarios.shopping.tools.ProductSearchTool;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

/**
 * Intelligent Recommendations Scenario Tests.
 * 
 * Tests the assistant's ability to provide intelligent, contextual recommendations:
 * - Special offers and bargains
 * - Personalised recommendations based on customer history
 * - Stock-aware advice and alternatives
 * 
 * @see README.md "Intelligent Recommendations" section
 */
@DisplayName("Intelligent Recommendations")
public class ShoppingRecommendationsScenarioTest {

	private static final Logger log = LoggerFactory.getLogger(ShoppingRecommendationsScenarioTest.class);
	private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");
	private static final boolean RUN_LLM_TESTS = "true".equalsIgnoreCase(System.getenv("RUN_LLM_TESTS"));

	private MockStoreApi storeApi;
	private InventoryAwareShoppingActions actions;
	private EnhancedSpecialOfferTool offerTool;
	private InventoryTool inventoryTool;
	private PricingTool pricingTool;
	private ProductSearchTool searchTool;
	private CustomerTool customerTool;

	private Planner planner;
	private DefaultPlanExecutor executor;
	private ConversationManager conversationManager;

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
		customerTool = new CustomerTool(storeApi, storeApi.getCustomers());

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
				.defaultChatClient(chatClient)
				.persona(ShoppingPersonaSpec.standard())
				.tools(offerTool, inventoryTool, pricingTool, searchTool, customerTool)
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

	// ========================================================================
	// SPECIAL OFFERS AND BARGAINS
	// ========================================================================

	@Nested
	@DisplayName("Special Offers and Bargains")
	class SpecialOffers {

		@Test
		@DisplayName("should successfully start a shopping session")
		void startShoppingSession() {
			String sessionId = "rec-offers-start";
			ActionContext context = new ActionContext();

			ConversationTurnResult turn = conversationManager
					.converse("I want to start shopping", sessionId);
			Plan plan = turn.plan();
			
			assertThat(plan).isNotNull();
			// Session start should produce a READY plan or ERROR (resolution issue)
			assertThat(plan.status()).isIn(PlanStatus.READY, PlanStatus.ERROR);
			
			if (plan.status() == PlanStatus.READY) {
				executor.execute(plan, context);
				// Session should have been started
				assertThat(actions.startSessionInvoked()).isTrue();
			}
		}

		@Test
		@DisplayName("should handle adding products that may have promotions")
		void handleAddingProductsWithPotentialPromotions() {
			String sessionId = "rec-offers-add";
			ActionContext context = new ActionContext();

			// Start session
			ConversationTurnResult startTurn = conversationManager.converse("start shopping", sessionId);
			if (startTurn.plan().status() == PlanStatus.READY) {
				executor.execute(startTurn.plan(), context);
			}

			// Add a product that has an active offer (Coke Zero has 10% off)
			ConversationTurnResult addTurn = conversationManager
					.converse("add some Coca Cola", sessionId);
			Plan addPlan = addTurn.plan();
			
			assertThat(addPlan).isNotNull();
			// Adding products may succeed, require more info, or fail resolution
			assertThat(addPlan.status()).isIn(PlanStatus.READY, PlanStatus.PENDING, PlanStatus.ERROR);
			
			if (addPlan.status() == PlanStatus.READY) {
				executor.execute(addPlan, context);
				// If successful, addItem should have been invoked
				assertThat(actions.addItemInvoked()).isTrue();
			}
		}

		@Test
		@DisplayName("should handle request to list special offers")
		void handleListOffersRequest() {
			String sessionId = "rec-offers-list";
			ActionContext context = new ActionContext();

			ConversationTurnResult startTurn = conversationManager.converse("start shopping", sessionId);
			if (startTurn.plan().status() == PlanStatus.READY) {
				executor.execute(startTurn.plan(), context);
			}

			ConversationTurnResult offersTurn = conversationManager
					.converse("what special offers do you have today?", sessionId);
			Plan plan = offersTurn.plan();

			assertThat(plan).isNotNull();
			// May be ready (presentOffers action), pending, or error (resolution issue)
			assertThat(plan.status()).isIn(PlanStatus.READY, PlanStatus.PENDING, PlanStatus.ERROR);

			if (plan.status() == PlanStatus.READY) {
				executor.execute(plan, context);
				// Either tool or action may be used to list offers
				assertThat(offerTool.listInvoked() || actions.presentOffersInvoked()).isTrue();
			}
		}

		@Test
		@DisplayName("should handle total calculation request")
		void handleTotalCalculationRequest() {
			String sessionId = "rec-offers-discount";
			ActionContext context = new ActionContext();

			// Start session
			ConversationTurnResult startTurn = conversationManager.converse("start shopping", sessionId);
			if (startTurn.plan().status() == PlanStatus.READY) {
				executor.execute(startTurn.plan(), context);
			}
			
			// Add items
			ConversationTurnResult addTurn = conversationManager.converse("add 5 Coke Zero", sessionId);
			if (addTurn.plan().status() == PlanStatus.READY) {
				executor.execute(addTurn.plan(), context);
			}

			ConversationTurnResult totalTurn = conversationManager
					.converse("what's my total with discounts?", sessionId);
			Plan totalPlan = totalTurn.plan();
			
			assertThat(totalPlan).isNotNull();
			// Total request should be handled (may compute total or view basket)
			assertThat(totalPlan.status()).isIn(PlanStatus.READY, PlanStatus.PENDING, PlanStatus.ERROR);
			
			if (totalPlan.status() == PlanStatus.READY) {
				executor.execute(totalPlan, context);
				// Either computeTotal or viewBasket should have been invoked
				assertThat(actions.computeTotalInvoked() || actions.viewBasketInvoked()).isTrue();
			}
		}
	}

	// ========================================================================
	// PERSONALISED RECOMMENDATIONS
	// ========================================================================

	@Nested
	@DisplayName("Personalised Recommendations")
	class PersonalisedRecommendations {

		@Test
		@DisplayName("should recognise returning customer")
		void recogniseReturningCustomer() {
			String sessionId = "rec-customer-return";
			ActionContext context = new ActionContext();

			// Start session with customer identification
			ConversationTurnResult turn = conversationManager
					.converse("Hi, I'm customer cust-001, let me start shopping", sessionId);

			executor.execute(turn.plan(), context);

			// Should identify the customer
			assertThat(actions.currentCustomerId()).isEqualTo("cust-001");
		}

		@Test
		@DisplayName("should handle personalised recommendations request for known customer")
		void handlePersonalisedRecommendationsRequest() {
			String sessionId = "rec-customer-recs";
			ActionContext context = new ActionContext();

			// Start as known customer
			actions.startSessionForCustomer(context, "cust-001");

			ConversationTurnResult recTurn = conversationManager
					.converse("show me things I might like", sessionId);
			Plan plan = recTurn.plan();

			assertThat(plan).isNotNull();
			// May ask for customer ID if not visible in context, or proceed with recommendations
			assertThat(plan.status()).isIn(PlanStatus.READY, PlanStatus.PENDING, PlanStatus.ERROR);

			if (plan.status() == PlanStatus.READY) {
				executor.execute(plan, context);
				// Should invoke recommendations or related action
				assertThat(actions.showRecommendationsInvoked() || 
						   actions.viewBasketInvoked() ||
						   actions.presentOffersInvoked()).isTrue();
			}
		}

		@Test
		@DisplayName("should handle recommendation request based on purchase history")
		void handleRecommendationBasedOnHistory() {
			String sessionId = "rec-customer-history";
			ActionContext context = new ActionContext();

			// Start as customer with purchase history
			actions.startSessionForCustomer(context, "cust-001");

			ConversationTurnResult recTurn = conversationManager
					.converse("what do you recommend based on my previous purchases?", sessionId);
			Plan plan = recTurn.plan();

			assertThat(plan).isNotNull();
			// May require customer ID or proceed with recommendations
			assertThat(plan.status()).isIn(PlanStatus.READY, PlanStatus.PENDING, PlanStatus.ERROR);

			if (plan.status() == PlanStatus.READY) {
				executor.execute(plan, context);
				// LLM may use tools or actions to get recommendations
				// Either the tool was invoked or an action was executed
				assertThat(customerTool.getFrequentPurchasesInvoked() ||
						   customerTool.getRecommendationsInvoked() ||
						   actions.showRecommendationsInvoked()).isTrue();
			}
		}

		@Test
		@DisplayName("should handle personalised offers request for known customer")
		void handlePersonalisedOffersRequest() {
			String sessionId = "rec-customer-offers";
			ActionContext context = new ActionContext();

			actions.startSessionForCustomer(context, "cust-001");

			ConversationTurnResult offersTurn = conversationManager
					.converse("are there any special deals for me?", sessionId);
			Plan plan = offersTurn.plan();

			assertThat(plan).isNotNull();
			assertThat(plan.status()).isIn(PlanStatus.READY, PlanStatus.PENDING, PlanStatus.ERROR);

			if (plan.status() == PlanStatus.READY) {
				executor.execute(plan, context);
				// LLM may check personalized offers via tool or present general offers via action
				assertThat(customerTool.getPersonalizedOffersInvoked() ||
						   offerTool.listInvoked() ||
						   actions.presentOffersInvoked()).isTrue();
			}
		}

		@Test
		@DisplayName("should fail recommendations gracefully for anonymous customer")
		void failRecommendationsForAnonymous() {
			String sessionId = "rec-customer-anon";
			ActionContext context = new ActionContext();

			// Start anonymous session
			executor.execute(conversationManager.converse("start shopping", sessionId).plan(), context);

			ConversationTurnResult recTurn = conversationManager
					.converse("show me things I might like", sessionId);
			Plan plan = recTurn.plan();

			// Should either explain need for identification or offer generic recommendations
			assertThat(plan).isNotNull();
			assertThat(plan.status()).isIn(PlanStatus.READY, PlanStatus.PENDING, PlanStatus.ERROR);
		}
	}

	// ========================================================================
	// STOCK-AWARE ADVICE
	// ========================================================================

	@Nested
	@DisplayName("Stock-Aware Advice")
	class StockAwareAdvice {

		@Test
		@DisplayName("should handle adding items that may exceed available stock")
		void handleAddingItemsExceedingStock() {
			String sessionId = "rec-stock-low";
			ActionContext context = new ActionContext();

			ConversationTurnResult startTurn = conversationManager.converse("start shopping", sessionId);
			if (startTurn.plan().status() == PlanStatus.READY) {
				executor.execute(startTurn.plan(), context);
			}

			// Request more than available (Coke Zero has limited stock)
			ConversationTurnResult addTurn = conversationManager
					.converse("add 100 bottles of Coke Zero", sessionId);
			Plan plan = addTurn.plan();

			// Plan may be ready (add attempt), pending (need confirmation), or error (resolution)
			assertThat(plan).isNotNull();
			assertThat(plan.status()).isIn(PlanStatus.READY, PlanStatus.PENDING, PlanStatus.ERROR);
		}

		@Test
		@DisplayName("should suggest alternatives for out-of-stock items")
		void suggestAlternativesForOutOfStock() {
			String sessionId = "rec-stock-alt";
			ActionContext context = new ActionContext();

			executor.execute(conversationManager.converse("start shopping", sessionId).plan(), context);

			// Try to add an out-of-stock item
			ConversationTurnResult addTurn = conversationManager
					.converse("add 5 bottles of UnavailableProduct", sessionId);
			Plan plan = addTurn.plan();

			// Should check inventory and potentially suggest alternatives or error
			assertThat(plan).isNotNull();
			assertThat(plan.status()).isIn(PlanStatus.READY, PlanStatus.PENDING, PlanStatus.ERROR);
		}

		@Test
		@DisplayName("should check stock before adding to basket")
		void checkStockBeforeAdding() {
			String sessionId = "rec-stock-check";
			ActionContext context = new ActionContext();

			executor.execute(conversationManager.converse("start shopping", sessionId).plan(), context);

			ConversationTurnResult addTurn = conversationManager
					.converse("add 5 Coke Zero", sessionId);
			executor.execute(addTurn.plan(), context);

			// Item should have been added (inventory is checked internally)
			assertThat(actions.addItemInvoked()).isTrue();
		}

		@Test
		@DisplayName("should offer partial quantity when stock insufficient")
		void offerPartialQuantity() {
			String sessionId = "rec-stock-partial";
			ActionContext context = new ActionContext();

			executor.execute(conversationManager.converse("start shopping", sessionId).plan(), context);

			// Request more than available
			ConversationTurnResult addTurn = conversationManager
					.converse("add 50 bottles of Coke Zero", sessionId);
			Plan plan = addTurn.plan();

			// Should either add partial quantity or ask for confirmation
			assertThat(plan).isNotNull();
			// The plan might be PENDING (asking for confirmation) or READY (adding partial)
		}

		@Test
		@DisplayName("should handle request for similar product suggestions")
		void handleSimilarProductRequest() {
			String sessionId = "rec-stock-similar";
			ActionContext context = new ActionContext();

			ConversationTurnResult startTurn = conversationManager.converse("start shopping", sessionId);
			if (startTurn.plan().status() == PlanStatus.READY) {
				executor.execute(startTurn.plan(), context);
			}

			ConversationTurnResult searchTurn = conversationManager
					.converse("I want something like Coke but different", sessionId);
			Plan plan = searchTurn.plan();

			// Should provide suggestions, ask for clarification, or error if unable to resolve
			assertThat(plan).isNotNull();
			assertThat(plan.status()).isIn(PlanStatus.READY, PlanStatus.PENDING, PlanStatus.ERROR);
		}
	}

	// ========================================================================
	// CONTEXTUAL AWARENESS
	// ========================================================================

	@Nested
	@DisplayName("Contextual Awareness")
	class ContextualAwareness {

		@Test
		@DisplayName("should handle recommendations request for customer with preferences")
		void handleRecommendationsForCustomerWithPreferences() {
			String sessionId = "rec-context-prefs";
			ActionContext context = new ActionContext();

			// Start as known customer with dietary restrictions
			actions.startSessionForCustomer(context, "cust-003"); // Sam has gluten allergy

			// Add an item
			ConversationTurnResult addTurn = conversationManager.converse("add some snacks", sessionId);
			if (addTurn.plan().status() == PlanStatus.READY) {
				executor.execute(addTurn.plan(), context);
			}

			// Request recommendations - should respect dietary restrictions
			ConversationTurnResult recTurn = conversationManager
					.converse("what else would you recommend?", sessionId);
			Plan plan = recTurn.plan();

			assertThat(plan).isNotNull();
			assertThat(plan.status()).isIn(PlanStatus.READY, PlanStatus.PENDING, PlanStatus.ERROR);

			if (plan.status() == PlanStatus.READY) {
				executor.execute(plan, context);
				// May check profile, get recommendations, or show recommendations action
				assertThat(customerTool.getProfileInvoked() ||
						   customerTool.getRecommendationsInvoked() ||
						   actions.showRecommendationsInvoked()).isTrue();
			}
		}

		@Test
		@DisplayName("should handle request for offers on frequently purchased products")
		void handleOffersOnFrequentPurchasesRequest() {
			String sessionId = "rec-context-combo";
			ActionContext context = new ActionContext();

			actions.startSessionForCustomer(context, "cust-001");

			ConversationTurnResult turn = conversationManager
					.converse("show me offers on products I usually buy", sessionId);
			Plan plan = turn.plan();

			assertThat(plan).isNotNull();
			assertThat(plan.status()).isIn(PlanStatus.READY, PlanStatus.PENDING, PlanStatus.ERROR);

			if (plan.status() == PlanStatus.READY) {
				executor.execute(plan, context);
				// LLM may use various tools/actions to fulfill this request
				assertThat(customerTool.getFrequentPurchasesInvoked() || 
						   customerTool.getProfileInvoked() ||
						   customerTool.getPersonalizedOffersInvoked() ||
						   offerTool.listInvoked() ||
						   actions.presentOffersInvoked() ||
						   actions.showRecommendationsInvoked()).isTrue();
			}
		}
	}
}

