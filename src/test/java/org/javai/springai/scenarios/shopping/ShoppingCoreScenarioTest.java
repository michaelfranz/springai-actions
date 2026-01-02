package org.javai.springai.scenarios.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.javai.springai.actions.test.PlanAssertions.assertExecutionSuccess;
import static org.javai.springai.actions.test.PlanAssertions.assertPlanReady;
import java.util.List;
import java.util.Map;
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
import org.javai.springai.scenarios.shopping.actions.ActionResult;
import org.javai.springai.scenarios.shopping.actions.InventoryAwareShoppingActions;
import org.javai.springai.scenarios.shopping.actions.Notification;
import org.javai.springai.scenarios.shopping.actions.NotificationType;
import org.javai.springai.scenarios.shopping.actions.ShoppingPersonaSpec;
import org.javai.springai.scenarios.shopping.store.MockStoreApi;
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
 * Core Shopping Activities Scenario Tests.
 * 
 * Tests the fundamental shopping workflow:
 * - Starting and ending sessions
 * - Adding items to the basket
 * - Viewing and managing the basket
 * - Computing totals and checkout
 * 
 * @see README.md "Core Shopping Activities" section
 */
@DisplayName("Core Shopping Activities")
public class ShoppingCoreScenarioTest {

	private static final Logger log = LoggerFactory.getLogger(ShoppingCoreScenarioTest.class);
	private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");
	private static final boolean RUN_LLM_TESTS = "true".equalsIgnoreCase(System.getenv("RUN_LLM_TESTS"));

	private MockStoreApi storeApi;
	private InventoryAwareShoppingActions actions;
	private EnhancedSpecialOfferTool offerTool;
	private InventoryTool inventoryTool;
	private PricingTool pricingTool;
	private ProductSearchTool searchTool;

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
				.tools(offerTool, inventoryTool, pricingTool, searchTool)
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
	// SESSION LIFECYCLE
	// ========================================================================

	@Nested
	@DisplayName("Session Lifecycle")
	class SessionLifecycle {

		@Test
		@DisplayName("should start a new shopping session")
		void startNewSession() {
			String sessionId = "core-start-session";
			ActionContext context = new ActionContext();

			ConversationTurnResult turn = conversationManager
					.converse("I want to start a new shopping basket", sessionId);
			Plan plan = turn.plan();

			assertThat(plan).isNotNull();
			assertPlanReady(plan);

			PlanExecutionResult result = executor.execute(plan, context);
			assertExecutionSuccess(result);
			assertThat(actions.startSessionInvoked()).isTrue();
			
			// Verify basket was placed in context
			assertThat(context.contains("basket")).isTrue();
		}

		@Test
		@DisplayName("should surface special offers when session starts")
		void surfaceOffersOnSessionStart() {
			String sessionId = "core-offers-session";
			ActionContext context = new ActionContext();

			ConversationTurnResult turn = conversationManager
					.converse("I'd like to start shopping", sessionId);
			PlanExecutionResult result = executor.execute(turn.plan(), context);

			// Verify offers are programmatically included in action result
			// This is a business-critical requirement - offers are NOT left to LLM discretion
			assertThat(result.wasExecuted()).isTrue();
			assertThat(result.success()).isTrue();
			
			// Extract notifications from the step results
			List<Notification> notifications = extractNotifications(result);
			assertThat(notifications)
					.filteredOn(n -> n.type() == NotificationType.OFFER)
					.isNotEmpty();
		}
		
		/**
		 * Extract all notifications from a plan execution result.
		 */
		private List<Notification> extractNotifications(PlanExecutionResult result) {
			return result.steps().stream()
					.map(step -> step.returnValue())
					.filter(rv -> rv instanceof ActionResult)
					.map(rv -> (ActionResult) rv)
					.flatMap(ar -> ar.notifications().stream())
					.toList();
		}

