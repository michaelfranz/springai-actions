package org.javai.springai.scenarios.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.javai.springai.actions.test.PlanAssertions.assertExecutionSuccess;
import static org.javai.springai.actions.test.PlanAssertions.assertPlanReady;
import java.util.Map;
import java.util.Objects;
import org.javai.springai.actions.DefaultPlanExecutor;
import org.javai.springai.actions.PersonaSpec;
import org.javai.springai.actions.Plan;
import org.javai.springai.actions.PlanExecutionResult;
import org.javai.springai.actions.PlanStatus;
import org.javai.springai.actions.PlanStep;
import org.javai.springai.actions.Planner;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.conversation.ConversationManager;
import org.javai.springai.actions.conversation.ConversationTurnResult;
import org.javai.springai.actions.conversation.InMemoryConversationStateStore;
import org.javai.springai.scenarios.shopping.actions.ShoppingActions;
import org.javai.springai.scenarios.shopping.tools.SpecialOfferTool;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

public class ShoppingApplicationScenarioTest {

	private static final Logger log = LoggerFactory.getLogger(ShoppingApplicationScenarioTest.class);
	private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");
	private static final boolean RUN_LLM_TESTS = "true".equalsIgnoreCase(System.getenv("RUN_LLM_TESTS"));

	Planner planner;
	DefaultPlanExecutor executor;
	ConversationManager conversationManager;
	ShoppingActions shoppingActions;
	SpecialOfferTool specialOfferTool;


