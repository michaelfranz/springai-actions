package org.javai.springai.scenarios.shopping.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.javai.springai.scenarios.shopping.store.model.AvailabilityResult;
import org.javai.springai.scenarios.shopping.store.model.PricingBreakdown;
import org.javai.springai.scenarios.shopping.store.model.Product;
import org.javai.springai.scenarios.shopping.store.model.SpecialOffer;
import org.javai.springai.scenarios.shopping.store.model.StockLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the mock store infrastructure.
 */
class MockStoreApiTest {

	private MockStoreApi storeApi;

	@BeforeEach
	void setUp() {
		storeApi = new MockStoreApi();
	}

	@Nested
	@DisplayName("Product Catalog Tests")
	class ProductCatalogTests {

		@Test
		@DisplayName("should find product by exact name")
		void findProductByExactName() {
			Optional<Product> product = storeApi.findProduct("Coke Zero");

			assertThat(product).isPresent();
			assertThat(product.get().sku()).isEqualTo("BEV-002");
			assertThat(product.get().name()).isEqualTo("Coke Zero");
		}

		@Test
		@DisplayName("should find product by partial name (case insensitive)")
		void findProductByPartialName() {
			Optional<Product> product = storeApi.findProduct("coke");

			assertThat(product).isPresent();
			assertThat(product.get().name()).containsIgnoringCase("coke");
		}

		@Test
		@DisplayName("should find product by SKU")
		void findProductBySku() {
			Optional<Product> product = storeApi.findProductBySku("SNK-001");

			assertThat(product).isPresent();
			assertThat(product.get().name()).isEqualTo("Sea Salt Crisps");
		}

		@Test
		@DisplayName("should return empty for non-existent product")
		void findNonExistentProduct() {
			Optional<Product> product = storeApi.findProduct("NonExistentProduct");

			assertThat(product).isEmpty();
		}

		@Test
		@DisplayName("should get products by category")
		void getProductsByCategory() {
			List<Product> beverages = storeApi.getProductsByCategory("beverages");

			assertThat(beverages).isNotEmpty();
			assertThat(beverages).allMatch(p -> p.category().equalsIgnoreCase("beverages"));
			assertThat(beverages).hasSizeGreaterThanOrEqualTo(5);
		}

		@Test
		@DisplayName("should get all categories")
		void getCategories() {
			Set<String> categories = storeApi.getCategories();

			assertThat(categories).contains("beverages", "snacks", "dairy", "produce", "party", "bakery");
		}

		@Test
		@DisplayName("should find vegetarian products")
		void findVegetarianProducts() {
			List<Product> vegetarian = storeApi.getProductsByDiet("vegetarian");

			assertThat(vegetarian).isNotEmpty();
			assertThat(vegetarian).allMatch(p -> p.dietaryFlags().contains("vegetarian"));
		}

		@Test
		@DisplayName("should find products safe for nut allergy")
		void findNutFreeProducts() {
			List<Product> nutFree = storeApi.getSafeProducts(Set.of("peanuts", "tree nuts"));

			assertThat(nutFree).isNotEmpty();
			assertThat(nutFree).noneMatch(p ->
					p.allergens().contains("peanuts") || p.allergens().contains("tree nuts"));
		}

		@Test
		@DisplayName("should search products by query")
		void searchProducts() {
			List<Product> results = storeApi.searchProducts("party");

			assertThat(results).isNotEmpty();
			assertThat(results).anyMatch(p ->
					p.name().toLowerCase().contains("party") ||
					p.category().equalsIgnoreCase("party") ||
					p.description().toLowerCase().contains("party"));
		}

		@Test
		@DisplayName("catalog should contain approximately 25 products")
		void catalogSize() {
			List<Product> allProducts = storeApi.getAllProducts();

			assertThat(allProducts).hasSizeGreaterThanOrEqualTo(25);
		}
	}

	@Nested
	@DisplayName("Inventory Service Tests")
	class InventoryServiceTests {

		@Test
		@DisplayName("should return available for in-stock product")
		void checkAvailableProduct() {
			AvailabilityResult result = storeApi.checkAvailability("Coca Cola", 5);

			assertThat(result).isInstanceOf(AvailabilityResult.Available.class);
			AvailabilityResult.Available available = (AvailabilityResult.Available) result;
			assertThat(available.quantity()).isEqualTo(5);
		}

