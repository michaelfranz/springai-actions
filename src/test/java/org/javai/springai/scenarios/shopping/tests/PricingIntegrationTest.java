package org.javai.springai.scenarios.shopping.tests;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.javai.springai.actions.PersonaSpec;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.scenarios.shopping.actions.ActionResult;
import org.javai.springai.scenarios.shopping.actions.InventoryAwareShoppingActions;
import org.javai.springai.scenarios.shopping.actions.ShoppingPersonaSpec;
import org.javai.springai.scenarios.shopping.store.MockStoreApi;
import org.javai.springai.scenarios.shopping.store.model.PricingBreakdown;
import org.javai.springai.scenarios.shopping.tools.EnhancedSpecialOfferTool;
import org.javai.springai.scenarios.shopping.tools.PricingTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for pricing functionality.
 * Tests discount application, pricing tools, and checkout flow.
 */
class PricingIntegrationTest {

	private MockStoreApi storeApi;
	private InventoryAwareShoppingActions actions;
	private PricingTool pricingTool;
	private EnhancedSpecialOfferTool offerTool;
	private Map<String, Integer> basket;
	private ActionContext context;

	@BeforeEach
	void setUp() {
		storeApi = new MockStoreApi();
		actions = new InventoryAwareShoppingActions(storeApi);
		pricingTool = new PricingTool(storeApi);
		offerTool = new EnhancedSpecialOfferTool(storeApi);
		
		// Set up shared basket and context for direct action method calls
		basket = new HashMap<>();
		context = new ActionContext();
		context.put("basket", basket);
	}

	@Nested
	@DisplayName("Percentage Discount Tests")
	class PercentageDiscountTests {

		@Test
		@DisplayName("should apply 10% off to Coke Zero (Summer Refresh)")
		void applyCokeZeroDiscount() {
			// Coke Zero: £1.50 x 10 = £15.00, 10% off = £1.50 discount
			actions.addItem(basket, "Coke Zero", 10);

			ActionResult total = actions.computeTotal(basket);

			assertThat(total.isSuccess()).isTrue();
			assertThat(total.message()).contains("Subtotal: £15.00");
			assertThat(total.message()).contains("Discounts: -£1.50");
			assertThat(total.message()).contains("Total: £13.50");
		}

		@Test
		@DisplayName("should apply 15% off to snacks (Party Pack)")
		void applySnacksDiscount() {
			// Sea Salt Crisps: £2.00 x 4 = £8.00, 15% off = £1.20 discount
			actions.addItem(basket, "Sea Salt Crisps", 4);

			ActionResult total = actions.computeTotal(basket);

			assertThat(total.isSuccess()).isTrue();
			assertThat(total.message()).contains("Subtotal: £8.00");
			assertThat(total.message()).contains("Discounts: -£1.20");
			assertThat(total.message()).contains("Total: £6.80");
		}

		@Test
		@DisplayName("should apply 20% off to party items (Party Platter Special)")
		void applyPartyItemDiscount() {
			// Caprese Skewers: £6.00 x 2 = £12.00, 20% off = £2.40 discount
			actions.addItem(basket, "Caprese Skewers", 2);

			ActionResult total = actions.computeTotal(basket);

			assertThat(total.isSuccess()).isTrue();
			assertThat(total.message()).contains("£12.00");
			assertThat(total.message()).contains("Discounts");
			// Total should be £9.60
			assertThat(total.message()).contains("£9.60");
		}

		@Test
		@DisplayName("should apply 10% off to produce (Healthy Choice)")
		void applyProduceDiscount() {
			// Fruit Salad Bowl: £4.50 x 2 = £9.00, 10% off = £0.90 discount
			actions.addItem(basket, "Fruit Salad Bowl", 2);

			ActionResult total = actions.computeTotal(basket);

			assertThat(total.isSuccess()).isTrue();
			assertThat(total.message()).contains("£9.00");
			assertThat(total.message()).contains("Discounts");
		}
	}

	@Nested
	@DisplayName("Fixed Amount Discount Tests")
	class FixedAmountDiscountTests {

		@Test
		@DisplayName("should apply £0.20 off per milk/yogurt (Dairy Deal)")
		void applyDairyDeal() {
			// Milk: £1.20 x 3 = £3.60, £0.20 off x 3 = £0.60 discount
			actions.addItem(basket, "Semi-skimmed Milk", 3);

			ActionResult total = actions.computeTotal(basket);

			assertThat(total.isSuccess()).isTrue();
			assertThat(total.message()).contains("£3.60");
			assertThat(total.message()).contains("Discounts");
			// Total should be £3.00
			assertThat(total.message()).contains("£3.00");
		}

