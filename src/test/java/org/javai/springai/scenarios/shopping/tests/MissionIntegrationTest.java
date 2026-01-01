package org.javai.springai.scenarios.shopping.tests;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.javai.springai.scenarios.shopping.ShoppingPromptContributor;
import org.javai.springai.scenarios.shopping.actions.ActionResult;
import org.javai.springai.scenarios.shopping.actions.InventoryAwareShoppingActions;
import org.javai.springai.scenarios.shopping.store.MockStoreApi;
import org.javai.springai.scenarios.shopping.store.model.MissionPlan;
import org.javai.springai.scenarios.shopping.store.model.MissionRequest;
import org.javai.springai.scenarios.shopping.store.model.ShoppingSession;
import org.javai.springai.scenarios.shopping.tools.GuardrailTool;
import org.javai.springai.scenarios.shopping.tools.MissionTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for mission-based shopping (Phase 7).
 * 
 * Tests:
 * - Mission creation via actions
 * - Mission planning via MissionTool
 * - Mission plan stored in session
 * - Guardrail content moderation
 * - Dynamic prompt contribution
 * - Plan-to-basket comparison
 */
@DisplayName("Mission Integration Tests")
class MissionIntegrationTest {

	private MockStoreApi storeApi;
	private InventoryAwareShoppingActions actions;
	private MissionTool missionTool;
	private GuardrailTool guardrailTool;
	private ShoppingPromptContributor promptContributor;

	@BeforeEach
	void setUp() {
		storeApi = new MockStoreApi();
		actions = new InventoryAwareShoppingActions(storeApi);
		missionTool = new MissionTool(storeApi);
		guardrailTool = new GuardrailTool();
		promptContributor = new ShoppingPromptContributor(storeApi);
	}

	@Nested
	@DisplayName("Starting Missions via Actions")
	class StartMissionTests {

		@Test
		@DisplayName("should start a simple party mission")
		void startSimplePartyMission() {
			actions.startSession(null);

			ActionResult result = actions.startMission(
					null,
					"Snacks for a casual gathering",
					6,
					"PARTY",
					null, // no dietary requirements
					null, // no allergen exclusions
					null  // no budget
			);

			assertThat(result.success()).isTrue();
			assertThat(result.message()).contains("Mission Plan");
			assertThat(result.message()).contains("6 people");
			assertThat(actions.startMissionInvoked()).isTrue();
		}

		@Test
		@DisplayName("should start mission with dietary requirements")
		void startMissionWithDietary() {
			actions.startSession(null);

			ActionResult result = actions.startMission(
					null,
					"Vegetarian dinner party",
					8,
					"DINNER",
					"vegetarian",
					null,
					null
			);

			assertThat(result.success()).isTrue();
			assertThat(result.message()).contains("vegetarian");

			// Verify mission is stored in session
			ShoppingSession session = actions.getCurrentSession();
			assertThat(session.activeMission()).isPresent();

			MissionPlan plan = session.activeMission().get();
			assertThat(plan.request().dietaryRequirements()).contains("vegetarian");
		}

		@Test
		@DisplayName("should start mission with allergen exclusions")
		void startMissionWithAllergens() {
			actions.startSession(null);

			ActionResult result = actions.startMission(
					null,
					"Party with peanut-allergic guests",
					10,
					"PARTY",
					null,
					"peanuts",
					null
			);

			assertThat(result.success()).isTrue();

			ShoppingSession session = actions.getCurrentSession();
			MissionPlan plan = session.activeMission().get();
			assertThat(plan.request().allergenExclusions()).contains("peanuts");

			// Verify no items contain peanuts
			for (var item : plan.items()) {
				assertThat(item.product().allergens())
						.as("Product %s should not contain peanuts", item.product().name())
						.doesNotContain("peanuts");
			}
		}

		@Test
		@DisplayName("should start mission with budget constraint")
		void startMissionWithBudget() {
			actions.startSession(null);

			ActionResult result = actions.startMission(
					null,
					"Budget party",
					5,
					"PARTY",
					null,
					null,
					new BigDecimal("25.00")
			);

			assertThat(result.success()).isTrue();

			ShoppingSession session = actions.getCurrentSession();
			MissionPlan plan = session.activeMission().get();
			assertThat(plan.estimatedTotal()).isLessThanOrEqualTo(new BigDecimal("25.00"));
		}

		@Test
		@DisplayName("should reject invalid mission parameters")
		void rejectInvalidMission() {
			actions.startSession(null);

			// Missing description
			ActionResult result1 = actions.startMission(null, "", 5, "PARTY", null, null, null);
			assertThat(result1.success()).isFalse();

			// Invalid headcount
			ActionResult result2 = actions.startMission(null, "Test", 0, "PARTY", null, null, null);
			assertThat(result2.success()).isFalse();
		}
	}

	@Nested
	@DisplayName("Mission Planning via Tool")
	class MissionToolTests {

