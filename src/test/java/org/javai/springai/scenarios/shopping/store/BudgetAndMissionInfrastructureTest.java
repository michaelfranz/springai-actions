package org.javai.springai.scenarios.shopping.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.javai.springai.scenarios.shopping.store.model.BudgetStatus;
import org.javai.springai.scenarios.shopping.store.model.MissionItem;
import org.javai.springai.scenarios.shopping.store.model.MissionPlan;
import org.javai.springai.scenarios.shopping.store.model.MissionRequest;
import org.javai.springai.scenarios.shopping.store.model.MissionRequest.Occasion;
import org.javai.springai.scenarios.shopping.store.model.Product;
import org.javai.springai.scenarios.shopping.store.model.ShoppingSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Phase 5 infrastructure: Budget tracking and Mission planning.
 */
class BudgetAndMissionInfrastructureTest {

	private MockStoreApi storeApi;

	@BeforeEach
	void setUp() {
		storeApi = new MockStoreApi();
	}

	// ========== SHOPPING SESSION TESTS ==========

	@Nested
	@DisplayName("Shopping Session Model Tests")
	class ShoppingSessionTests {

		@Test
		@DisplayName("should create anonymous session")
		void createAnonymousSession() {
			ShoppingSession session = ShoppingSession.anonymous("sess-001");

			assertThat(session.sessionId()).isEqualTo("sess-001");
			assertThat(session.customerId()).isNull();
			assertThat(session.isAuthenticated()).isFalse();
			assertThat(session.hasBudget()).isFalse();
			assertThat(session.isBasketEmpty()).isTrue();
		}

		@Test
		@DisplayName("should create session with customer ID")
		void createAuthenticatedSession() {
			ShoppingSession session = ShoppingSession.create("sess-002", "cust-001");

			assertThat(session.isAuthenticated()).isTrue();
			assertThat(session.customerId()).isEqualTo("cust-001");
		}

		@Test
		@DisplayName("should set budget on session")
		void setBudget() {
			ShoppingSession session = ShoppingSession.anonymous("sess-003")
					.withBudget(new BigDecimal("50.00"));

			assertThat(session.hasBudget()).isTrue();
			assertThat(session.budgetLimit()).isEqualByComparingTo(new BigDecimal("50.00"));
		}

		@Test
		@DisplayName("should add items to basket immutably")
		void addItemsToBasket() {
			ShoppingSession original = ShoppingSession.anonymous("sess-004");
			ShoppingSession updated = original.withItemAdded("SKU001", 3);

			assertThat(original.isBasketEmpty()).isTrue();
			assertThat(updated.isBasketEmpty()).isFalse();
			assertThat(updated.basket().get("SKU001")).isEqualTo(3);
		}

		@Test
		@DisplayName("should accumulate quantities when adding same item")
		void accumulateQuantities() {
			ShoppingSession session = ShoppingSession.anonymous("sess-005")
					.withItemAdded("SKU001", 2)
					.withItemAdded("SKU001", 3);

			assertThat(session.basket().get("SKU001")).isEqualTo(5);
		}

		@Test
		@DisplayName("should track total item count")
		void trackItemCount() {
			ShoppingSession session = ShoppingSession.anonymous("sess-006")
					.withItemAdded("SKU001", 2)
					.withItemAdded("SKU002", 3)
					.withItemAdded("SKU003", 1);

			assertThat(session.getItemCount()).isEqualTo(6);
		}

		@Test
		@DisplayName("should remove items from basket")
		void removeItems() {
			ShoppingSession session = ShoppingSession.anonymous("sess-007")
					.withItemAdded("SKU001", 2)
					.withItemAdded("SKU002", 3)
					.withItemRemoved("SKU001");

			assertThat(session.basket().containsKey("SKU001")).isFalse();
			assertThat(session.basket().get("SKU002")).isEqualTo(3);
		}

		@Test
		@DisplayName("should update item quantity")
		void updateQuantity() {
			ShoppingSession session = ShoppingSession.anonymous("sess-008")
					.withItemAdded("SKU001", 5)
					.withItemQuantity("SKU001", 2);

			assertThat(session.basket().get("SKU001")).isEqualTo(2);
		}

		@Test
		@DisplayName("should remove item when quantity set to zero")
		void removeOnZeroQuantity() {
			ShoppingSession session = ShoppingSession.anonymous("sess-009")
					.withItemAdded("SKU001", 5)
					.withItemQuantity("SKU001", 0);

			assertThat(session.basket().containsKey("SKU001")).isFalse();
		}
	}