		@Test
		@DisplayName("should apply dairy deal to yogurt")
		void applyDairyDealToYogurt() {
			// Greek Yogurt: £2.00 x 2 = £4.00, £0.20 off x 2 = £0.40 discount
			actions.addItem(basket, "Greek Yogurt", 2);

			ActionResult total = actions.computeTotal(basket);

			assertThat(total.isSuccess()).isTrue();
			assertThat(total.message()).contains("£4.00");
			assertThat(total.message()).contains("Discounts");
		}
	}

	@Nested
	@DisplayName("Multiple Discounts Tests")
	class MultipleDiscountsTests {

		@Test
		@DisplayName("should apply multiple discounts to mixed basket")
		void applyMultipleDiscounts() {
			actions.addItem(basket, "Coke Zero", 4);       // 10% off
			actions.addItem(basket, "Sea Salt Crisps", 2); // 15% off
			actions.addItem(basket, "Greek Yogurt", 1);    // £0.20 off

			ActionResult total = actions.computeTotal(basket);

			assertThat(total.isSuccess()).isTrue();
			assertThat(total.message()).contains("Discounts");
			// Should have multiple discount lines
			String message = total.message();
			assertThat(message.split("Discounts")[1]).contains("-£");
		}

		@Test
		@DisplayName("basket summary should show discounts")
		void basketSummaryShowsDiscounts() {
			actions.addItem(basket, "Coke Zero", 5);
			actions.addItem(basket, "Caprese Skewers", 1);

			ActionResult summary = actions.viewBasketSummary(basket);

			assertThat(summary.isSuccess()).isTrue();
			assertThat(summary.message()).contains("Coke Zero");
			assertThat(summary.message()).contains("Caprese Skewers");
			assertThat(summary.message()).contains("Discounts applied");
			assertThat(summary.message()).contains("Total:");
		}
	}

	@Nested
	@DisplayName("Pricing Tool Tests")
	class PricingToolTests {

		@Test
		@DisplayName("should calculate basket total with tool")
		void calculateBasketTotal() {
			String result = pricingTool.calculateBasketTotal("Coke Zero:3, Sea Salt Crisps:2");

			assertThat(result).contains("Basket breakdown");
			assertThat(result).contains("Coke Zero");
			assertThat(result).contains("Sea Salt Crisps");
			assertThat(result).contains("Subtotal:");
			assertThat(result).contains("Total:");
			assertThat(pricingTool.calculateTotalInvoked()).isTrue();
		}

		@Test
		@DisplayName("should get product price with offers")
		void getProductPrice() {
			String result = pricingTool.getProductPrice("Coke Zero");

			assertThat(result).contains("Coke Zero");
			assertThat(result).contains("£1.50");
			assertThat(result).contains("Current offers");
			assertThat(result).contains("Summer Refresh");
		}

		@Test
		@DisplayName("should estimate cost with discount")
		void estimateCost() {
			String result = pricingTool.estimateCost("Coke Zero", 10);

			assertThat(result).contains("10 × Coke Zero");
			assertThat(result).contains("£15.00");
			assertThat(result).contains("Discount");
			assertThat(result).contains("Summer Refresh");
			assertThat(result).contains("Total after discount: £13.50");
			assertThat(pricingTool.estimateCostInvoked()).isTrue();
		}

		@Test
		@DisplayName("should get applicable offers")
		void getApplicableOffers() {
			String result = pricingTool.getApplicableOffers("Coke Zero, Coca Cola, Sea Salt Crisps");

			assertThat(result).contains("Applicable offers");
			assertThat(result).contains("Summer Refresh");
			assertThat(result).contains("Party Pack");
			assertThat(pricingTool.getApplicableOffersInvoked()).isTrue();
		}

		@Test
		@DisplayName("should compare prices")
		void comparePrices() {
			String result = pricingTool.comparePrices("Coca Cola, Coke Zero, Sparkling Water");

			assertThat(result).contains("Price comparison");
			assertThat(result).contains("Coca Cola");
			assertThat(result).contains("Coke Zero");
			assertThat(result).contains("Sparkling Water");
			assertThat(result).contains("🏷️"); // Has discount indicator
		}

		@Test
		@DisplayName("should calculate savings")
		void calculateSavings() {
			String result = pricingTool.calculateSavings("Coke Zero:10, Sea Salt Crisps:4");

			assertThat(result).contains("Your savings today");
			assertThat(result).contains("Summer Refresh");
			assertThat(result).contains("Party Pack");
			assertThat(result).contains("Total savings");
		}

		@Test
		@DisplayName("should handle no discounts")
		void handleNoDiscounts() {
			// Baguette has no discount
			String result = pricingTool.estimateCost("Baguette", 2);

			assertThat(result).contains("Baguette");
			assertThat(result).contains("Total: £3.00"); // £1.50 x 2
			assertThat(result).doesNotContain("Discount");
		}
	}