		@Test
		@DisplayName("should flag low stock when quantity is near threshold")
		void checkLowStockProduct() {
			// Sparkling Water has 8 units with threshold of 10
			AvailabilityResult result = storeApi.checkAvailability("Sparkling Water", 3);

			assertThat(result).isInstanceOf(AvailabilityResult.Available.class);
			AvailabilityResult.Available available = (AvailabilityResult.Available) result;
			assertThat(available.lowStock()).isTrue();
		}

		@Test
		@DisplayName("should return out of stock for zero-quantity product")
		void checkOutOfStockProduct() {
			// Mixed Nuts has 0 stock
			AvailabilityResult result = storeApi.checkAvailability("Mixed Nuts", 5);

			assertThat(result).isInstanceOf(AvailabilityResult.OutOfStock.class);
			AvailabilityResult.OutOfStock outOfStock = (AvailabilityResult.OutOfStock) result;
			assertThat(outOfStock.alternatives()).isNotEmpty();
		}

		@Test
		@DisplayName("should return discontinued for discontinued product")
		void checkDiscontinuedProduct() {
			AvailabilityResult result = storeApi.checkAvailability("Discontinued Soda", 1);

			assertThat(result).isInstanceOf(AvailabilityResult.Discontinued.class);
		}

		@Test
		@DisplayName("should return partially available when requesting more than available")
		void checkPartialAvailability() {
			// Sparkling Water has 8 units
			AvailabilityResult result = storeApi.checkAvailability("Sparkling Water", 15);

			assertThat(result).isInstanceOf(AvailabilityResult.PartiallyAvailable.class);
			AvailabilityResult.PartiallyAvailable partial = (AvailabilityResult.PartiallyAvailable) result;
			assertThat(partial.available()).isEqualTo(8);
			assertThat(partial.requested()).isEqualTo(15);
		}

		@Test
		@DisplayName("should return not found for unknown product")
		void checkUnknownProduct() {
			AvailabilityResult result = storeApi.checkAvailability("FakeProduct123", 1);

			assertThat(result).isInstanceOf(AvailabilityResult.NotFound.class);
		}

		@Test
		@DisplayName("should get stock level by SKU")
		void getStockLevel() {
			Optional<StockLevel> stock = storeApi.getStockLevel("BEV-002");

			assertThat(stock).isPresent();
			assertThat(stock.get().quantityAvailable()).isEqualTo(50);
			assertThat(stock.get().lowStockThreshold()).isEqualTo(10);
		}

		@Test
		@DisplayName("should reserve and release stock")
		void reserveAndReleaseStock() {
			String sku = "BEV-001"; // Coca Cola with 100 units
			int initialAvailable = storeApi.getAvailableQuantity(sku);

			// Reserve some stock
			boolean reserved = storeApi.reserveStock(sku, 10);
			assertThat(reserved).isTrue();
			assertThat(storeApi.getAvailableQuantity(sku)).isEqualTo(initialAvailable - 10);

			// Release the reservation
			storeApi.releaseStock(sku, 10);
			assertThat(storeApi.getAvailableQuantity(sku)).isEqualTo(initialAvailable);
		}

		@Test
		@DisplayName("should fail to reserve more than available")
		void reserveMoreThanAvailable() {
			String sku = "BEV-003"; // Sparkling Water with 8 units
			boolean reserved = storeApi.reserveStock(sku, 100);

			assertThat(reserved).isFalse();
		}

		@Test
		@DisplayName("should find alternatives for product")
		void findAlternatives() {
			// Mixed Nuts is out of stock
			List<Product> alternatives = storeApi.getAlternatives("Mixed Nuts");

			// Should suggest other snacks that are in stock
			assertThat(alternatives).isNotEmpty();
			assertThat(alternatives).allMatch(p -> p.category().equalsIgnoreCase("snacks"));
		}
	}

	@Nested
	@DisplayName("Pricing Service Tests")
	class PricingServiceTests {

		@Test
		@DisplayName("should get unit price for product")
		void getUnitPrice() {
			Optional<BigDecimal> price = storeApi.getPrice("BEV-002");

			assertThat(price).isPresent();
			assertThat(price.get()).isEqualByComparingTo(new BigDecimal("1.50"));
		}

		@Test
		@DisplayName("should get price by product name")
		void getPriceByName() {
			Optional<BigDecimal> price = storeApi.getPriceByName("Coke Zero");

			assertThat(price).isPresent();
			assertThat(price.get()).isEqualByComparingTo(new BigDecimal("1.50"));
		}

