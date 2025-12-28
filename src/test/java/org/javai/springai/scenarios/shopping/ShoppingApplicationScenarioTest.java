package org.javai.springai.scenarios.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import java.util.Objects;
import org.javai.springai.dsl.conversation.ConversationManager;
import org.javai.springai.dsl.conversation.ConversationTurnResult;
import org.javai.springai.dsl.conversation.InMemoryConversationStateStore;
import org.javai.springai.dsl.exec.DefaultPlanExecutor;
import org.javai.springai.dsl.exec.DefaultPlanResolver;
import org.javai.springai.dsl.exec.PlanExecutionResult;
import org.javai.springai.dsl.exec.PlanResolver;
import org.javai.springai.dsl.exec.ResolvedPlan;
import org.javai.springai.dsl.exec.ResolvedStep;
import org.javai.springai.dsl.plan.PlanStatus;
import org.javai.springai.dsl.plan.PlanStep;
import org.javai.springai.dsl.plan.Planner;
import org.javai.springai.dsl.prompt.PersonaSpec;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

public class ShoppingApplicationScenarioTest {

	private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");
	private static final boolean RUN_LLM_TESTS = "true".equalsIgnoreCase(System.getenv("RUN_LLM_TESTS"));

	Planner planner;
	PlanResolver resolver;
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
		resolver = new DefaultPlanResolver();
		executor = new DefaultPlanExecutor();
		conversationManager = new ConversationManager(planner, resolver, new InMemoryConversationStateStore());
	}

	@Test
	void startSessionAndOffersTest() {
		String request = "I want to start a new shopping basket";
		ConversationTurnResult turn = conversationManager.converse(request, "display-session");
		ResolvedPlan resolvedPlan = turn.resolvedPlan();

		assertThat(resolvedPlan).isNotNull();
		assertThat(resolvedPlan.status()).isEqualTo(PlanStatus.READY);
		assertThat(resolvedPlan.steps()).hasSize(1);
		ResolvedStep step = resolvedPlan.steps().getFirst();
		assertThat(step).isInstanceOf(ResolvedStep.ActionStep.class);

		PlanExecutionResult executed = executor.execute(resolvedPlan);
		assertThat(executed.success()).isTrue();
		assertThat(shoppingActions.startSessionInvoked()).isTrue();
		assertThat(shoppingActions.presentOffersInvoked()).isTrue();
		assertThat(specialOfferTool.listInvoked()).isTrue();
	}

	@Test
	void addCokeZeroTest() {
		String request = "add 6 bottles of Coke Zero to my basket";
		ConversationTurnResult turn = conversationManager.converse(request, "export-session");
		ResolvedPlan resolvedPlan = turn.resolvedPlan();
		assertThat(resolvedPlan).isNotNull();
		assertThat(resolvedPlan.status()).isEqualTo(PlanStatus.READY);
		assertThat(resolvedPlan.steps()).hasSize(1);
		ResolvedStep step = resolvedPlan.steps().getFirst();
		assertThat(step).isInstanceOf(ResolvedStep.ActionStep.class);

		PlanExecutionResult executed = executor.execute(resolvedPlan);
		assertThat(executed.success()).isTrue();
		assertThat(shoppingActions.addItemInvoked()).isTrue();
		assertThat(shoppingActions.lastAddItem()).isNotNull();
		assertThat(shoppingActions.lastAddItem().product()).containsIgnoringCase("coke zero");
		assertThat(shoppingActions.lastAddItem().quantity()).isEqualTo(6);
	}

	@Test
	void addSnacksForTenTest() {
		String request = "add crisps and nuts for around 10 people";
		ConversationTurnResult turn = conversationManager.converse(request, "snacks-session");
		ResolvedPlan resolvedPlan = turn.resolvedPlan();
		assertThat(resolvedPlan).isNotNull();
		assertThat(resolvedPlan.status()).isEqualTo(PlanStatus.READY);
		assertThat(resolvedPlan.steps()).hasSize(1);

		PlanExecutionResult executed = executor.execute(resolvedPlan);
		assertThat(executed.success()).isTrue();
		assertThat(shoppingActions.addPartySnacksInvoked()).isTrue();
	}

	@Test
	void unableToIdentifyActionTest() {
		// No action supports arbitrary vehicle maintenance
		String request = "change the oil in my car";
		ConversationTurnResult turn = conversationManager.converse(request, "anova-session");
		ResolvedPlan resolvedPlan = turn.resolvedPlan();
		assertThat(resolvedPlan).isNotNull();
		assertThat(resolvedPlan.status()).isEqualTo(PlanStatus.ERROR);
		assertThat(resolvedPlan.steps()).hasSize(1);
		ResolvedStep step = resolvedPlan.steps().getFirst();
		assertThat(step).isInstanceOf(ResolvedStep.ErrorStep.class);
	}

	@Test
	void requireMoreInformationTest() {
		// Add item requires quantity
		String request = "add coke zero";
		ConversationTurnResult turn = conversationManager.converse(request, "pending-session");
		ResolvedPlan resolvedPlan = turn.resolvedPlan();
		assertThat(resolvedPlan).isNotNull();
		assertThat(resolvedPlan.status()).isEqualTo(PlanStatus.PENDING);
		assertThat(turn.pendingParams()).isNotEmpty();
		assertThat(turn.pendingParams().stream().map(PlanStep.PendingParam::name))
				.anyMatch(name -> name.equals("quantity") || name.equals("product"));
	}

	@Test
	void requireMoreInformationFollowUpProvidesMissingBundleId() {
		String sessionId = "shopping-session";

		// Turn 1: missing quantity -> expect pending
		ConversationTurnResult firstTurn = conversationManager
				.converse("add coke zero", sessionId);
		ResolvedPlan firstResolved = firstTurn.resolvedPlan();
		assertThat(firstResolved).isNotNull();
		assertThat(firstResolved.status()).isEqualTo(PlanStatus.PENDING);
		assertThat(firstTurn.pendingParams()).isNotEmpty();

		// Turn 2: user supplies only the missing info; desired behavior is that
		// the system merges context and produces an executable step (documented scenario)
		ConversationTurnResult secondTurn = conversationManager
				.converse("add 4 bottles", sessionId);
		ResolvedPlan secondPlan = secondTurn.resolvedPlan();
		assertThat(secondPlan).isNotNull();
		// Ideal outcome after context merge: actionable step, no pending
		assertThat(secondPlan.steps().getFirst()).isInstanceOf(ResolvedStep.ActionStep.class);

		PlanExecutionResult executed = executor.execute(secondPlan);
		assertThat(executed.success()).isTrue();
		assertThat(shoppingActions.addItemInvoked()).isTrue();
	}

	@Test
	void viewBasketSummaryTest() {
		String sessionId = "basket-view-session";

		// Turn 1: Start session
		ConversationTurnResult startTurn = conversationManager
				.converse("I want to start shopping", sessionId);
		executor.execute(startTurn.resolvedPlan());

		// Turn 2: Add an item
		ConversationTurnResult addTurn = conversationManager
				.converse("add 3 bottles of Coke Zero", sessionId);
		executor.execute(addTurn.resolvedPlan());

		// Turn 3: View basket
		ConversationTurnResult viewTurn = conversationManager
				.converse("show me my basket", sessionId);
		ResolvedPlan viewPlan = viewTurn.resolvedPlan();

		assertThat(viewPlan).isNotNull();
		assertThat(viewPlan.status()).isEqualTo(PlanStatus.READY);
		assertThat(viewPlan.steps()).hasSize(1);
		assertThat(viewPlan.steps().getFirst()).isInstanceOf(ResolvedStep.ActionStep.class);

		PlanExecutionResult executed = executor.execute(viewPlan);
		assertThat(executed.success()).isTrue();
		assertThat(shoppingActions.viewBasketInvoked()).isTrue();
		assertThat(shoppingActions.getBasketState()).containsEntry("Coke Zero", 3);
	}

	@Test
	void removeItemFromBasketTest() {
		String sessionId = "remove-item-session";

		// Turn 1: Start session
		ConversationTurnResult startTurn = conversationManager
				.converse("start shopping", sessionId);
		executor.execute(startTurn.resolvedPlan());

		// Turn 2: Add item
		ConversationTurnResult addTurn = conversationManager
				.converse("add 2 bottles of Coke Zero", sessionId);
		executor.execute(addTurn.resolvedPlan());

		// Turn 3: Remove the item
		ConversationTurnResult removeTurn = conversationManager
				.converse("remove Coke Zero from my basket", sessionId);
		ResolvedPlan removePlan = removeTurn.resolvedPlan();

		assertThat(removePlan).isNotNull();
		assertThat(removePlan.status()).isEqualTo(PlanStatus.READY);
		PlanExecutionResult executed = executor.execute(removePlan);
		assertThat(executed.success()).isTrue();
		assertThat(shoppingActions.removeItemInvoked()).isTrue();
		assertThat(shoppingActions.getBasketState()).doesNotContainKey("Coke Zero");
	}

	@Test
	void basketPersistenceAcrossMultipleTurnsTest() {
		String sessionId = "persistence-session";

		// Turn 1: Start
		ConversationTurnResult turn1 = conversationManager
				.converse("start shopping", sessionId);
		executor.execute(turn1.resolvedPlan());

		// Turn 2: Add first item
		ConversationTurnResult turn2 = conversationManager
				.converse("add 2 bottles of Coke Zero", sessionId);
		executor.execute(turn2.resolvedPlan());

		// Turn 3: Add second item (different session context check)
		ConversationTurnResult turn3 = conversationManager
				.converse("add crisps and nuts for 5 people", sessionId);
		executor.execute(turn3.resolvedPlan());

		// Turn 4: View basket - should have BOTH items
		ConversationTurnResult turn4 = conversationManager
				.converse("what's in my basket", sessionId);
		executor.execute(turn4.resolvedPlan());

		Map<String, Integer> basket = shoppingActions.getBasketState();
		assertThat(basket).containsEntry("Coke Zero", 2);
		assertThat(basket).containsEntry("crisps (party)", 5);
		assertThat(basket).containsEntry("nuts (party)", 5);
	}

	@Test
	void checkoutWithConfirmationTest() {
		String sessionId = "checkout-session";

		// Turn 1: Start
		ConversationTurnResult turn1 = conversationManager
				.converse("start shopping", sessionId);
		executor.execute(turn1.resolvedPlan());

		// Turn 2: Add item
		ConversationTurnResult turn2 = conversationManager
				.converse("add 3 bottles of Coke Zero", sessionId);
		executor.execute(turn2.resolvedPlan());

		// Turn 3: Compute total
		ConversationTurnResult turn3 = conversationManager
				.converse("what's the total", sessionId);
		executor.execute(turn3.resolvedPlan());
		assertThat(shoppingActions.computeTotalInvoked()).isTrue();

		// Turn 4: Checkout
		ConversationTurnResult turn4 = conversationManager
				.converse("I'm ready to checkout", sessionId);
		ResolvedPlan checkoutPlan = turn4.resolvedPlan();

		assertThat(checkoutPlan).isNotNull();
		assertThat(checkoutPlan.status()).isEqualTo(PlanStatus.READY);
		PlanExecutionResult executed = executor.execute(checkoutPlan);
		assertThat(executed.success()).isTrue();
		assertThat(shoppingActions.checkoutInvoked()).isTrue();
	}

	@Test
	void outOfStockItemTest() {
		String sessionId = "outofstock-session";

		// Turn 1: Start
		ConversationTurnResult turn1 = conversationManager
				.converse("start shopping", sessionId);
		executor.execute(turn1.resolvedPlan());

		// Turn 2: Try to add out-of-stock item (UnavailableProduct is not in inventory)
		ConversationTurnResult turn2 = conversationManager
				.converse("add 10 bottles of UnavailableProduct", sessionId);
		ResolvedPlan plan2 = turn2.resolvedPlan();

		// System should either:
		// - Reject with ERROR status, OR
		// - Ask for clarification (PENDING) with helpful message
		assertThat(plan2).isNotNull();
		assertThat(plan2.status()).isIn(PlanStatus.ERROR, PlanStatus.PENDING);
	}

	@Test
	void completeSessionWithFeedbackTest() {
		String sessionId = "feedback-session";

		// Turn 1: Start
		ConversationTurnResult turn1 = conversationManager
				.converse("help me shop for a dinner party", sessionId);
		executor.execute(turn1.resolvedPlan());

		// Turn 2: Add items
		ConversationTurnResult turn2 = conversationManager
				.converse("add crisps and nuts for 8 people", sessionId);
		executor.execute(turn2.resolvedPlan());

		// Turn 3: View basket
		ConversationTurnResult turn3 = conversationManager
				.converse("show me what I have", sessionId);
		executor.execute(turn3.resolvedPlan());

		// Turn 4: Checkout
		ConversationTurnResult turn4 = conversationManager
				.converse("let's checkout", sessionId);
		executor.execute(turn4.resolvedPlan());
		assertThat(shoppingActions.checkoutInvoked()).isTrue();

		// Turn 5: Request feedback
		ConversationTurnResult turn5 = conversationManager
				.converse("how was your experience", sessionId);
		ResolvedPlan feedbackPlan = turn5.resolvedPlan();

		assertThat(feedbackPlan).isNotNull();
		PlanExecutionResult executed = executor.execute(feedbackPlan);
		assertThat(executed.success()).isTrue();
		assertThat(shoppingActions.requestFeedbackInvoked()).isTrue();
	}
}