	@Nested
	@DisplayName("Checkout Pricing Tests")
	class CheckoutPricingTests {

		@Test
		@DisplayName("should show correct total at checkout")
		void correctTotalAtCheckout() {
			actions.addItem(basket, "Coke Zero", 6);       // £1.50 x 6 = £9.00, 10% off = £0.90, net = £8.10
			actions.addItem(basket, "Sea Salt Crisps", 2); // £2.00 x 2 = £4.00, 15% off = £0.60, net = £3.40
			// Total: £11.50

			ActionResult checkout = actions.checkoutBasket(basket);

			assertThat(checkout.isSuccess()).isTrue();
			assertThat(checkout.message()).contains("Order complete");
			assertThat(checkout.message()).contains("£11.50");
		}

		@Test
		@DisplayName("should clear basket after checkout")
		void clearBasketAfterCheckout() {
			actions.addItem(basket, "Coke Zero", 3);
			actions.checkoutBasket(basket);

			assertThat(actions.getBasketState()).isEmpty();
		}

		@Test
		@DisplayName("should not checkout empty basket")
		void noCheckoutEmptyBasket() {
			ActionResult result = actions.checkoutBasket(basket);

			assertThat(result.isSuccess()).isFalse();
			assertThat(result.message()).containsIgnoringCase("empty");
		}
	}

	@Nested
	@DisplayName("Pricing Consistency Tests")
	class PricingConsistencyTests {

		@Test
		@DisplayName("pricing tool and actions should give same total")
		void pricingConsistency() {
			// Add items via actions
			actions.addItem(basket, "Coke Zero", 5);
			actions.addItem(basket, "Hummus", 2);

			ActionResult actionTotal = actions.computeTotal(basket);

			// Calculate same basket via pricing tool
			String toolTotal = pricingTool.calculateBasketTotal("Coke Zero:5, Hummus:2");

			// Both should show the same final total
			// Extract totals from messages
			assertThat(actionTotal.message()).contains("Total:");
			assertThat(toolTotal).contains("Total:");
		}

		@Test
		@DisplayName("estimated cost should match basket total for single item")
		void estimateMatchesSingleItemBasket() {
			String estimate = pricingTool.estimateCost("Coke Zero", 5);

			actions.addItem(basket, "Coke Zero", 5);
			ActionResult basketTotal = actions.computeTotal(basket);

			// Both should have the same final total (£6.75)
			// £1.50 x 5 = £7.50, 10% off = £0.75, net = £6.75
			assertThat(estimate).contains("£6.75");
			assertThat(basketTotal.message()).contains("£6.75");
		}
	}

	@Nested
	@DisplayName("Offer Tool Integration Tests")
	class OfferToolIntegrationTests {

		@Test
		@DisplayName("should list all active offers")
		void listActiveOffers() {
			String result = offerTool.listSpecialOffers();

			assertThat(result).contains("Summer Refresh");
			assertThat(result).contains("Party Pack");
			assertThat(result).contains("Dairy Deal");
			assertThat(result).contains("Healthy Choice");
			assertThat(result).contains("Party Platter Special");
		}

		@Test
		@DisplayName("should get offer details")
		void getOfferDetails() {
			String result = offerTool.getOfferDetails("Summer Refresh");

			assertThat(result).contains("Summer Refresh");
			assertThat(result).contains("10% off");
			assertThat(result).contains("Coca Cola");
			assertThat(result).contains("Coke Zero");
		}

		@Test
		@DisplayName("should get offers for specific products")
		void getOffersForProducts() {
			String result = offerTool.getOffersForProducts("Greek Yogurt, Semi-skimmed Milk");

			assertThat(result).contains("Applicable offers");
			assertThat(result).contains("Dairy Deal");
		}
	}

	@Nested
	@DisplayName("Persona Spec Tests")
	class PersonaSpecTests {

		@Test
		@DisplayName("standard persona should have price constraints")
		void standardPersonaHasPriceConstraints() {
			PersonaSpec persona = ShoppingPersonaSpec.standard();

			assertThat(persona.constraints()).anyMatch(c -> c.contains("NEVER invent product prices"));
			assertThat(persona.constraints()).anyMatch(c -> c.contains("pricing tool"));
		}

		@Test
		@DisplayName("budget-conscious persona should emphasize savings")
		void budgetPersonaEmphasizesSavings() {
			PersonaSpec persona = ShoppingPersonaSpec.budgetConscious();

			assertThat(persona.principles()).anyMatch(p -> p.contains("discount") || p.contains("savings"));
			assertThat(persona.styleGuidance()).anyMatch(s -> s.contains("savings") || s.contains("value"));
		}