		@Test
		@DisplayName("should calculate basket total")
		void calculateBasketTotal() {
			Map<String, Integer> basket = Map.of(
					"BEV-002", 2,  // Coke Zero £1.50 x 2 = £3.00
					"SNK-001", 1   // Sea Salt Crisps £2.00 x 1 = £2.00
			);

			PricingBreakdown breakdown = storeApi.calculateTotal(basket);

			assertThat(breakdown.items()).hasSize(2);
			// Subtotal should be £5.00
			assertThat(breakdown.subtotal()).isEqualByComparingTo(new BigDecimal("5.00"));
		}

		@Test
		@DisplayName("should calculate basket total by product name")
		void calculateBasketTotalByName() {
			Map<String, Integer> basket = Map.of(
					"Coke Zero", 2,
					"Sea Salt Crisps", 1
			);

			PricingBreakdown breakdown = storeApi.calculateTotalByName(basket);

			assertThat(breakdown.items()).hasSize(2);
			assertThat(breakdown.subtotal()).isEqualByComparingTo(new BigDecimal("5.00"));
		}

		@Test
		@DisplayName("should apply percentage discount")
		void applyPercentageDiscount() {
			// Coke Zero has 10% off (Summer Refresh offer)
			Map<String, Integer> basket = Map.of("BEV-002", 10); // £1.50 x 10 = £15.00

			PricingBreakdown breakdown = storeApi.calculateTotal(basket);

			assertThat(breakdown.subtotal()).isEqualByComparingTo(new BigDecimal("15.00"));
			assertThat(breakdown.discounts()).isNotEmpty();

			// 10% off £15.00 = £1.50 discount
			BigDecimal expectedDiscount = new BigDecimal("1.50");
			assertThat(breakdown.totalDiscount()).isEqualByComparingTo(expectedDiscount);
			assertThat(breakdown.total()).isEqualByComparingTo(new BigDecimal("13.50"));
		}

		@Test
		@DisplayName("should apply category-wide discount")
		void applyCategoryDiscount() {
			// Snacks have 15% off (Party Pack offer)
			Map<String, Integer> basket = Map.of("SNK-001", 2); // Sea Salt Crisps £2.00 x 2 = £4.00

			PricingBreakdown breakdown = storeApi.calculateTotal(basket);

			// 15% off £4.00 = £0.60 discount
			assertThat(breakdown.totalDiscount()).isEqualByComparingTo(new BigDecimal("0.60"));
			assertThat(breakdown.total()).isEqualByComparingTo(new BigDecimal("3.40"));
		}

		@Test
		@DisplayName("should get all active offers")
		void getActiveOffers() {
			List<SpecialOffer> offers = storeApi.getActiveOffers();

			assertThat(offers).hasSizeGreaterThanOrEqualTo(5);
			assertThat(offers).anyMatch(o -> o.name().equals("Summer Refresh"));
			assertThat(offers).anyMatch(o -> o.name().equals("Party Pack"));
		}

		@Test
		@DisplayName("should get applicable offers for basket")
		void getApplicableOffers() {
			Set<String> basketSkus = Set.of("BEV-002", "SNK-001");

			List<SpecialOffer> applicable = storeApi.getApplicableOffers(basketSkus);

			assertThat(applicable).isNotEmpty();
			// Should include Summer Refresh (for Coke Zero) and Party Pack (for snacks)
			assertThat(applicable).anyMatch(o -> o.name().equals("Summer Refresh"));
			assertThat(applicable).anyMatch(o -> o.name().equals("Party Pack"));
		}

		@Test
		@DisplayName("should return empty breakdown for empty basket")
		void emptyBasket() {
			PricingBreakdown breakdown = storeApi.calculateTotal(Map.of());

			assertThat(breakdown.items()).isEmpty();
			assertThat(breakdown.subtotal()).isEqualByComparingTo(BigDecimal.ZERO);
			assertThat(breakdown.total()).isEqualByComparingTo(BigDecimal.ZERO);
		}
	}

	@Nested
	@DisplayName("Combined Operations Tests")
	class CombinedOperationsTests {

		@Test
		@DisplayName("should check if product can be added to basket")
		void canAddToBasket() {
			MockStoreApi.AddToBasketResult result = storeApi.canAddToBasket("Coke Zero", 5);

			assertThat(result.canAdd()).isTrue();
			assertThat(result.product()).isNotNull();
			assertThat(result.product().name()).isEqualTo("Coke Zero");
			assertThat(result.availableQuantity()).isEqualTo(5);
		}