		@Test
		@DisplayName("should plan mission via tool")
		void planMissionViaTool() {
			String result = missionTool.planMission(
					"Office meeting snacks",
					12,
					"MEETING",
					null,
					null,
					new BigDecimal("40.00")
			);

			assertThat(result).contains("Mission Plan");
			assertThat(result).contains("12 people");
			assertThat(missionTool.planMissionInvoked()).isTrue();
		}

		@Test
		@DisplayName("should compare plan to basket")
		void comparePlanToBasket() {
			// Create a plan
			MissionRequest request = MissionRequest.builder()
					.description("Test mission")
					.headcount(4)
					.occasion(MissionRequest.Occasion.SNACKS)
					.build();
			MissionPlan plan = storeApi.planMission(request);

			// Create a basket with some plan items
			Map<String, Integer> basket = new HashMap<>();
			if (!plan.items().isEmpty()) {
				String firstSku = plan.items().get(0).product().sku();
				basket.put(firstSku, plan.items().get(0).quantity());
			}

			String result = missionTool.comparePlanToBasket(plan, basket);

			assertThat(result).contains("Mission Progress");
			assertThat(missionTool.comparePlanToBasketInvoked()).isTrue();
		}

		@Test
		@DisplayName("should report full gap when basket is empty")
		void fullGapWhenBasketEmpty() {
			MissionRequest request = MissionRequest.builder()
					.description("Test mission")
					.headcount(4)
					.occasion(MissionRequest.Occasion.PARTY)
					.build();
			MissionPlan plan = storeApi.planMission(request);

			String result = missionTool.comparePlanToBasket(plan, new HashMap<>());

			assertThat(result).contains("0%");
			assertThat(result).contains("empty");
		}

		@Test
		@DisplayName("should adjust mission quantities")
		void adjustMissionQuantities() {
			MissionRequest request = MissionRequest.builder()
					.description("Test mission")
					.headcount(4)
					.occasion(MissionRequest.Occasion.PARTY)
					.build();
			MissionPlan plan = storeApi.planMission(request);

			// Adjust quantities
			Map<String, Integer> adjustments = new HashMap<>();
			if (!plan.items().isEmpty()) {
				String productName = plan.items().get(0).product().name();
				adjustments.put(productName, 10);
			}

			String result = missionTool.adjustMissionQuantities(plan, adjustments);

			assertThat(result).contains("Mission Plan");
			assertThat(missionTool.refineMissionInvoked()).isTrue();
		}

		@Test
		@DisplayName("should suggest mission additions")
		void suggestMissionAdditions() {
			MissionRequest request = MissionRequest.builder()
					.description("Test mission")
					.headcount(4)
					.occasion(MissionRequest.Occasion.PARTY)
					.build();
			MissionPlan plan = storeApi.planMission(request);

			String result = missionTool.suggestMissionAdditions(plan, 3);

			assertThat(result).isNotEmpty();
		}
	}

	@Nested
	@DisplayName("Reviewing Mission Plan")
	class ReviewMissionTests {

		@Test
		@DisplayName("should review active mission plan")
		void reviewActiveMission() {
			actions.startSession(null);
			actions.startMission(null, "Test party", 6, "PARTY", null, null, null);

			ActionResult result = actions.reviewMissionPlan(null);

			assertThat(result.success()).isTrue();
			assertThat(result.message()).contains("Mission Plan");
			assertThat(actions.reviewMissionPlanInvoked()).isTrue();
		}

		@Test
		@DisplayName("should fail when no active mission")
		void failWhenNoMission() {
			actions.startSession(null);

			ActionResult result = actions.reviewMissionPlan(null);

			assertThat(result.success()).isFalse();
			assertThat(result.message()).containsIgnoringCase("no active mission");
		}
	}

	@Nested
	@DisplayName("Guardrail Content Moderation")
	class GuardrailTests {

		@Test
		@DisplayName("should pass appropriate content")
		void passAppropriateContent() {
			var result = guardrailTool.evaluateContent("Planning a birthday party for my kids");

			assertThat(result.isAppropriate()).isTrue();
			assertThat(result.hasWarning()).isFalse();
			assertThat(guardrailTool.evaluateContentInvoked()).isTrue();
		}

		@Test
		@DisplayName("should reject blacklisted terms")
		void rejectBlacklistedTerms() {
			var result = guardrailTool.evaluateContent("I want to plan something with nazi themes");

			assertThat(result.isAppropriate()).isFalse();
			assertThat(result.reason()).containsIgnoringCase("inappropriate");
			assertThat(result.suggestion()).isNotEmpty();
		}

		@Test
		@DisplayName("should warn about excessive caps")
		void warnAboutExcessiveCaps() {
			var result = guardrailTool.evaluateContent("I WANT TO PLAN A PARTY RIGHT NOW!!!");

			assertThat(result.isAppropriate()).isTrue(); // Still appropriate, just a warning
			assertThat(result.hasWarning()).isTrue();
		}