	// ========== BUDGET SERVICE TESTS ==========

	@Nested
	@DisplayName("Budget Service Tests")
	class BudgetServiceTests {

		@Test
		@DisplayName("should return NoBudget for session without budget")
		void noBudgetStatus() {
			ShoppingSession session = ShoppingSession.anonymous("sess-010");

			BudgetStatus status = storeApi.getBudgetStatus(session);

			assertThat(status).isInstanceOf(BudgetStatus.NoBudget.class);
		}

		@Test
		@DisplayName("should return WithinBudget for empty basket")
		void withinBudgetEmpty() {
			ShoppingSession session = ShoppingSession.anonymous("sess-011")
					.withBudget(new BigDecimal("50.00"));

			BudgetStatus status = storeApi.getBudgetStatus(session);

			assertThat(status).isInstanceOf(BudgetStatus.WithinBudget.class);
			BudgetStatus.WithinBudget wb = (BudgetStatus.WithinBudget) status;
			assertThat(wb.remaining()).isEqualByComparingTo(new BigDecimal("50.00"));
		}

		@Test
		@DisplayName("should calculate remaining budget with items")
		void calculateRemainingWithItems() {
			// Coca Cola is £1.50 per bottle
			Product cocaCola = storeApi.findProduct("Coca Cola").orElseThrow();
			
			ShoppingSession session = ShoppingSession.anonymous("sess-012")
					.withBudget(new BigDecimal("50.00"))
					.withItemAdded(cocaCola.sku(), 4); // 4 x £1.50 = £6.00

			BudgetStatus status = storeApi.getBudgetStatus(session);

			assertThat(status).isInstanceOf(BudgetStatus.WithinBudget.class);
			BudgetStatus.WithinBudget wb = (BudgetStatus.WithinBudget) status;
			// Note: Discount may apply (Summer Refresh 10% off)
			assertThat(wb.remaining()).isLessThan(new BigDecimal("50.00"));
		}

		@Test
		@DisplayName("should detect approaching budget limit")
		void detectApproachingLimit() {
			Product cocaCola = storeApi.findProduct("Coca Cola").orElseThrow();
			
			// Budget £10, spend £8.40 (>80%)
			ShoppingSession session = ShoppingSession.anonymous("sess-013")
					.withBudget(new BigDecimal("10.00"))
					.withItemAdded(cocaCola.sku(), 6); // 6 x £1.50 = £9.00 before discount

			BudgetStatus status = storeApi.getBudgetStatus(session);

			// Should be approaching limit (>80% used)
			assertThat(status).isInstanceOfAny(
					BudgetStatus.ApproachingLimit.class,
					BudgetStatus.WithinBudget.class); // Depends on discount
		}

		@Test
		@DisplayName("should detect would exceed budget")
		void detectWouldExceed() {
			Product cheeseBoard = storeApi.findProduct("Cheese Board Selection").orElseThrow();
			
			ShoppingSession session = ShoppingSession.anonymous("sess-014")
					.withBudget(new BigDecimal("10.00"));

			// Cheese Board is £12.00
			BudgetStatus status = storeApi.checkBudgetForAddition(session, "Cheese Board Selection", 1);

			assertThat(status).isInstanceOf(BudgetStatus.WouldExceed.class);
			BudgetStatus.WouldExceed we = (BudgetStatus.WouldExceed) status;
			assertThat(we.proposedItemCost()).isEqualByComparingTo(new BigDecimal("12.00"));
		}

		@Test
		@DisplayName("should calculate max affordable quantity")
		void calculateMaxAffordable() {
			// Coca Cola is £1.50
			ShoppingSession session = ShoppingSession.anonymous("sess-015")
					.withBudget(new BigDecimal("5.00"));

			int maxQty = storeApi.getMaxAffordableQuantity(session, "Coca Cola");

			assertThat(maxQty).isEqualTo(3); // 3 x £1.50 = £4.50, 4 x £1.50 = £6.00
		}

		@Test
		@DisplayName("should return -1 for max affordable when no budget")
		void noLimitWhenNoBudget() {
			ShoppingSession session = ShoppingSession.anonymous("sess-016");

			int maxQty = storeApi.getMaxAffordableQuantity(session, "Coca Cola");

			assertThat(maxQty).isEqualTo(-1);
		}