		@Test
		@DisplayName("should indicate low stock warning")
		void canAddToBasketLowStock() {
			MockStoreApi.AddToBasketResult result = storeApi.canAddToBasket("Sparkling Water", 3);

			assertThat(result.canAdd()).isTrue();
			assertThat(result.message()).contains("low");
		}

		@Test
		@DisplayName("should indicate out of stock")
		void canAddToBasketOutOfStock() {
			MockStoreApi.AddToBasketResult result = storeApi.canAddToBasket("Mixed Nuts", 5);

			assertThat(result.canAdd()).isFalse();
			assertThat(result.message()).contains("Out of stock");
			assertThat(result.alternatives()).isNotEmpty();
		}

		@Test
		@DisplayName("should indicate product not found")
		void canAddToBasketNotFound() {
			MockStoreApi.AddToBasketResult result = storeApi.canAddToBasket("NonExistent", 1);

			assertThat(result.canAdd()).isFalse();
			assertThat(result.message()).contains("not found");
		}

		@Test
		@DisplayName("should reset store to initial state")
		void resetStore() {
			// Reserve some stock
			storeApi.reserveStock("BEV-001", 50);
			int afterReserve = storeApi.getAvailableQuantity("BEV-001");

			// Reset
			storeApi.reset();
			int afterReset = storeApi.getAvailableQuantity("BEV-001");

			assertThat(afterReserve).isLessThan(afterReset);
			assertThat(afterReset).isEqualTo(100); // Initial stock level
		}
	}

	@Nested
	@DisplayName("End-to-End Shopping Flow Tests")
	class EndToEndTests {

		@Test
		@DisplayName("should complete a full shopping flow")
		void fullShoppingFlow() {
			// 1. Search for products
			List<Product> searchResults = storeApi.searchProducts("coke");
			assertThat(searchResults).isNotEmpty();

			// 2. Check availability
			AvailabilityResult availability = storeApi.checkAvailability("Coke Zero", 6);
			assertThat(availability).isInstanceOf(AvailabilityResult.Available.class);

			// 3. Reserve stock
			Optional<Product> product = storeApi.findProduct("Coke Zero");
			assertThat(product).isPresent();
			boolean reserved = storeApi.reserveStock(product.get().sku(), 6);
			assertThat(reserved).isTrue();

			// 4. Add more items
			storeApi.reserveStock("SNK-001", 2); // Sea Salt Crisps
			storeApi.reserveStock("PTY-001", 1); // Caprese Skewers

			// 5. Calculate total
			Map<String, Integer> basket = Map.of(
					"BEV-002", 6,  // Coke Zero
					"SNK-001", 2,  // Sea Salt Crisps
					"PTY-001", 1   // Caprese Skewers
			);
			PricingBreakdown pricing = storeApi.calculateTotal(basket);

			assertThat(pricing.items()).hasSize(3);
			assertThat(pricing.subtotal()).isPositive();
			assertThat(pricing.total()).isPositive();
			assertThat(pricing.total()).isLessThanOrEqualTo(pricing.subtotal()); // Discounts applied

			// 6. Checkout (commit reservations)
			storeApi.commitReservations(basket);

			// 7. Verify stock reduced
			int remainingCokeZero = storeApi.getAvailableQuantity("BEV-002");
			assertThat(remainingCokeZero).isEqualTo(50 - 6); // Started with 50
		}

		@Test
		@DisplayName("should handle party shopping scenario with dietary requirements")
		void partyShoppingWithDietaryRequirements() {
			// Find vegetarian products safe for nut allergies
			List<Product> vegetarian = storeApi.getProductsByDiet("vegetarian");
			List<Product> nutFree = storeApi.getSafeProducts(Set.of("peanuts", "tree nuts"));

			// Find products that are both
			List<Product> suitable = vegetarian.stream()
					.filter(nutFree::contains)
					.filter(p -> {
						// Check if in stock
						AvailabilityResult avail = storeApi.checkAvailabilityBySku(p.sku(), 1);
						return avail instanceof AvailabilityResult.Available;
					})
					.toList();

			assertThat(suitable).isNotEmpty();

			// Verify the results include expected party items
			assertThat(suitable).anyMatch(p -> p.category().equalsIgnoreCase("party"));
			assertThat(suitable).anyMatch(p -> p.category().equalsIgnoreCase("produce"));
		}
	}
}

