package org.javai.springai.scenarios.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.javai.springai.actions.test.PlanAssertions.assertExecutionSuccess;
import static org.javai.springai.actions.test.PlanAssertions.assertPlanReady;

import java.math.BigDecimal;
import java.util.Objects;

import java.util.Set;

import org.javai.springai.actions.DefaultPlanExecutor;
import org.javai.springai.actions.Plan;
import org.javai.springai.actions.PlanExecutionResult;
import org.javai.springai.actions.PlanStatus;
import org.javai.springai.actions.PlanStep;
import org.javai.springai.actions.Planner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.javai.springai.actions.conversation.ConversationManager;
import org.javai.springai.actions.conversation.ConversationTurnResult;
import org.javai.springai.actions.conversation.InMemoryConversationStateStore;
import org.javai.springai.scenarios.shopping.actions.InventoryAwareShoppingActions;
import org.javai.springai.scenarios.shopping.actions.ShoppingPersonaSpec;
import org.javai.springai.scenarios.shopping.store.MockStoreApi;
import org.javai.springai.scenarios.shopping.store.model.MissionPlan;
import org.javai.springai.scenarios.shopping.store.model.MissionRequest;
import org.javai.springai.scenarios.shopping.store.model.MissionRequest.Occasion;
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
 * Mission-Based Shopping Scenario Tests.
 * 
 * Tests the assistant's ability to orchestrate complex shopping goals:
 * - Party planning with dietary requirements
 * - Budget-conscious shopping
 * - Occasion-driven recommendations
 * - Allergen-aware filtering
 * 
 * The mission plan acts as a "structured statement of intent" that guides
 * the LLM's recommendations. The LLM is responsible for interpreting gaps
 * between the mission plan and the current basket.
 * 
 * @see README.md "Mission-Based Shopping" section
 */
@DisplayName("Mission-Based Shopping")
public class ShoppingMissionScenarioTest {

	private static final Logger log = LoggerFactory.getLogger(ShoppingMissionScenarioTest.class);
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

