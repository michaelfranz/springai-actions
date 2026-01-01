package org.javai.springai.scenarios.shopping.tests;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.javai.springai.scenarios.shopping.actions.ActionResult;
import org.javai.springai.scenarios.shopping.actions.InventoryAwareShoppingActions;
import org.javai.springai.scenarios.shopping.store.CustomerProfileService;
import org.javai.springai.scenarios.shopping.store.MockStoreApi;
import org.javai.springai.scenarios.shopping.store.model.CustomerProfile;
import org.javai.springai.scenarios.shopping.store.model.Product;
import org.javai.springai.scenarios.shopping.store.model.PurchaseHistory;
import org.javai.springai.scenarios.shopping.tools.CustomerTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for customer personalisation functionality.
 */
class CustomerPersonalisationTest {

	private MockStoreApi storeApi;
	private CustomerProfileService customerService;
	private InventoryAwareShoppingActions actions;
	private CustomerTool customerTool;

	@BeforeEach
	void setUp() {
		storeApi = new MockStoreApi();
		customerService = storeApi.getCustomers();
		actions = new InventoryAwareShoppingActions(storeApi);
		customerTool = new CustomerTool(storeApi, customerService);
	}

	@Nested
	@DisplayName("Customer Profile Tests")
	class CustomerProfileTests {

		@Test
		@DisplayName("should have 5 pre-seeded customers")
		void hasSeedCustomers() {
			Set<String> customerIds = customerService.getAllCustomerIds();

			assertThat(customerIds).hasSize(5);
			assertThat(customerIds).contains("cust-001", "cust-002", "cust-003", "cust-004", "cust-005");
		}

		@Test
		@DisplayName("should retrieve customer profile by ID")
		void getCustomerProfile() {
			Optional<CustomerProfile> profile = customerService.getProfile("cust-001");

			assertThat(profile).isPresent();
			assertThat(profile.get().name()).isEqualTo("Alex");
			assertThat(profile.get().dietaryPreferences()).contains("vegetarian");
		}

		@Test
		@DisplayName("Alex should be vegetarian")
		void alexIsVegetarian() {
			Optional<CustomerProfile> profile = customerService.getProfile("cust-001");

			assertThat(profile).isPresent();
			assertThat(profile.get().hasDietaryPreference("vegetarian")).isTrue();
		}

		@Test
		@DisplayName("Sam should have nut allergies")
		void samHasNutAllergies() {
			Optional<CustomerProfile> profile = customerService.getProfile("cust-003");

			assertThat(profile).isPresent();
			assertThat(profile.get().allergens()).contains("peanuts", "tree nuts");
		}

		@Test
		@DisplayName("Morgan should be dairy-free")
		void morganIsDairyFree() {
			Optional<CustomerProfile> profile = customerService.getProfile("cust-005");

			assertThat(profile).isPresent();
			assertThat(profile.get().hasAllergen("dairy")).isTrue();
		}

		@Test
		@DisplayName("Taylor should have high budget for parties")
		void taylorHasHighBudget() {
			Optional<CustomerProfile> profile = customerService.getProfile("cust-004");

			assertThat(profile).isPresent();
			assertThat(profile.get().hasBudget()).isTrue();
			assertThat(profile.get().defaultBudget()).isEqualByComparingTo(new BigDecimal("100.00"));
		}
	}

	@Nested
	@DisplayName("Purchase History Tests")
	class PurchaseHistoryTests {

		@Test
		@DisplayName("customers should have purchase history")
		void customersHavePurchaseHistory() {
			PurchaseHistory history = customerService.getPurchaseHistory("cust-001");

			assertThat(history.orders()).isNotEmpty();
			assertThat(history.orders()).hasSizeGreaterThanOrEqualTo(2);
		}

		@Test
		@DisplayName("should calculate total spent")
		void calculateTotalSpent() {
			PurchaseHistory history = customerService.getPurchaseHistory("cust-001");

			assertThat(history.getTotalSpent()).isPositive();
		}

		@Test
		@DisplayName("should calculate average order value")
		void calculateAverageOrderValue() {
			PurchaseHistory history = customerService.getPurchaseHistory("cust-001");

			assertThat(history.getAverageOrderValue()).isPositive();
		}

		@Test
		@DisplayName("should get frequently bought SKUs")
		void getFrequentlyBoughtSkus() {
			PurchaseHistory history = customerService.getPurchaseHistory("cust-001");
			Map<String, Integer> frequent = history.getFrequentlyBoughtSkus(5);

			assertThat(frequent).isNotEmpty();
			// Alex frequently buys Coke Zero (BEV-002)
			assertThat(frequent).containsKey("BEV-002");
		}

		@Test
		@DisplayName("should record new purchases")
		void recordNewPurchase() {
			int initialOrderCount = customerService.getPurchaseHistory("cust-001").orders().size();

			customerService.recordPurchase("cust-001",
					Map.of("BEV-002", 5, "SNK-001", 2),
					new BigDecimal("12.50"));

			PurchaseHistory updated = customerService.getPurchaseHistory("cust-001");
			assertThat(updated.orders()).hasSize(initialOrderCount + 1);
		}
	}

