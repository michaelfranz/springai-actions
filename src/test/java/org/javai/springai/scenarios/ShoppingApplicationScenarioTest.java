package org.javai.springai.scenarios;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.api.ActionParam;
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
import org.springframework.ai.tool.annotation.Tool;

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

	public static class ShoppingActions {
		private final AtomicBoolean startSessionInvoked = new AtomicBoolean(false);
		private final AtomicBoolean presentOffersInvoked = new AtomicBoolean(false);
		private final AtomicBoolean addItemInvoked = new AtomicBoolean(false);
		private final AtomicBoolean addPartySnacksInvoked = new AtomicBoolean(false);
		private final AtomicBoolean computeTotalInvoked = new AtomicBoolean(false);
		private final AtomicBoolean checkoutInvoked = new AtomicBoolean(false);
		private final AtomicBoolean requestFeedbackInvoked = new AtomicBoolean(false);
		private AddItemRequest lastAddItem;
		private final Map<String, Integer> basket = new HashMap<>();

		@Action(description = """
				Start or reset a shopping session and basket.""")
		public void startSession(ActionContext context) {
			startSessionInvoked.set(true);
			basket.clear();
			context.put("basket", basket);
		}

		@Action(description = "Present current special offers to the shopper.")
		public void presentOffers() {
			presentOffersInvoked.set(true);
		}

		@Action(description = """
				Add a product and quantity to the current basket.""")
		public void addItem(
				@ActionParam(description = "Product name") String product,
				@ActionParam(description = "Quantity", allowedRegex = "[0-9]+") int quantity) {
			addItemInvoked.set(true);
			lastAddItem = new AddItemRequest(product, quantity);
			basket.merge(product, quantity, (existing, add) -> Integer.valueOf(existing + add));
		}

		@Action(description = """
				Add snacks for a party of a given size (e.g., crisps and nuts).""")
		public void addPartySnacks(
				@ActionParam(description = "Party size", allowedRegex = "[0-9]+") int partySize) {
			addPartySnacksInvoked.set(true);
			basket.merge("crisps (party)", partySize, (existing, add) -> Integer.valueOf(existing + add));
			basket.merge("nuts (party)", partySize, (existing, add) -> Integer.valueOf(existing + add));
		}

		@Action(description = """
				Compute or retrieve the basket total.""")
		public void computeTotal() {
			computeTotalInvoked.set(true);
		}

		@Action(description = """
				Checkout the basket and end the shopping session.""")
		public void checkoutBasket() {
			checkoutInvoked.set(true);
		}

		@Action(description = "Request end-of-session feedback from the shopper.")
		public void requestFeedback() {
			requestFeedbackInvoked.set(true);
		}

		boolean startSessionInvoked() {
			return startSessionInvoked.get();
		}

		boolean presentOffersInvoked() {
			return presentOffersInvoked.get();
		}

		boolean addItemInvoked() {
			return addItemInvoked.get();
		}

		boolean addPartySnacksInvoked() {
			return addPartySnacksInvoked.get();
		}

		boolean computeTotalInvoked() {
			return computeTotalInvoked.get();
		}

		boolean checkoutInvoked() {
			return checkoutInvoked.get();
		}

		boolean requestFeedbackInvoked() {
			return requestFeedbackInvoked.get();
		}

		AddItemRequest lastAddItem() {
			return lastAddItem;
		}

		public record AddItemRequest(String product, int quantity) {
		}
	}

	public static class SpecialOfferTool {
		private final AtomicBoolean listInvoked = new AtomicBoolean(false);

		@Tool(name = "listSpecialOffers", description = "List current special offers and discounts.")
		public String listSpecialOffers() {
			listInvoked.set(true);
			return """
					Today's offers:
					- 10% off Coca Cola (regular)
					- 10% off Coke Zero
					- 5% off mixed nuts (party size)
					""";
		}

		boolean listInvoked() {
			return listInvoked.get();
		}
	}
}