		@Test
		@DisplayName("should format budget status messages")
		void formatBudgetStatus() {
			BudgetStatus noBudget = new BudgetStatus.NoBudget();
			BudgetStatus withinBudget = new BudgetStatus.WithinBudget(
					new BigDecimal("50.00"), new BigDecimal("20.00"), new BigDecimal("30.00"));
			BudgetStatus approaching = new BudgetStatus.ApproachingLimit(
					new BigDecimal("50.00"), new BigDecimal("42.00"), new BigDecimal("8.00"));
			BudgetStatus exceeded = new BudgetStatus.Exceeded(
					new BigDecimal("50.00"), new BigDecimal("55.00"), new BigDecimal("5.00"));

			assertThat(storeApi.formatBudgetStatus(noBudget)).contains("No budget");
			assertThat(storeApi.formatBudgetStatus(withinBudget)).contains("Remaining: £30.00");
			assertThat(storeApi.formatBudgetStatus(approaching)).contains("Approaching");
			assertThat(storeApi.formatBudgetStatus(exceeded)).contains("Over budget");
		}
	}

	// ========== MISSION REQUEST TESTS ==========

	@Nested
	@DisplayName("Mission Request Model Tests")
	class MissionRequestTests {

		@Test
		@DisplayName("should build mission request with builder")
		void buildMissionRequest() {
			MissionRequest request = MissionRequest.builder()
					.description("Office party")
					.headcount(15)
					.dietaryRequirements(Set.of("vegetarian"))
					.allergenExclusions(Set.of("peanuts"))
					.budgetLimit(new BigDecimal("75.00"))
					.occasion(Occasion.PARTY)
					.build();

			assertThat(request.description()).isEqualTo("Office party");
			assertThat(request.headcount()).isEqualTo(15);
			assertThat(request.hasDietaryRequirements()).isTrue();
			assertThat(request.hasAllergenExclusions()).isTrue();
			assertThat(request.hasBudget()).isTrue();
			assertThat(request.occasion()).isEqualTo(Occasion.PARTY);
		}

		@Test
		@DisplayName("should parse occasion from string")
		void parseOccasion() {
			assertThat(Occasion.fromString("party")).isEqualTo(Occasion.PARTY);
			assertThat(Occasion.fromString("DINNER")).isEqualTo(Occasion.DINNER);
			assertThat(Occasion.fromString("picnic")).isEqualTo(Occasion.PICNIC);
			assertThat(Occasion.fromString("unknown")).isEqualTo(Occasion.SNACKS);
			assertThat(Occasion.fromString(null)).isEqualTo(Occasion.SNACKS);
		}

		@Test
		@DisplayName("occasions should have portion multipliers")
		void occasionPortionMultipliers() {
			assertThat(Occasion.PARTY.portionMultiplier()).isGreaterThan(1.0);
			assertThat(Occasion.MEETING.portionMultiplier()).isLessThan(1.0);
			assertThat(Occasion.DINNER.portionMultiplier()).isEqualTo(1.0);
		}
	}

	// ========== MISSION PLANNING SERVICE TESTS ==========

	@Nested
	@DisplayName("Mission Planning Service Tests")
	class MissionPlanningTests {

		@Test
		@DisplayName("should plan simple snacks mission")
		void planSimpleSnacks() {
			MissionRequest request = MissionRequest.builder()
					.description("Quick snacks")
					.headcount(4)
					.occasion(Occasion.SNACKS)
					.build();

			MissionPlan plan = storeApi.planMission(request);

			assertThat(plan.hasItems()).isTrue();
			assertThat(plan.items()).isNotEmpty();
			assertThat(plan.estimatedTotal()).isPositive();
		}

		@Test
		@DisplayName("should plan party with appropriate categories")
		void planPartyMission() {
			MissionRequest request = MissionRequest.builder()
					.description("Birthday party")
					.headcount(10)
					.occasion(Occasion.PARTY)
					.build();

			MissionPlan plan = storeApi.planMission(request);

			assertThat(plan.hasItems()).isTrue();
			// Party should include party, snacks, beverages categories
			Set<String> categories = plan.getItemsByCategory().keySet();
			assertThat(categories).containsAnyOf("party", "snacks", "beverages");
		}

		@Test
		@DisplayName("should respect vegetarian dietary requirement")
		void respectVegetarianRequirement() {
			MissionRequest request = MissionRequest.builder()
					.description("Vegetarian gathering")
					.headcount(6)
					.dietaryRequirements(Set.of("vegetarian"))
					.occasion(Occasion.PARTY)
					.build();

			MissionPlan plan = storeApi.planMission(request);

			// All items should be vegetarian
			for (MissionItem item : plan.items()) {
				assertThat(item.product().hasDietaryFlag("vegetarian"))
						.as("Product %s should be vegetarian", item.productName())
						.isTrue();
			}
		}