		@Test
		@DisplayName("should evaluate mission request")
		void evaluateMissionRequest() {
			var result = guardrailTool.evaluateMissionRequest(
					"Party for my friends",
					10,
					"PARTY"
			);

			assertThat(result.isAppropriate()).isTrue();
			assertThat(guardrailTool.evaluateMissionInvoked()).isTrue();
		}

		@Test
		@DisplayName("should reject invalid headcount")
		void rejectInvalidHeadcount() {
			var result = guardrailTool.evaluateMissionRequest(
					"Test party",
					0,
					"PARTY"
			);

			assertThat(result.isAppropriate()).isFalse();
			assertThat(result.reason()).containsIgnoringCase("headcount");
		}

		@Test
		@DisplayName("should warn about very large parties")
		void warnAboutLargeParties() {
			var result = guardrailTool.evaluateMissionRequest(
					"Corporate event",
					1500,
					"PARTY"
			);

			assertThat(result.isAppropriate()).isTrue();
			assertThat(result.hasWarning()).isTrue();
			assertThat(result.reason()).containsIgnoringCase("large");
		}

		@Test
		@DisplayName("should reset invocation flags")
		void resetInvocationFlags() {
			guardrailTool.evaluateContent("test");
			assertThat(guardrailTool.evaluateContentInvoked()).isTrue();

			guardrailTool.reset();
			assertThat(guardrailTool.evaluateContentInvoked()).isFalse();
		}
	}

	@Nested
	@DisplayName("Dynamic Prompt Contribution")
	class PromptContributorTests {

		@Test
		@DisplayName("should generate empty prompt when no context")
		void emptyPromptWhenNoContext() {
			String prompt = promptContributor.buildPromptContribution(null, null);

			assertThat(prompt).isEmpty();
		}

		@Test
		@DisplayName("should include budget section when budget set")
		void includeBudgetSection() {
			ShoppingSession session = ShoppingSession.create("test", null)
					.withBudget(new BigDecimal("50.00"));

			String prompt = promptContributor.buildPromptContribution(session, new HashMap<>());

			assertThat(prompt).contains("BUDGET STATUS");
			assertThat(prompt).contains("£50.00");
		}

		@Test
		@DisplayName("should include mission section when mission active")
		void includeMissionSection() {
			MissionRequest request = MissionRequest.builder()
					.description("Vegetarian party")
					.headcount(10)
					.occasion(MissionRequest.Occasion.PARTY)
					.dietaryRequirements(Set.of("vegetarian"))
					.allergenExclusions(Set.of("peanuts"))
					.build();
			MissionPlan plan = storeApi.planMission(request);

			ShoppingSession session = ShoppingSession.create("test", null)
					.withMission(plan);

			String prompt = promptContributor.buildPromptContribution(session, new HashMap<>());

			assertThat(prompt).contains("CURRENT MISSION");
			assertThat(prompt).contains("Vegetarian party");
			assertThat(prompt).contains("vegetarian");
			assertThat(prompt).contains("peanuts");
		}

		@Test
		@DisplayName("should include basket section when basket has items")
		void includeBasketSection() {
			Map<String, Integer> basket = new HashMap<>();
			basket.put("BEV-001", 2); // Coca Cola

			String prompt = promptContributor.buildPromptContribution(null, basket);

			assertThat(prompt).contains("CURRENT BASKET");
			assertThat(prompt).contains("Coca Cola");
		}

		@Test
		@DisplayName("should include guidance section when context exists")
		void includeGuidanceSection() {
			MissionRequest request = MissionRequest.builder()
					.description("Test party")
					.headcount(5)
					.occasion(MissionRequest.Occasion.PARTY)
					.allergenExclusions(Set.of("dairy"))
					.build();
			MissionPlan plan = storeApi.planMission(request);

			ShoppingSession session = ShoppingSession.create("test", null)
					.withBudget(new BigDecimal("30.00"))
					.withMission(plan);

			String prompt = promptContributor.buildPromptContribution(session, new HashMap<>());

			assertThat(prompt).contains("GUIDANCE");
			assertThat(prompt).contains("Gap Analysis");
			assertThat(prompt).contains("Allergen Safety");
			assertThat(prompt).contains("Budget Awareness");
		}

		@Test
		@DisplayName("should include allergen info in basket items")
		void includeAllergenInfoInBasket() {
			Map<String, Integer> basket = new HashMap<>();
			basket.put("SNK-003", 1); // Mixed Nuts (contains nuts)

			String prompt = promptContributor.buildPromptContribution(null, basket);

			assertThat(prompt).contains("Contains:");
		}
	}

	@Nested
	@DisplayName("Tool Reset")
	class ToolResetTests {

		@Test
		@DisplayName("should reset mission tool flags")
		void resetMissionToolFlags() {
			missionTool.planMission("test", 5, "PARTY", null, null, null);
			assertThat(missionTool.planMissionInvoked()).isTrue();

			missionTool.reset();
			assertThat(missionTool.planMissionInvoked()).isFalse();
		}
	}
}