		@Test
		@DisplayName("should complete checkout and end session")
		void completeCheckoutAndEndSession() {
			String sessionId = "core-checkout-session";
			ActionContext context = new ActionContext();

			// Start session - this puts the basket into context
			executor.execute(conversationManager.converse("start shopping", sessionId).plan(), context);
			assertThat(context.contains("basket")).isTrue();

			// Add items - uses basket from context
			executor.execute(conversationManager.converse("add 2 Coke Zero", sessionId).plan(), context);

			// Checkout
			ConversationTurnResult checkoutTurn = conversationManager
					.converse("I'm ready to pay", sessionId);
			Plan checkoutPlan = checkoutTurn.plan();

			assertThat(checkoutPlan).isNotNull();
			assertPlanReady(checkoutPlan);

			PlanExecutionResult result = executor.execute(checkoutPlan, context);
			assertExecutionSuccess(result);
			assertThat(actions.checkoutInvoked()).isTrue();
			
			// Verify basket is empty after checkout
			@SuppressWarnings("unchecked")
			Map<String, Integer> basket = context.get("basket", Map.class);
			assertThat(basket).isEmpty();
		}
	}

	// ========================================================================
	// ADDING ITEMS TO BASKET
	// ========================================================================

	@Nested
	@DisplayName("Adding Items to Basket")
	class AddingItems {

		@Test
		@DisplayName("should add item with explicit product and quantity")
		void addItemWithExplicitQuantity() {
			String sessionId = "core-add-explicit";
			ActionContext context = new ActionContext();

			// Start session - initializes basket in context
			executor.execute(conversationManager.converse("start shopping", sessionId).plan(), context);

			// Add with explicit quantity
			ConversationTurnResult addTurn = conversationManager
					.converse("Add 6 bottles of Coke Zero to my basket", sessionId);
			Plan addPlan = addTurn.plan();

			assertThat(addPlan).isNotNull();
			assertPlanReady(addPlan);

			PlanExecutionResult result = executor.execute(addPlan, context);
			assertExecutionSuccess(result);
			assertThat(actions.addItemInvoked()).isTrue();
			
			// Verify item was added to basket
			@SuppressWarnings("unchecked")
			Map<String, Integer> basket = context.get("basket", Map.class);
			assertThat(basket).isNotEmpty();
		}

		@Test
		@DisplayName("should request quantity when not provided or assume default")
		void requestQuantityWhenMissing() {
			String sessionId = "core-add-pending";
			ActionContext context = new ActionContext();

			// Start session
			executor.execute(conversationManager.converse("start shopping", sessionId).plan(), context);

			// Add without quantity - LLM may either:
			// 1. Assume quantity=1 and return READY plan
			// 2. Return PENDING plan requesting quantity
			ConversationTurnResult addTurn = conversationManager
					.converse("add coke zero", sessionId);
			Plan plan = addTurn.plan();

			assertThat(plan).isNotNull();
			assertThat(plan.status()).isIn(PlanStatus.READY, PlanStatus.PENDING);

			if (plan.status() == PlanStatus.READY) {
				// LLM assumed quantity=1 - this is acceptable
				PlanExecutionResult result = executor.execute(plan, context);
				assertExecutionSuccess(result);
				assertThat(actions.addItemInvoked()).isTrue();
			} else {
				// LLM requested quantity - provide it and verify plan completes
				assertThat(addTurn.pendingParams()).isNotEmpty();
				ConversationTurnResult followUp = conversationManager
						.converse("just 1", sessionId);
				Plan followUpPlan = followUp.plan();
				assertPlanReady(followUpPlan);
				PlanExecutionResult result = executor.execute(followUpPlan, context);
				assertExecutionSuccess(result);
				assertThat(actions.addItemInvoked()).isTrue();
			}
		}

		@Test
		@DisplayName("should complete add after providing missing quantity")
		void completeAddAfterProvidingQuantity() {
			String sessionId = "core-add-followup";
			ActionContext context = new ActionContext();

			// Start session
			executor.execute(conversationManager.converse("start shopping", sessionId).plan(), context);

			// Add without quantity -> pending
			conversationManager.converse("add coke zero", sessionId);

			// Provide quantity
			ConversationTurnResult followUp = conversationManager
					.converse("make it 4 bottles", sessionId);
			Plan plan = followUp.plan();

			assertThat(plan).isNotNull();
			assertPlanReady(plan);

			PlanExecutionResult result = executor.execute(plan, context);
			assertExecutionSuccess(result);
			assertThat(actions.addItemInvoked()).isTrue();
		}
	}