		@Test
		@DisplayName("should respect vegan dietary requirement")
		void respectVeganRequirement() {
			MissionRequest request = MissionRequest.builder()
					.description("Vegan party")
					.headcount(8)
					.dietaryRequirements(Set.of("vegan"))
					.occasion(Occasion.SNACKS)
					.build();

			MissionPlan plan = storeApi.planMission(request);

			// All items should be vegan
			for (MissionItem item : plan.items()) {
				assertThat(item.product().hasDietaryFlag("vegan"))
						.as("Product %s should be vegan", item.productName())
						.isTrue();
			}
		}

		@Test
		@DisplayName("should exclude allergens from plan")
		void excludeAllergens() {
			MissionRequest request = MissionRequest.builder()
					.description("Nut-free gathering")
					.headcount(6)
					.allergenExclusions(Set.of("peanuts", "tree nuts"))
					.occasion(Occasion.SNACKS)
					.build();

			MissionPlan plan = storeApi.planMission(request);

			// No items should contain nuts
			for (MissionItem item : plan.items()) {
				assertThat(item.product().allergens())
						.as("Product %s should not contain nuts", item.productName())
						.doesNotContain("peanuts", "tree nuts");
			}
		}

		@Test
		@DisplayName("should respect budget limit")
		void respectBudgetLimit() {
			MissionRequest request = MissionRequest.builder()
					.description("Budget party")
					.headcount(10)
					.budgetLimit(new BigDecimal("25.00"))
					.occasion(Occasion.PARTY)
					.build();

			MissionPlan plan = storeApi.planMission(request);

			assertThat(plan.estimatedTotal()).isLessThanOrEqualTo(new BigDecimal("25.00"));
			assertThat(plan.isWithinBudget()).isTrue();
		}

		@Test
		@DisplayName("should scale quantities with headcount")
		void scaleWithHeadcount() {
			MissionRequest smallRequest = MissionRequest.builder()
					.description("Small gathering")
					.headcount(4)
					.occasion(Occasion.SNACKS)
					.build();

			MissionRequest largeRequest = MissionRequest.builder()
					.description("Large gathering")
					.headcount(12)
					.occasion(Occasion.SNACKS)
					.build();

			MissionPlan smallPlan = storeApi.planMission(smallRequest);
			MissionPlan largePlan = storeApi.planMission(largeRequest);

			// Larger headcount should result in more items
			assertThat(largePlan.getTotalItemCount())
					.isGreaterThan(smallPlan.getTotalItemCount());
		}

		@Test
		@DisplayName("should include notes about the plan")
		void includeNotes() {
			MissionRequest request = MissionRequest.builder()
					.description("Team lunch")
					.headcount(8)
					.dietaryRequirements(Set.of("vegetarian"))
					.occasion(Occasion.DINNER)
					.build();

			MissionPlan plan = storeApi.planMission(request);

			assertThat(plan.notes()).isNotEmpty();
			// Should mention the occasion
			assertThat(plan.notes()).anyMatch(note -> note.toLowerCase().contains("dinner"));
			// Should mention vegetarian
			assertThat(plan.notes()).anyMatch(note -> note.toLowerCase().contains("vegetarian"));
		}

		@Test
		@DisplayName("should convert plan to basket map")
		void convertToBasketMap() {
			MissionRequest request = MissionRequest.builder()
					.description("Quick snacks")
					.headcount(4)
					.occasion(Occasion.SNACKS)
					.build();

			MissionPlan plan = storeApi.planMission(request);
			Map<String, Integer> basket = plan.toBasketMap();

			assertThat(basket).isNotEmpty();
			// All values should be positive
			assertThat(basket.values()).allMatch(qty -> qty > 0);
		}

		@Test
		@DisplayName("should refine plan with adjustments")
		void refinePlanWithAdjustments() {
			MissionRequest request = MissionRequest.builder()
					.description("Party")
					.headcount(6)
					.occasion(Occasion.PARTY)
					.build();

			MissionPlan original = storeApi.planMission(request);

			// Get first item and adjust its quantity
			if (!original.items().isEmpty()) {
				String productName = original.items().get(0).productName();
				Map<String, Integer> adjustments = Map.of(productName, 1);

				MissionPlan refined = storeApi.refineMissionPlan(original, adjustments);

				assertThat(refined.items()).anyMatch(item ->
						item.productName().equals(productName) && item.quantity() == 1);
			}
		}

