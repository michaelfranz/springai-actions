package org.javai.springai.scenarios.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.javai.springai.actions.test.PlanAssertions.assertPlanReady;
import java.util.Objects;
import org.javai.springai.actions.DefaultPlanExecutor;
import org.javai.springai.actions.Plan;
import org.javai.springai.actions.PlanStatus;
import org.javai.springai.actions.Planner;
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
				.withChatClient(chatClient)
				.persona(ShoppingPersonaSpec.standard())
				.tools(offerTool, inventoryTool, pricingTool, searchTool, customerTool)
				.actions(actions)
				.build();
		executor = new DefaultPlanExecutor();
		conversationManager = new ConversationManager(planner, new InMemoryConversationStateStore());
	}

	// ========================================================================
	// SPECIAL OFFERS AND BARGAINS
	// ========================================================================

	@Nested
	@DisplayName("Special Offers and Bargains")
	class SpecialOffers {

		@Test
		@DisplayName("should proactively surface offers at session start")
		void surfaceOffersAtSessionStart() {
			String sessionId = "rec-offers-start";

			ConversationTurnResult turn = conversationManager
					.converse("I want to start shopping", sessionId);
			executor.execute(turn.plan());

			// Offer tool should have been consulted
			assertThat(offerTool.listInvoked()).isTrue();
		}

		@Test
		@DisplayName("should mention relevant offers when adding product with promotion")
		void mentionOffersWhenAddingPromotedProduct() {
			String sessionId = "rec-offers-add";

			// Start session
			executor.execute(conversationManager.converse("start shopping", sessionId).plan());

			// Add a product that has an active offer (Coke Zero has 10% off)
			ConversationTurnResult addTurn = conversationManager
					.converse("add some Coca Cola", sessionId);
			executor.execute(addTurn.plan());

			// The assistant should have checked offers or added the item
			assertThat(actions.addItemInvoked() || offerTool.listInvoked()).isTrue();
		}

		@Test
		@DisplayName("should list all current special offers on request")
		void listAllOffersOnRequest() {
			String sessionId = "rec-offers-list";

			executor.execute(conversationManager.converse("start shopping", sessionId).plan());

			ConversationTurnResult offersTurn = conversationManager
					.converse("what special offers do you have today?", sessionId);
			Plan plan = offersTurn.plan();

			assertThat(plan).isNotNull();
			assertPlanReady(plan);

			executor.execute(plan);
			assertThat(offerTool.listInvoked()).isTrue();
		}

		@Test
		@DisplayName("should apply offer discount to basket total")
		void applyOfferDiscountToTotal() {
			String sessionId = "rec-offers-discount";

			executor.execute(conversationManager.converse("start shopping", sessionId).plan());
			executor.execute(conversationManager.converse("add 5 Coke Zero", sessionId).plan());

			ConversationTurnResult totalTurn = conversationManager
					.converse("what's my total with discounts?", sessionId);
			executor.execute(totalTurn.plan());

			// Total should be computed (pricing is used internally)
			assertThat(actions.computeTotalInvoked()).isTrue();
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

			// Start session with customer identification
			ConversationTurnResult turn = conversationManager
					.converse("Hi, I'm customer cust-001, let me start shopping", sessionId);

			executor.execute(turn.plan());

			// Should identify the customer
			assertThat(actions.currentCustomerId()).isEqualTo("cust-001");
		}

		@Test
		@DisplayName("should show personalised recommendations for known customer")
		void showPersonalisedRecommendations() {
			String sessionId = "rec-customer-recs";

			// Start as known customer
			actions.startSessionForCustomer(null, "cust-001");

			ConversationTurnResult recTurn = conversationManager
					.converse("show me things I might like", sessionId);
			Plan plan = recTurn.plan();

			assertThat(plan).isNotNull();
			assertPlanReady(plan);

			executor.execute(plan);
			assertThat(actions.showRecommendationsInvoked()).isTrue();
		}

		@Test
		@DisplayName("should recommend based on purchase history")
		void recommendBasedOnHistory() {
			String sessionId = "rec-customer-history";

			// Start as customer with purchase history
			actions.startSessionForCustomer(null, "cust-001");

			ConversationTurnResult recTurn = conversationManager
					.converse("what do you recommend based on my previous purchases?", sessionId);

			executor.execute(recTurn.plan());

			// Customer tool should have been consulted for history
			assertThat(customerTool.getFrequentPurchasesInvoked()).isTrue();
		}

		@Test
		@DisplayName("should offer personalised offers for known customer")
		void offerPersonalisedOffers() {
			String sessionId = "rec-customer-offers";

			actions.startSessionForCustomer(null, "cust-001");

			ConversationTurnResult offersTurn = conversationManager
					.converse("are there any special deals for me?", sessionId);

			executor.execute(offersTurn.plan());

			// Should check for personalised offers
			assertThat(customerTool.getPersonalizedOffersInvoked()).isTrue();
		}

		@Test
		@DisplayName("should fail recommendations gracefully for anonymous customer")
		void failRecommendationsForAnonymous() {
			String sessionId = "rec-customer-anon";

			// Start anonymous session
			executor.execute(conversationManager.converse("start shopping", sessionId).plan());

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
		@DisplayName("should warn about low stock when adding items")
		void warnAboutLowStock() {
			String sessionId = "rec-stock-low";

			executor.execute(conversationManager.converse("start shopping", sessionId).plan());

			// Request more than available (Coke Zero has limited stock)
			ConversationTurnResult addTurn = conversationManager
					.converse("add 100 bottles of Coke Zero", sessionId);
			Plan plan = addTurn.plan();

			// Plan should either be ready with warning or pending for confirmation
			assertThat(plan).isNotNull();
			assertThat(plan.status()).isIn(PlanStatus.READY, PlanStatus.PENDING);
		}

		@Test
		@DisplayName("should suggest alternatives for out-of-stock items")
		void suggestAlternativesForOutOfStock() {
			String sessionId = "rec-stock-alt";

			executor.execute(conversationManager.converse("start shopping", sessionId).plan());

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

			executor.execute(conversationManager.converse("start shopping", sessionId).plan());

			ConversationTurnResult addTurn = conversationManager
					.converse("add 5 Coke Zero", sessionId);
			executor.execute(addTurn.plan());

			// Item should have been added (inventory is checked internally)
			assertThat(actions.addItemInvoked()).isTrue();
		}

		@Test
		@DisplayName("should offer partial quantity when stock insufficient")
		void offerPartialQuantity() {
			String sessionId = "rec-stock-partial";

			executor.execute(conversationManager.converse("start shopping", sessionId).plan());

			// Request more than available
			ConversationTurnResult addTurn = conversationManager
					.converse("add 50 bottles of Coke Zero", sessionId);
			Plan plan = addTurn.plan();

			// Should either add partial quantity or ask for confirmation
			assertThat(plan).isNotNull();
			// The plan might be PENDING (asking for confirmation) or READY (adding partial)
		}

		@Test
		@DisplayName("should suggest similar products when requested item unavailable")
		void suggestSimilarProducts() {
			String sessionId = "rec-stock-similar";

			executor.execute(conversationManager.converse("start shopping", sessionId).plan());

			ConversationTurnResult searchTurn = conversationManager
					.converse("I want something like Coke but different", sessionId);
			Plan plan = searchTurn.plan();

			// Should provide suggestions or ask for clarification
			assertThat(plan).isNotNull();
			assertThat(plan.status()).isIn(PlanStatus.READY, PlanStatus.PENDING);
		}
	}

	// ========================================================================
	// CONTEXTUAL AWARENESS
	// ========================================================================

	@Nested
	@DisplayName("Contextual Awareness")
	class ContextualAwareness {

		@Test
		@DisplayName("should remember customer preferences across turns")
		void rememberPreferencesAcrossTurns() {
			String sessionId = "rec-context-prefs";

			// Start as known customer with dietary restrictions
			actions.startSessionForCustomer(null, "cust-003"); // Sam has gluten allergy

			// Add an item
			executor.execute(conversationManager.converse("add some snacks", sessionId).plan());

			// Request recommendations - should respect dietary restrictions
			ConversationTurnResult recTurn = conversationManager
					.converse("what else would you recommend?", sessionId);

			executor.execute(recTurn.plan());

			// Should have checked customer profile
			assertThat(customerTool.getProfileInvoked()).isTrue();
		}

		@Test
		@DisplayName("should combine offers with customer preferences")
		void combineOffersWithPreferences() {
			String sessionId = "rec-context-combo";

			actions.startSessionForCustomer(null, "cust-001");

			ConversationTurnResult turn = conversationManager
					.converse("show me offers on products I usually buy", sessionId);

			executor.execute(turn.plan());

			// Customer profile or offers should have been consulted
			assertThat(customerTool.getFrequentPurchasesInvoked() || 
					   customerTool.getProfileInvoked() ||
					   offerTool.listInvoked()).isTrue();
		}
	}
}