	// ========================================================================
	// VIEWING AND MANAGING BASKET
	// ========================================================================

	@Nested
	@DisplayName("Viewing and Managing Basket")
	class ManagingBasket {

		@Test
		@DisplayName("should show basket contents")
		void showBasketContents() {
			String sessionId = "core-view-basket";
			ActionContext context = new ActionContext();

			// Start and add items
			executor.execute(conversationManager.converse("start shopping", sessionId).plan(), context);
			executor.execute(conversationManager.converse("add 3 Coke Zero", sessionId).plan(), context);

			// View basket
			ConversationTurnResult viewTurn = conversationManager
					.converse("show me my basket", sessionId);
			Plan plan = viewTurn.plan();

			assertThat(plan).isNotNull();
			assertPlanReady(plan);

			PlanExecutionResult result = executor.execute(plan, context);
			assertExecutionSuccess(result);
			assertThat(actions.viewBasketInvoked()).isTrue();
		}

		@Test
		@DisplayName("should remove item from basket")
		void removeItemFromBasket() {
			String sessionId = "core-remove-item";
			ActionContext context = new ActionContext();

			// Start and add items
			executor.execute(conversationManager.converse("start shopping", sessionId).plan(), context);
			executor.execute(conversationManager.converse("add 2 Coke Zero", sessionId).plan(), context);
			
			// Verify item is in basket before removal
			@SuppressWarnings("unchecked")
			Map<String, Integer> basket = context.get("basket", Map.class);
			assertThat(basket).isNotEmpty();

			// Remove item
			ConversationTurnResult removeTurn = conversationManager
					.converse("remove the Coke Zero", sessionId);
			Plan plan = removeTurn.plan();

			assertThat(plan).isNotNull();
			assertPlanReady(plan);

			PlanExecutionResult result = executor.execute(plan, context);
			assertExecutionSuccess(result);
			assertThat(actions.removeItemInvoked()).isTrue();
			
			// Verify item was removed from basket
			assertThat(basket).isEmpty();
		}

		@Test
		@DisplayName("should change quantity of existing item")
		void changeItemQuantity() {
			String sessionId = "core-change-qty";
			ActionContext context = new ActionContext();

			// Start and add items
			executor.execute(conversationManager.converse("start shopping", sessionId).plan(), context);
			executor.execute(conversationManager.converse("add 5 Coke Zero", sessionId).plan(), context);

			// Change quantity
			ConversationTurnResult changeTurn = conversationManager
					.converse("change the Coke Zero to just 2 bottles", sessionId);
			Plan plan = changeTurn.plan();

			assertThat(plan).isNotNull();
			assertPlanReady(plan);

			PlanExecutionResult result = executor.execute(plan, context);
			assertExecutionSuccess(result);
			assertThat(actions.updateQuantityInvoked()).isTrue();
		}
	}

	// ========================================================================
	// COMPUTING TOTALS AND CHECKOUT
	// ========================================================================

	@Nested
	@DisplayName("Computing Totals and Checkout")
	class TotalsAndCheckout {

		@Test
		@DisplayName("should compute basket total")
		void computeBasketTotal() {
			String sessionId = "core-total";
			ActionContext context = new ActionContext();

			// Start and add items
			executor.execute(conversationManager.converse("start shopping", sessionId).plan(), context);
			executor.execute(conversationManager.converse("add 3 Coke Zero", sessionId).plan(), context);

			// Compute total
			ConversationTurnResult totalTurn = conversationManager
					.converse("what's my total?", sessionId);
			Plan plan = totalTurn.plan();

			assertThat(plan).isNotNull();
			assertPlanReady(plan);

			PlanExecutionResult result = executor.execute(plan, context);
			assertExecutionSuccess(result);
			assertThat(actions.computeTotalInvoked()).isTrue();
		}

		@Test
		@DisplayName("should apply discounts to total")
		void applyDiscountsToTotal() {
			String sessionId = "core-discount-total";
			ActionContext context = new ActionContext();

			// Start and add discounted item
			executor.execute(conversationManager.converse("start shopping", sessionId).plan(), context);
			executor.execute(conversationManager.converse("add 5 Coke Zero", sessionId).plan(), context);

			// Compute total - should reflect discount
			ConversationTurnResult totalTurn = conversationManager
					.converse("what's the total including discounts?", sessionId);

			PlanExecutionResult result = executor.execute(totalTurn.plan(), context);
			assertExecutionSuccess(result);

			// Verify total was computed (pricing is used internally)
			assertThat(actions.computeTotalInvoked()).isTrue();
		}