	@Nested
	@DisplayName("Recommendation Tests")
	class RecommendationTests {

		@Test
		@DisplayName("should get frequently bought items for customer")
		void getFrequentlyBoughtItems() {
			List<Product> frequent = customerService.getFrequentlyBoughtItems("cust-001", 5);

			assertThat(frequent).isNotEmpty();
			// Alex frequently buys Coke Zero
			assertThat(frequent).anyMatch(p -> p.name().equals("Coke Zero"));
		}

		@Test
		@DisplayName("should get personalized recommendations")
		void getPersonalizedRecommendations() {
			List<Product> recommendations = customerService.getRecommendations(
					"cust-001", Set.of(), 5);

			assertThat(recommendations).isNotEmpty();
		}

		@Test
		@DisplayName("recommendations should respect allergen restrictions")
		void recommendationsRespectAllergens() {
			// Sam has nut allergies
			List<Product> recommendations = customerService.getRecommendations(
					"cust-003", Set.of(), 10);

			// None should contain peanuts or tree nuts
			assertThat(recommendations).noneMatch(p ->
					p.allergens().contains("peanuts") || p.allergens().contains("tree nuts"));
		}

		@Test
		@DisplayName("recommendations should exclude items already in basket")
		void recommendationsExcludeBasketItems() {
			Set<String> basket = Set.of("BEV-002"); // Coke Zero in basket

			List<Product> recommendations = customerService.getRecommendations(
					"cust-001", basket, 5);

			assertThat(recommendations).noneMatch(p -> p.sku().equals("BEV-002"));
		}

		@Test
		@DisplayName("should get similar products to usual purchases")
		void getSimilarToUsual() {
			List<Product> similar = customerService.getSimilarToUsual("cust-001", 5);

			assertThat(similar).isNotEmpty();
		}
	}

	@Nested
	@DisplayName("Product Safety Tests")
	class ProductSafetyTests {

		@Test
		@DisplayName("should identify safe products for customer")
		void identifySafeProducts() {
			// Sam has nut allergies
			boolean isSafe = customerService.isProductSafeForCustomer("cust-003", "SNK-001"); // Sea Salt Crisps

			assertThat(isSafe).isTrue();
		}

		@Test
		@DisplayName("should identify unsafe products for customer")
		void identifyUnsafeProducts() {
			// Sam has nut allergies, Mixed Nuts are not safe
			boolean isSafe = customerService.isProductSafeForCustomer("cust-003", "SNK-003"); // Mixed Nuts

			assertThat(isSafe).isFalse();
		}

		@Test
		@DisplayName("should get all safe products for customer")
		void getAllSafeProducts() {
			List<Product> safeProducts = customerService.getSafeProductsForCustomer("cust-003");

			assertThat(safeProducts).isNotEmpty();
			assertThat(safeProducts).noneMatch(p ->
					p.allergens().contains("peanuts") || p.allergens().contains("tree nuts"));
		}
	}

	@Nested
	@DisplayName("Customer Tool Tests")
	class CustomerToolTests {

		@Test
		@DisplayName("should get customer profile via tool")
		void getProfileViaTool() {
			String result = customerTool.getCustomerProfile("cust-001");

			assertThat(result).contains("Alex");
			assertThat(result).contains("vegetarian");
			assertThat(customerTool.getProfileInvoked()).isTrue();
		}

		@Test
		@DisplayName("should get recommendations via tool")
		void getRecommendationsViaTool() {
			String result = customerTool.getRecommendations("cust-001", "");

			assertThat(result).contains("Recommendations for Alex");
			assertThat(result).contains("£");
			assertThat(customerTool.getRecommendationsInvoked()).isTrue();
		}

		@Test
		@DisplayName("should get frequent purchases via tool")
		void getFrequentPurchasesViaTool() {
			String result = customerTool.getFrequentPurchases("cust-001");

			assertThat(result).contains("frequently bought");
			assertThat(result).contains("Coke Zero");
			assertThat(customerTool.getFrequentPurchasesInvoked()).isTrue();
		}

		@Test
		@DisplayName("should get personalized offers via tool")
		void getPersonalizedOffersViaTool() {
			String result = customerTool.getPersonalizedOffers("cust-001");

			assertThat(result).contains("offers");
			assertThat(customerTool.getPersonalizedOffersInvoked()).isTrue();
		}

		@Test
		@DisplayName("should check product safety via tool")
		void checkProductSafetyViaTool() {
			// Safe product
			String safe = customerTool.checkProductSafety("cust-003", "Sea Salt Crisps");
			assertThat(safe).contains("✓");
			assertThat(safe).contains("safe");

			// Unsafe product
			String unsafe = customerTool.checkProductSafety("cust-003", "Mixed Nuts");
			assertThat(unsafe).contains("⚠️");
			assertThat(unsafe).contains("WARNING");
		}

		@Test
		@DisplayName("should get similar products via tool")
		void getSimilarProductsViaTool() {
			String result = customerTool.getSimilarProducts("cust-001");

			assertThat(result).contains("similar");
		}