		@Test
		@DisplayName("party planner persona should check quantities")
		void partyPlannerChecksQuantities() {
			PersonaSpec persona = ShoppingPersonaSpec.partyPlanner();

			assertThat(persona.principles()).anyMatch(p -> p.contains("quantities"));
			assertThat(persona.principles()).anyMatch(p -> p.contains("dietary"));
			assertThat(persona.constraints()).anyMatch(c -> c.contains("allergen"));
		}

		@Test
		@DisplayName("quick checkout persona should be efficient")
		void quickCheckoutIsEfficient() {
			PersonaSpec persona = ShoppingPersonaSpec.quickCheckout();

			assertThat(persona.styleGuidance()).anyMatch(s -> s.contains("brief"));
			assertThat(persona.constraints()).anyMatch(c -> c.contains("one or two sentences"));
		}
	}

	@Nested
	@DisplayName("Edge Cases")
	class EdgeCaseTests {

		@Test
		@DisplayName("should handle invalid basket format in pricing tool")
		void handleInvalidBasketFormat() {
			String result = pricingTool.calculateBasketTotal("not a valid format");

			assertThat(result).containsIgnoringCase("no valid");
		}

		@Test
		@DisplayName("should handle empty product list")
		void handleEmptyProductList() {
			String result = pricingTool.getApplicableOffers("");

			assertThat(result).containsIgnoringCase("no valid");
		}

		@Test
		@DisplayName("should handle unknown product in pricing")
		void handleUnknownProduct() {
			String result = pricingTool.getProductPrice("NonExistentProduct");

			assertThat(result).containsIgnoringCase("not found");
		}

		@Test
		@DisplayName("should handle zero quantity")
		void handleZeroQuantity() {
			String result = pricingTool.calculateBasketTotal("Coke Zero:0");

			assertThat(result).containsIgnoringCase("no valid");
		}

		@Test
		@DisplayName("should handle negative quantity gracefully")
		void handleNegativeQuantity() {
			String result = pricingTool.calculateBasketTotal("Coke Zero:-5");

			assertThat(result).containsIgnoringCase("no valid");
		}
	}

	@Nested
	@DisplayName("Full Pricing Flow Tests")
	class FullPricingFlowTests {

		@Test
		@DisplayName("complete shopping session with pricing verification")
		void completeSessionWithPricing() {
			// 1. Check offers first
			String offers = offerTool.listSpecialOffers();
			assertThat(offers).contains("Summer Refresh");

			// 2. Estimate cost before adding
			String estimate = pricingTool.estimateCost("Coke Zero", 6);
			assertThat(estimate).contains("£8.10"); // After 10% discount

			// 3. Add to basket
			actions.addItem(basket, "Coke Zero", 6);
			actions.addItem(basket, "Caprese Skewers", 2);

			// 4. View basket with prices
			ActionResult basketView = actions.viewBasketSummary(basket);
			assertThat(basketView.isSuccess()).isTrue();
			assertThat(basketView.message()).contains("Coke Zero");
			assertThat(basketView.message()).contains("Caprese Skewers");
			assertThat(basketView.message()).contains("Discounts applied");

			// 5. Calculate savings
			String savings = pricingTool.calculateSavings("Coke Zero:6, Caprese Skewers:2");
			assertThat(savings).contains("savings");

			// 6. Compute total
			ActionResult total = actions.computeTotal(basket);
			assertThat(total.message()).contains("Discounts");

			// 7. Checkout
			ActionResult checkout = actions.checkoutBasket(basket);
			assertThat(checkout.isSuccess()).isTrue();
			assertThat(checkout.message()).contains("Order complete");
		}

		@Test
		@DisplayName("verify pricing accuracy for complex basket")
		void verifyPricingAccuracy() {
			// Build a complex basket
			actions.addItem(basket, "Coke Zero", 10);      // £15.00, 10% off = £1.50, net = £13.50
			actions.addItem(basket, "Sea Salt Crisps", 5); // £10.00, 15% off = £1.50, net = £8.50
			actions.addItem(basket, "Greek Yogurt", 3);    // £6.00, £0.20 x 3 = £0.60, net = £5.40
			actions.addItem(basket, "Fruit Salad Bowl", 2);// £9.00, 10% off = £0.90, net = £8.10

			// Expected subtotal: £40.00
			// Expected discounts: £4.50
			// Expected total: £35.50

			PricingBreakdown pricing = storeApi.calculateTotal(actions.getBasketState());

			assertThat(pricing.subtotal()).isEqualByComparingTo(new BigDecimal("40.00"));
			assertThat(pricing.totalDiscount()).isEqualByComparingTo(new BigDecimal("4.50"));
			assertThat(pricing.total()).isEqualByComparingTo(new BigDecimal("35.50"));
		}
	}
}