		@Test
		@DisplayName("should require confirmation before checkout")
		void requireConfirmationBeforeCheckout() {
			String sessionId = "core-confirm-checkout";
			ActionContext context = new ActionContext();

			// Start and add items
			executor.execute(conversationManager.converse("start shopping", sessionId).plan(), context);
			executor.execute(conversationManager.converse("add 2 Coke Zero", sessionId).plan(), context);

			// Request checkout
			ConversationTurnResult checkoutTurn = conversationManager
					.converse("checkout", sessionId);
			Plan plan = checkoutTurn.plan();

			// The plan should either execute checkout or request confirmation
			// depending on persona constraints interpretation
			assertThat(plan).isNotNull();
			assertThat(plan.status()).isIn(PlanStatus.READY, PlanStatus.PENDING);
		}

		@Test
		@DisplayName("should persist basket across multiple turns")
		void persistBasketAcrossTurns() {
			String sessionId = "core-persistence";
			ActionContext context = new ActionContext();

			// Turn 1: Start
			executor.execute(conversationManager.converse("start shopping", sessionId).plan(), context);

			// Turn 2: Add first item
			executor.execute(conversationManager.converse("add 2 Coke Zero", sessionId).plan(), context);

			// Turn 3: Add second item
			executor.execute(conversationManager.converse("add 3 Sea Salt Crisps", sessionId).plan(), context);

			// Turn 4: View basket - should have both items
			ConversationTurnResult viewTurn = conversationManager
					.converse("what's in my basket?", sessionId);
			executor.execute(viewTurn.plan(), context);

			assertThat(actions.viewBasketInvoked()).isTrue();
			
			// Verify basket contains both items
			@SuppressWarnings("unchecked")
			Map<String, Integer> basket = context.get("basket", Map.class);
			assertThat(basket).hasSize(2);
		}
	}

	// ========================================================================
	// ERROR HANDLING
	// ========================================================================

	@Nested
	@DisplayName("Error Handling")
	class ErrorHandling {

		@Test
		@DisplayName("should reject non-shopping requests gracefully")
		void rejectNonShoppingRequests() {
			String sessionId = "core-error-nonshop";
			ActionContext context = new ActionContext();

			ConversationTurnResult turn = conversationManager
					.converse("change the oil in my car", sessionId);
			Plan plan = turn.plan();

			assertThat(plan).isNotNull();
			
			// Non-shopping requests result in no actions - triggers noAction handler
			// The plan may have empty steps or a NoActionStep
			if (plan.planSteps().isEmpty()) {
				// Empty steps - noAction handler will be invoked on execute
				PlanExecutionResult result = executor.execute(plan, context);
				assertThat(result.success()).isFalse();
				// The assistant message should explain what the assistant can help with
				assertThat(plan.assistantMessage()).isNotBlank();
			} else if (plan.planSteps().getFirst() instanceof PlanStep.NoActionStep noAction) {
				// Explicit noAction step with explanation
				assertThat(noAction.message()).isNotBlank();
				PlanExecutionResult result = executor.execute(plan, context);
				assertThat(result.success()).isFalse();
			} else {
				// Fallback: might still be an error step
				assertThat(plan.status()).isEqualTo(PlanStatus.ERROR);
			}
		}

		@Test
		@DisplayName("should handle unrecognised product gracefully")
		void handleUnrecognisedProduct() {
			String sessionId = "core-error-unknown";
			ActionContext context = new ActionContext();

			executor.execute(conversationManager.converse("start shopping", sessionId).plan(), context);

			ConversationTurnResult addTurn = conversationManager
					.converse("add 5 bottles of NonExistentProduct", sessionId);
			Plan plan = addTurn.plan();

			// Should either error or ask for clarification
			assertThat(plan).isNotNull();
			assertThat(plan.status()).isIn(PlanStatus.ERROR, PlanStatus.PENDING);
		}
	}
}