		@Test
		@DisplayName("should handle unknown customer")
		void handleUnknownCustomer() {
			String result = customerTool.getCustomerProfile("unknown-123");

			assertThat(result).containsIgnoringCase("not found");
		}
	}

	@Nested
	@DisplayName("Customer-Aware Session Tests")
	class CustomerAwareSessionTests {

		@Test
		@DisplayName("should start session for known customer")
		void startSessionForKnownCustomer() {
			ActionResult result = actions.startSessionForCustomer(null, "cust-001");

			assertThat(result.isSuccess()).isTrue();
			assertThat(result.message()).contains("Welcome back");
			assertThat(result.message()).contains("Alex");
			assertThat(actions.currentCustomerId()).isEqualTo("cust-001");
		}

		@Test
		@DisplayName("should mention allergens for customer with restrictions")
		void mentionAllergens() {
			ActionResult result = actions.startSessionForCustomer(null, "cust-003");

			assertThat(result.isSuccess()).isTrue();
			assertThat(result.message()).contains("Sam");
			assertThat(result.message()).containsIgnoringCase("allergy");
		}

		@Test
		@DisplayName("should show recommendations for identified customer")
		void showRecommendationsForCustomer() {
			actions.startSessionForCustomer(null, "cust-001");

			ActionResult result = actions.showRecommendations();

			assertThat(result.isSuccess()).isTrue();
			assertThat(result.message()).contains("Recommended");
			assertThat(result.message()).contains("Alex");
			assertThat(actions.showRecommendationsInvoked()).isTrue();
		}

		@Test
		@DisplayName("should fail recommendations without customer ID")
		void failRecommendationsWithoutCustomer() {
			actions.startSession(null); // No customer ID

			ActionResult result = actions.showRecommendations();

			assertThat(result.isSuccess()).isFalse();
			assertThat(result.message()).containsIgnoringCase("no customer");
		}

		@Test
		@DisplayName("should record purchase history on checkout")
		void recordPurchaseOnCheckout() {
			int initialOrders = customerService.getPurchaseHistory("cust-001").orders().size();

			actions.startSessionForCustomer(null, "cust-001");
			actions.addItem("Coke Zero", 3);
			actions.checkoutBasket();

			int finalOrders = customerService.getPurchaseHistory("cust-001").orders().size();
			assertThat(finalOrders).isEqualTo(initialOrders + 1);
		}
	}

	@Nested
	@DisplayName("Full Personalisation Flow Tests")
	class FullFlowTests {

		@Test
		@DisplayName("complete personalized shopping session")
		void completePersonalizedSession() {
			// 1. Get customer profile
			String profile = customerTool.getCustomerProfile("cust-001");
			assertThat(profile).contains("Alex");
			assertThat(profile).contains("vegetarian");

			// 2. Start session for customer
			ActionResult start = actions.startSessionForCustomer(null, "cust-001");
			assertThat(start.isSuccess()).isTrue();

			// 3. Get recommendations
			ActionResult recs = actions.showRecommendations();
			assertThat(recs.isSuccess()).isTrue();

			// 4. Get frequent purchases
			String frequent = customerTool.getFrequentPurchases("cust-001");
			assertThat(frequent).contains("Coke Zero");

			// 5. Add a frequently bought item
			actions.addItem("Coke Zero", 4);

			// 6. Get personalized offers
			String offers = customerTool.getPersonalizedOffers("cust-001");
			assertThat(offers).contains("Summer Refresh"); // Coke Zero offer

			// 7. Checkout
			ActionResult checkout = actions.checkoutBasket();
			assertThat(checkout.isSuccess()).isTrue();
		}

		@Test
		@DisplayName("allergen-aware shopping for Sam")
		void allergenAwareShoppingForSam() {
			// Sam has nut allergies
			actions.startSessionForCustomer(null, "cust-003");

			// Get recommendations - should all be nut-free
			ActionResult recs = actions.showRecommendations();
			assertThat(recs.isSuccess()).isTrue();

			// Check product safety before adding
			String safetyCheck = customerTool.checkProductSafety("cust-003", "Mixed Nuts");
			assertThat(safetyCheck).contains("WARNING");

			// Add a safe product
			String safeCheck = customerTool.checkProductSafety("cust-003", "Guacamole");
			assertThat(safeCheck).contains("safe");

			actions.addItem("Guacamole", 2);
			ActionResult basket = actions.viewBasketSummary();
			assertThat(basket.message()).contains("Guacamole");
		}

		@Test
		@DisplayName("party planning for Taylor")
		void partyPlanningForTaylor() {
			// Taylor is a party planner with high budget
			String profile = customerTool.getCustomerProfile("cust-004");
			assertThat(profile).contains("Taylor");
			assertThat(profile).contains("party");

			actions.startSessionForCustomer(null, "cust-004");

			// Taylor's recommendations should include party items
			ActionResult recs = actions.showRecommendations();
			assertThat(recs.isSuccess()).isTrue();

			// Add party items
			actions.addItem("Caprese Skewers", 3);
			actions.addItem("Coke Zero", 12);

			ActionResult total = actions.computeTotal();
			assertThat(total.isSuccess()).isTrue();
		}
	}
}