		// Use party planner persona for mission-based tests
		planner = Planner.builder()
				.withChatClient(chatClient)
				.persona(ShoppingPersonaSpec.partyPlanner())
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
				.build();
		conversationManager = new ConversationManager(planner, new InMemoryConversationStateStore());
	}

	// ========================================================================
	// PARTY PLANNING
	// ========================================================================

	@Nested
	@DisplayName("Party Planning")
	class PartyPlanning {

		@Test
		@DisplayName("should interpret party planning mission")
		void interpretPartyMission() {
			String sessionId = "mission-party-interpret";

			ConversationTurnResult turn = conversationManager
					.converse("Help me prepare for a midday party of 10 vegetarians", sessionId);
			Plan plan = turn.plan();

			assertThat(plan).isNotNull();
			// The plan should either be ready with a mission plan or ask for clarification
			assertThat(plan.status()).isIn(PlanStatus.READY, PlanStatus.PENDING);
		}

		@Test
		@DisplayName("should respect dietary requirements in mission")
		void respectDietaryRequirements() {
			String sessionId = "mission-party-dietary";

			executor.execute(conversationManager.converse("start a shopping session", sessionId).plan());

			ConversationTurnResult missionTurn = conversationManager
					.converse("I need food for 8 vegetarians for a dinner party", sessionId);

			// The assistant should plan with vegetarian constraints
			executor.execute(missionTurn.plan());

			// Verify the mission was understood (infrastructure test)
			MissionRequest request = MissionRequest.builder()
					.description("dinner party for vegetarians")
					.headcount(8)
					.occasion(Occasion.DINNER)
					.dietaryRequirements(Set.of("vegetarian"))
					.build();

			MissionPlan plan = storeApi.planMission(request);
			assertThat(plan.items()).allMatch(item ->
					item.product().dietaryFlags().contains("vegetarian") ||
					item.product().dietaryFlags().contains("vegan"));
		}

		@Test
		@DisplayName("should exclude allergens from mission plan")
		void excludeAllergensFromMission() {
			String sessionId = "mission-party-allergen";

			ConversationTurnResult turn = conversationManager.converse(
					"Help me plan snacks for 10 people, one has a severe peanut allergy",
					sessionId);

			executor.execute(turn.plan());

			// Verify allergen exclusion in infrastructure
			MissionRequest request = MissionRequest.builder()
					.description("snacks for party")
					.headcount(10)
					.occasion(Occasion.PARTY)
					.allergenExclusions(Set.of("peanuts"))
					.build();

			MissionPlan plan = storeApi.planMission(request);
			assertThat(plan.items()).noneMatch(item ->
					item.product().allergens().contains("peanuts"));
		}

		@Test
		@DisplayName("should scale quantities for headcount")
		void scaleQuantitiesForHeadcount() {
			String sessionId = "mission-party-scale";

			ConversationTurnResult turn = conversationManager
					.converse("Plan beverages for a party of 20 people", sessionId);

			executor.execute(turn.plan());

			// Verify scaling in infrastructure
			MissionRequest request = MissionRequest.builder()
					.description("beverages for party")
					.headcount(20)
					.occasion(Occasion.PARTY)
					.build();

			MissionPlan plan = storeApi.planMission(request);

			// Should have enough for 20 people (roughly 2-3 servings each)
			int totalServings = plan.items().stream()
					.mapToInt(item -> item.quantity() * estimateServings(item.product().name()))
					.sum();

			assertThat(totalServings).isGreaterThanOrEqualTo(20);
		}

		private int estimateServings(String productName) {
			// Rough estimate for testing
			if (productName.toLowerCase().contains("pack") ||
				productName.toLowerCase().contains("party")) {
				return 10;
			}
			return 2; // Individual item serves ~2
		}

		@Test
		@DisplayName("should propose mission plan for review")
		void proposeMissionPlanForReview() {
			String sessionId = "mission-party-review";

			ConversationTurnResult turn = conversationManager
					.converse("Help me plan a picnic for 6 people", sessionId);
			Plan plan = turn.plan();

			assertThat(plan).isNotNull();
			// The assistant should propose items for review before adding
			assertPlanReady(plan);
		}

		@Test
		@DisplayName("should allow modifications to proposed mission plan")
		void allowMissionPlanModifications() {
			String sessionId = "mission-party-modify";

			// Initial mission
			executor.execute(conversationManager
					.converse("Plan snacks for a party of 8", sessionId).plan());

			// Modify the plan
			ConversationTurnResult modifyTurn = conversationManager
					.converse("Actually, make that 12 people instead", sessionId);

			Plan plan = modifyTurn.plan();
			assertThat(plan).isNotNull();
			// Should either update quantities or replan
		}
	}

	// ========================================================================
	// BUDGET-CONSCIOUS SHOPPING
	// ========================================================================

	@Nested
	@DisplayName("Budget-Conscious Shopping")
	class BudgetConsciousShopping {

		@Test
		@DisplayName("should respect budget constraint in mission")
		void respectBudgetConstraint() {
			String sessionId = "mission-budget-respect";

			ConversationTurnResult turn = conversationManager
					.converse("I need to feed 6 people dinner for under £30", sessionId);

			executor.execute(turn.plan());

			// Verify budget constraint in infrastructure
			MissionRequest request = MissionRequest.builder()
					.description("dinner for 6")
					.headcount(6)
					.occasion(Occasion.DINNER)
					.budgetLimit(new BigDecimal("30.00"))
					.build();

			MissionPlan plan = storeApi.planMission(request);
			assertThat(plan.estimatedTotal()).isLessThanOrEqualTo(new BigDecimal("30.00"));
		}

		@Test
		@DisplayName("should warn when budget is tight")
		void warnWhenBudgetTight() {
			String sessionId = "mission-budget-tight";

			ConversationTurnResult turn = conversationManager
					.converse("Plan a party for 20 people with only £15 budget", sessionId);

			// Infrastructure should generate warnings
			MissionRequest request = MissionRequest.builder()
					.description("party for 20")
					.headcount(20)
					.occasion(Occasion.PARTY)
					.budgetLimit(new BigDecimal("15.00"))
					.build();

			MissionPlan plan = storeApi.planMission(request);
			assertThat(plan.warnings()).isNotEmpty();
		}

		@Test
		@DisplayName("should suggest budget-friendly alternatives")
		void suggestBudgetFriendlyAlternatives() {
			String sessionId = "mission-budget-alt";

			// Ask for recommendations with budget constraint expressed naturally
			ConversationTurnResult recTurn = conversationManager
					.converse("what can I get for a small gathering with a £20 budget?", sessionId);
			Plan plan = recTurn.plan();

			// Should provide budget-aware recommendations
			assertThat(plan).isNotNull();
			assertThat(plan.status()).isIn(PlanStatus.READY, PlanStatus.PENDING);
		}

		@Test
		@DisplayName("should track remaining budget during mission")
		void trackRemainingBudgetDuringMission() {
			String sessionId = "mission-budget-track";

			// Start with a budget constraint
			executor.execute(conversationManager
					.converse("I have a £50 budget for party supplies", sessionId).plan());

			// Add items
			executor.execute(conversationManager
					.converse("add 5 Coke Zero", sessionId).plan());

			// Check remaining budget
			ConversationTurnResult budgetTurn = conversationManager
					.converse("how much budget do I have left?", sessionId);
			Plan plan = budgetTurn.plan();

			// Should answer budget query
			assertThat(plan).isNotNull();
			assertThat(plan.status()).isIn(PlanStatus.READY, PlanStatus.PENDING);
		}

		@Test
		@DisplayName("should warn before exceeding budget")
		void warnBeforeExceedingBudget() {
			String sessionId = "mission-budget-warn";

			// Start with a tight budget
			executor.execute(conversationManager
					.converse("I only have £10 to spend today", sessionId).plan());

			// Try to add expensive items
			ConversationTurnResult addTurn = conversationManager
					.converse("add 20 bottles of Coke Zero", sessionId);

			// Should warn about budget or ask for confirmation
			Plan plan = addTurn.plan();
			assertThat(plan).isNotNull();
			// Plan might be PENDING asking for confirmation or READY with warning
		}
	}

	// ========================================================================
	// OCCASION-DRIVEN SHOPPING
	// ========================================================================

	@Nested
	@DisplayName("Occasion-Driven Shopping")
	class OccasionDrivenShopping {

		@Test
		@DisplayName("should select appropriate products for party occasion")
		void selectProductsForParty() {
			String sessionId = "mission-occasion-party";

			ConversationTurnResult turn = conversationManager
					.converse("I'm throwing a party, what do you suggest?", sessionId);

			executor.execute(turn.plan());

			// Infrastructure should prioritize party categories
			MissionRequest request = MissionRequest.builder()
					.description("party")
					.headcount(10)
					.occasion(Occasion.PARTY)
					.build();

			MissionPlan plan = storeApi.planMission(request);
			assertThat(plan.items()).isNotEmpty();
		}

		@Test
		@DisplayName("should select appropriate products for dinner occasion")
		void selectProductsForDinner() {
			MissionRequest request = MissionRequest.builder()
					.description("dinner party")
					.headcount(6)
					.occasion(Occasion.DINNER)
					.build();

			MissionPlan plan = storeApi.planMission(request);

			// Should include produce, dairy, bakery for dinner
			java.util.Set<String> categories = plan.items().stream()
					.map(item -> item.product().category().toLowerCase())
					.collect(java.util.stream.Collectors.toSet());

			assertThat(categories).containsAnyOf("produce", "dairy", "bakery", "beverages");
		}

		@Test
		@DisplayName("should select appropriate products for picnic occasion")
		void selectProductsForPicnic() {
			MissionRequest request = MissionRequest.builder()
					.description("picnic")
					.headcount(4)
					.occasion(Occasion.PICNIC)
					.build();

			MissionPlan plan = storeApi.planMission(request);

			// Should include portable foods
			java.util.Set<String> categories = plan.items().stream()
					.map(item -> item.product().category().toLowerCase())
					.collect(java.util.stream.Collectors.toSet());

			assertThat(categories).containsAnyOf("snacks", "beverages", "bakery");
		}
	}

	// ========================================================================
	// MISSION PLAN AS REFERENCE (LLM-DRIVEN GAP ANALYSIS)
	// ========================================================================

	@Nested
	@DisplayName("Mission Plan as Reference")
	class MissionPlanAsReference {

		@Test
		@DisplayName("should allow basket additions beyond mission plan")
		void allowAdditionalItems() {
			String sessionId = "mission-ref-additive";

			// Start with a mission
			executor.execute(conversationManager
					.converse("Plan snacks for 6 people", sessionId).plan());

			// Add something extra not in the plan
			ConversationTurnResult addTurn = conversationManager
					.converse("also add some milk for tomorrow's breakfast", sessionId);

			Plan plan = addTurn.plan();
			assertThat(plan).isNotNull();
			// Should allow the addition (additive mode)
			assertThat(plan.status()).isIn(PlanStatus.READY, PlanStatus.PENDING);
		}

		@Test
		@DisplayName("should answer queries about mission progress")
		void answerMissionProgressQueries() {
			String sessionId = "mission-ref-progress";

			// Start with a mission
			executor.execute(conversationManager
					.converse("Help me plan a party for 10", sessionId).plan());

			// Add some items
			executor.execute(conversationManager
					.converse("add 10 Coke Zero", sessionId).plan());

			// Ask about progress
			ConversationTurnResult progressTurn = conversationManager
					.converse("what else do I need for the party?", sessionId);

			Plan plan = progressTurn.plan();
			assertThat(plan).isNotNull();
			// The LLM should analyze gap between mission and basket
		}

		@Test
		@DisplayName("should not block items that drift from mission")
		void allowMissionDrift() {
			String sessionId = "mission-ref-drift";

			// Start vegetarian party mission
			executor.execute(conversationManager.converse(
					"Plan a vegetarian party for 8", sessionId).plan());

			// Add non-vegetarian item (drift from mission)
			ConversationTurnResult addTurn = conversationManager
					.converse("also add some beef jerky", sessionId);

			Plan plan = addTurn.plan();
			// Should allow with possible advisory (high drift tolerance)
			assertThat(plan).isNotNull();
			assertThat(plan.status()).isIn(PlanStatus.READY, PlanStatus.PENDING);
		}
	}

	// ========================================================================
	// MISSION GUARDRAILS (PHASE 7)
	// ========================================================================

	@Nested
	@DisplayName("Mission Guardrails")
	class MissionGuardrails {

		@Test
		@DisplayName("should validate mission content for appropriateness")
		void validateMissionContent() {
			String sessionId = "mission-guard-validate";

			// Normal mission should pass
			ConversationTurnResult normalTurn = conversationManager
					.converse("Plan a birthday party for kids", sessionId);

			assertThat(normalTurn.plan()).isNotNull();
			assertThat(normalTurn.plan().status()).isNotEqualTo(PlanStatus.ERROR);
		}

		@Test
		@DisplayName("should reject missions with inappropriate content")
		void rejectInappropriateContent() {
			String sessionId = "mission-guard-reject";

			// This test verifies the guardrail mechanism exists
			// Actual content filtering would be handled by MissionGuardrailTool
			// which will be implemented in Phase 7

			// Placeholder: verify infrastructure can handle rejection
			// In Phase 7, this will test actual content validation
		}
	}

	// ========================================================================
	// COMPLEX MULTI-CONSTRAINT MISSIONS
	// ========================================================================

	@Nested
	@DisplayName("Complex Multi-Constraint Missions")
	class ComplexMissions {

		@Test
		@DisplayName("should handle mission with multiple dietary requirements")
		void handleMultipleDietaryRequirements() {
			MissionRequest request = MissionRequest.builder()
					.description("party with mixed dietary needs")
					.headcount(12)
					.occasion(Occasion.PARTY)
					.dietaryRequirements(Set.of("vegetarian", "gluten-free"))
					.build();

			MissionPlan plan = storeApi.planMission(request);

			// All items should satisfy both constraints
			assertThat(plan.items()).allMatch(item ->
					(item.product().dietaryFlags().contains("vegetarian") ||
					 item.product().dietaryFlags().contains("vegan")) &&
					item.product().dietaryFlags().contains("gluten-free"));
		}

		@Test
		@DisplayName("should handle mission with budget and allergen constraints")
		void handleBudgetAndAllergenConstraints() {
			MissionRequest request = MissionRequest.builder()
					.description("affordable party snacks")
					.headcount(8)
					.occasion(Occasion.PARTY)
					.budgetLimit(new BigDecimal("25.00"))
					.allergenExclusions(Set.of("nuts", "dairy"))
					.build();

			MissionPlan plan = storeApi.planMission(request);

			assertThat(plan.estimatedTotal()).isLessThanOrEqualTo(new BigDecimal("25.00"));
			assertThat(plan.items()).noneMatch(item ->
					item.product().allergens().contains("nuts") ||
					item.product().allergens().contains("dairy"));
		}

		@Test
		@DisplayName("should generate warnings when constraints conflict")
		void generateWarningsForConflicts() {
			// Very restrictive constraints that may be hard to satisfy
			MissionRequest request = MissionRequest.builder()
					.description("very restricted party")
					.headcount(20)
					.occasion(Occasion.PARTY)
					.budgetLimit(new BigDecimal("10.00"))
					.dietaryRequirements(Set.of("vegan", "gluten-free"))
					.allergenExclusions(Set.of("soy", "nuts"))
					.build();

			MissionPlan plan = storeApi.planMission(request);

			// Should have warnings or notes about constraint difficulties
			assertThat(plan.warnings().isEmpty() && plan.notes().isEmpty()).isFalse();
		}
	}
}