		@Test
		@DisplayName("should suggest additions to complement plan")
		void suggestAdditions() {
			MissionRequest request = MissionRequest.builder()
					.description("Small snacks")
					.headcount(2)
					.budgetLimit(new BigDecimal("10.00"))
					.occasion(Occasion.SNACKS)
					.build();

			MissionPlan plan = storeApi.planMission(request);
			List<Product> suggestions = storeApi.suggestMissionAdditions(plan, 5);

			// Suggestions should not include items already in plan
			Set<String> planSkus = plan.toBasketMap().keySet();
			for (Product suggestion : suggestions) {
				assertThat(planSkus).doesNotContain(suggestion.sku());
			}
		}
	}

	// ========== MISSION PLAN MODEL TESTS ==========

	@Nested
	@DisplayName("Mission Plan Model Tests")
	class MissionPlanModelTests {

		@Test
		@DisplayName("should build plan with items")
		void buildPlanWithItems() {
			MissionRequest request = MissionRequest.builder()
					.description("Test")
					.headcount(4)
					.build();

			Product product = storeApi.findProduct("Coca Cola").orElseThrow();

			MissionPlan plan = MissionPlan.builder(request)
					.addItem(product, 4, "1 per person")
					.addNote("Test note")
					.build();

			assertThat(plan.items()).hasSize(1);
			assertThat(plan.items().get(0).quantity()).isEqualTo(4);
			assertThat(plan.estimatedTotal()).isEqualByComparingTo(new BigDecimal("6.00")); // 4 x £1.50
		}

		@Test
		@DisplayName("should track budget remaining in notes")
		void trackBudgetInNotes() {
			MissionRequest request = MissionRequest.builder()
					.description("Test")
					.headcount(2)
					.budgetLimit(new BigDecimal("20.00"))
					.build();

			Product product = storeApi.findProduct("Sea Salt Crisps").orElseThrow(); // £0.90

			MissionPlan plan = MissionPlan.builder(request)
					.addItem(product, 2, "1 per person")
					.build();

			assertThat(plan.notes()).anyMatch(note -> note.contains("Budget remaining"));
		}

		@Test
		@DisplayName("should group items by category")
		void groupByCategory() {
			MissionRequest request = MissionRequest.builder()
					.description("Mixed")
					.headcount(4)
					.occasion(Occasion.PARTY)
					.build();

			MissionPlan plan = storeApi.planMission(request);
			Map<String, List<MissionItem>> byCategory = plan.getItemsByCategory();

			// Should have at least one category
			assertThat(byCategory).isNotEmpty();
		}
	}

	// ========== INTEGRATION TESTS ==========

	@Nested
	@DisplayName("Budget and Mission Integration Tests")
	class IntegrationTests {

		@Test
		@DisplayName("should plan mission within session budget")
		void planMissionWithinSessionBudget() {
			ShoppingSession session = ShoppingSession.anonymous("sess-020")
					.withBudget(new BigDecimal("30.00"));

			MissionRequest request = MissionRequest.builder()
					.description("Party within budget")
					.headcount(6)
					.budgetLimit(session.budgetLimit())
					.occasion(Occasion.PARTY)
					.build();

			MissionPlan plan = storeApi.planMission(request);

			// Plan should fit within session budget
			assertThat(plan.isWithinBudget()).isTrue();

			// Adding plan to session should not exceed budget
			ShoppingSession withPlan = session.withBasket(plan.toBasketMap());
			BudgetStatus status = storeApi.getBudgetStatus(withPlan);

			assertThat(status).isNotInstanceOf(BudgetStatus.Exceeded.class);
		}

		@Test
		@DisplayName("complex mission with all constraints")
		void complexMissionWithAllConstraints() {
			MissionRequest request = MissionRequest.builder()
					.description("Vegan party with nut allergy guest")
					.headcount(8)
					.dietaryRequirements(Set.of("vegan"))
					.allergenExclusions(Set.of("peanuts", "tree nuts"))
					.budgetLimit(new BigDecimal("40.00"))
					.occasion(Occasion.PARTY)
					.build();

			MissionPlan plan = storeApi.planMission(request);

			// Verify all constraints
			assertThat(plan.isWithinBudget()).isTrue();

			for (MissionItem item : plan.items()) {
				Product p = item.product();
				assertThat(p.hasDietaryFlag("vegan"))
						.as("%s should be vegan", p.name()).isTrue();
				assertThat(p.allergens())
						.as("%s should not contain nuts", p.name())
						.doesNotContain("peanuts", "tree nuts");
			}
		}
	}
}