	@BeforeEach
	void setUp() {
		Assumptions.assumeTrue(RUN_LLM_TESTS, "Set RUN_LLM_TESTS=true to enable LLM integration tests");
		Assumptions.assumeTrue(OPENAI_API_KEY != null && !OPENAI_API_KEY.isBlank(),
				"OPENAI_API_KEY must be set for this integration test");

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

		shoppingActions = new ShoppingActions();
		specialOfferTool = new SpecialOfferTool();

		PersonaSpec persona = PersonaSpec.builder()
				.name("shopping-assistant")
				.role("Helpful shopping assistant with access to inventory and offers")
				.principles(java.util.List.of(
						"Confirm missing quantities before adding items.",
						"Surface relevant special offers before checkout.",
						"Keep responses concise and action-focused."
				))
				.constraints(java.util.List.of(
						"Do not invent products or prices; use provided tools/actions.",
						"Do not commit checkout without user confirmation."
				))
				.styleGuidance(java.util.List.of(
						"Friendly but brief tone.",
						"Summarize basket changes clearly."
				))
				.build();

		planner = Planner.builder()
				.withChatClient(chatClient)
				.persona(persona)
				.tools(specialOfferTool)
				.actions(shoppingActions)
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

	@Test
	void startSessionAndOffersTest() {
		String request = "I want to start a new shopping basket";
		ActionContext context = new ActionContext();
		
		ConversationTurnResult turn = conversationManager.converse(request, "display-session");
		Plan plan = turn.plan();

		assertThat(plan).isNotNull();
		assertPlanReady(plan);
		assertThat(plan.planSteps()).hasSize(1);
		PlanStep step = plan.planSteps().getFirst();
		assertThat(step).isInstanceOf(PlanStep.ActionStep.class);

		PlanExecutionResult executed = executor.execute(plan, context);
		assertExecutionSuccess(executed);
		assertThat(shoppingActions.startSessionInvoked()).isTrue();
		assertThat(shoppingActions.presentOffersInvoked()).isTrue();
		assertThat(specialOfferTool.listInvoked()).isTrue();
		
		// Verify basket was placed in context
		assertThat(context.contains("basket")).isTrue();
	}

	@Test
	void addCokeZeroTest() {
		String request = "add 6 bottles of Coke Zero to my basket";
		ActionContext context = new ActionContext();
		
		// First start a session to initialize the basket in context
		executor.execute(conversationManager.converse("start shopping", "export-session").plan(), context);
		
		ConversationTurnResult turn = conversationManager.converse(request, "export-session");
		Plan plan = turn.plan();
		assertThat(plan).isNotNull();
		assertPlanReady(plan);
		assertThat(plan.planSteps()).hasSize(1);
		PlanStep step = plan.planSteps().getFirst();
		assertThat(step).isInstanceOf(PlanStep.ActionStep.class);

		PlanExecutionResult executed = executor.execute(plan, context);
		assertExecutionSuccess(executed);
		assertThat(shoppingActions.addItemInvoked()).isTrue();
		assertThat(shoppingActions.lastAddItem()).isNotNull();
		assertThat(shoppingActions.lastAddItem().product()).containsIgnoringCase("coke zero");
		assertThat(shoppingActions.lastAddItem().quantity()).isEqualTo(6);
	}

	@Test
	void unableToIdentifyActionTest() {
		// No action supports arbitrary vehicle maintenance
		String request = "change the oil in my car";
		ConversationTurnResult turn = conversationManager.converse(request, "anova-session");
		Plan plan = turn.plan();
		assertThat(plan).isNotNull();
		assertThat(plan.status()).isEqualTo(PlanStatus.ERROR);
		assertThat(plan.planSteps()).hasSize(1);
		PlanStep step = plan.planSteps().getFirst();
		assertThat(step).isInstanceOf(PlanStep.ErrorStep.class);
	}

	@Test
	void requireMoreInformationTest() {
		// Add item requires quantity
		String request = "add coke zero";
		ConversationTurnResult turn = conversationManager.converse(request, "pending-session");
		Plan plan = turn.plan();
		assertThat(plan).isNotNull();
		assertThat(plan.status()).isEqualTo(PlanStatus.PENDING);
		assertThat(turn.pendingParams()).isNotEmpty();
		assertThat(turn.pendingParams().stream().map(PlanStep.PendingParam::name))
				.anyMatch(name -> name.equals("quantity") || name.equals("product"));
	}

	@Test
	void requireMoreInformationFollowUpProvidesMissingBundleId() {
		String sessionId = "shopping-session";
		ActionContext context = new ActionContext();
		
		// Start session to initialize basket
		executor.execute(conversationManager.converse("start shopping", sessionId).plan(), context);

		// Turn 1: missing quantity -> expect pending
		ConversationTurnResult firstTurn = conversationManager
				.converse("add coke zero", sessionId);
		Plan firstPlan = firstTurn.plan();
		assertThat(firstPlan).isNotNull();
		assertThat(firstPlan.status()).isEqualTo(PlanStatus.PENDING);
		assertThat(firstTurn.pendingParams()).isNotEmpty();

		// Turn 2: user supplies only the missing info; desired behavior is that
		// the system merges context and produces an executable step (documented scenario)
		ConversationTurnResult secondTurn = conversationManager
				.converse("add 4 bottles", sessionId);
		Plan secondPlan = secondTurn.plan();
		assertThat(secondPlan).isNotNull();
		// Ideal outcome after context merge: actionable step, no pending
		assertThat(secondPlan.planSteps().getFirst()).isInstanceOf(PlanStep.ActionStep.class);

		PlanExecutionResult executed = executor.execute(secondPlan, context);
		assertExecutionSuccess(executed);
		assertThat(shoppingActions.addItemInvoked()).isTrue();
	}

	@Test
	void viewBasketSummaryTest() {
		String sessionId = "basket-view-session";
		ActionContext context = new ActionContext();

		// Turn 1: Start session
		ConversationTurnResult startTurn = conversationManager
				.converse("I want to start shopping", sessionId);
		executor.execute(startTurn.plan(), context);

		// Turn 2: Add an item
		ConversationTurnResult addTurn = conversationManager
				.converse("add 3 bottles of Coke Zero", sessionId);
		executor.execute(addTurn.plan(), context);

		// Turn 3: View basket
		ConversationTurnResult viewTurn = conversationManager
				.converse("show me my basket", sessionId);
		Plan viewPlan = viewTurn.plan();

		assertThat(viewPlan).isNotNull();
		assertPlanReady(viewPlan);
		assertThat(viewPlan.planSteps()).hasSize(1);
		assertThat(viewPlan.planSteps().getFirst()).isInstanceOf(PlanStep.ActionStep.class);

		PlanExecutionResult executed = executor.execute(viewPlan, context);
		assertExecutionSuccess(executed);
		assertThat(shoppingActions.viewBasketInvoked()).isTrue();
		assertThat(shoppingActions.getBasketState()).containsEntry("Coke Zero", 3);
	}

	@Test
	void removeItemFromBasketTest() {
		String sessionId = "remove-item-session";
		ActionContext context = new ActionContext();

		// Turn 1: Start session
		ConversationTurnResult startTurn = conversationManager
				.converse("start shopping", sessionId);
		executor.execute(startTurn.plan(), context);

		// Turn 2: Add item
		ConversationTurnResult addTurn = conversationManager
				.converse("add 2 bottles of Coke Zero", sessionId);
		executor.execute(addTurn.plan(), context);

		// Turn 3: Remove the item
		ConversationTurnResult removeTurn = conversationManager
				.converse("remove Coke Zero from my basket", sessionId);
		Plan removePlan = removeTurn.plan();

		assertThat(removePlan).isNotNull();
		assertPlanReady(removePlan);
		PlanExecutionResult executed = executor.execute(removePlan, context);
		assertExecutionSuccess(executed);
		assertThat(shoppingActions.removeItemInvoked()).isTrue();
		assertThat(shoppingActions.getBasketState()).doesNotContainKey("Coke Zero");
	}

	@Test
	void basketPersistenceAcrossMultipleTurnsTest() {
		String sessionId = "persistence-session";
		ActionContext context = new ActionContext();

		// Turn 1: Start
		ConversationTurnResult turn1 = conversationManager
				.converse("start shopping", sessionId);
		executor.execute(turn1.plan(), context);

		// Turn 2: Add first item
		ConversationTurnResult turn2 = conversationManager
				.converse("add 2 bottles of Coke Zero", sessionId);
		executor.execute(turn2.plan(), context);

		// Turn 3: Add second item (different session context check)
		ConversationTurnResult turn3 = conversationManager
				.converse("add crisps and nuts for 5 people", sessionId);
		executor.execute(turn3.plan(), context);

		// Turn 4: View basket - should have BOTH items
		ConversationTurnResult turn4 = conversationManager
				.converse("what's in my basket", sessionId);
		executor.execute(turn4.plan(), context);

		Map<String, Integer> basket = shoppingActions.getBasketState();
		assertThat(basket).containsEntry("Coke Zero", 2);
		assertThat(basket).containsEntry("crisps (party)", 5);
		assertThat(basket).containsEntry("nuts (party)", 5);
	}

	@Test
	void checkoutWithConfirmationTest() {
		String sessionId = "checkout-session";
		ActionContext context = new ActionContext();

		// Turn 1: Start
		ConversationTurnResult turn1 = conversationManager
				.converse("start shopping", sessionId);
		executor.execute(turn1.plan(), context);

		// Turn 2: Add item
		ConversationTurnResult turn2 = conversationManager
				.converse("add 3 bottles of Coke Zero", sessionId);
		executor.execute(turn2.plan(), context);

		// Turn 3: Compute total
		ConversationTurnResult turn3 = conversationManager
				.converse("what's the total", sessionId);
		executor.execute(turn3.plan(), context);
		assertThat(shoppingActions.computeTotalInvoked()).isTrue();

		// Turn 4: Checkout
		ConversationTurnResult turn4 = conversationManager
				.converse("I'm ready to checkout", sessionId);
		Plan checkoutPlan = turn4.plan();

		assertThat(checkoutPlan).isNotNull();
		assertPlanReady(checkoutPlan);
		PlanExecutionResult executed = executor.execute(checkoutPlan, context);
		assertExecutionSuccess(executed);
		assertThat(shoppingActions.checkoutInvoked()).isTrue();
	}

	@Test
	void outOfStockItemTest() {
		String sessionId = "outofstock-session";
		ActionContext context = new ActionContext();

		// Turn 1: Start
		ConversationTurnResult turn1 = conversationManager
				.converse("start shopping", sessionId);
		executor.execute(turn1.plan(), context);

		// Turn 2: Try to add out-of-stock item (UnavailableProduct is not in inventory)
		ConversationTurnResult turn2 = conversationManager
				.converse("add 10 bottles of UnavailableProduct", sessionId);
		Plan plan2 = turn2.plan();

		// System should either:
		// - Reject with ERROR status, OR
		// - Ask for clarification (PENDING) with helpful message
		assertThat(plan2).isNotNull();
		assertThat(plan2.status()).isIn(PlanStatus.ERROR, PlanStatus.PENDING);
	}

	@Test
	void completeSessionWithFeedbackTest() {
		String sessionId = "feedback-session";
		ActionContext context = new ActionContext();

		// Turn 1: Start
		ConversationTurnResult turn1 = conversationManager
				.converse("help me shop for a dinner party", sessionId);
		executor.execute(turn1.plan(), context);

		// Turn 2: Add items
		ConversationTurnResult turn2 = conversationManager
				.converse("add crisps and nuts for 8 people", sessionId);
		executor.execute(turn2.plan(), context);

		// Turn 3: View basket
		ConversationTurnResult turn3 = conversationManager
				.converse("show me what I have", sessionId);
		executor.execute(turn3.plan(), context);

		// Turn 4: Checkout
		ConversationTurnResult turn4 = conversationManager
				.converse("let's checkout", sessionId);
		executor.execute(turn4.plan(), context);
		assertThat(shoppingActions.checkoutInvoked()).isTrue();

		// Turn 5: Request feedback
		ConversationTurnResult turn5 = conversationManager
				.converse("how was your experience", sessionId);
		Plan feedbackPlan = turn5.plan();

		assertThat(feedbackPlan).isNotNull();
		PlanExecutionResult executed = executor.execute(feedbackPlan, context);
		assertExecutionSuccess(executed);
		assertThat(shoppingActions.requestFeedbackInvoked()).isTrue();
	}
}
