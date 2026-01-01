package org.javai.springai.scenarios.shopping.store;

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

/**
 * Unified API façade for the mock store infrastructure.
 * Coordinates access to the product catalog, inventory, and pricing services.
 * <p>
 * This is the primary entry point for tools and actions that need to interact
 * with the store's backend systems.
 */
public class MockStoreApi {

	private final ProductCatalog catalog;
	private final InventoryService inventory;
	private final PricingService pricing;

	/**
	 * Create a MockStoreApi with default services.
	 */
	public MockStoreApi() {
		this.catalog = new ProductCatalog();
		this.inventory = new InventoryService(catalog);
		this.pricing = new PricingService(catalog);
	}

	/**
	 * Create a MockStoreApi with custom services (for testing).
	 */
	public MockStoreApi(ProductCatalog catalog, InventoryService inventory, PricingService pricing) {
		this.catalog = catalog;
		this.inventory = inventory;
		this.pricing = pricing;
	}

	// ========== CATALOG QUERIES ==========

	/**
	 * Search products by query string (matches name, description, category).
	 */
	public List<Product> searchProducts(String query) {
		return catalog.searchProducts(query);
	}

	/**
	 * Find a product by name.
	 */
	public Optional<Product> findProduct(String name) {
		return catalog.findByName(name);
	}

	/**
	 * Find a product by SKU.
	 */
	public Optional<Product> findProductBySku(String sku) {
		return catalog.findBySku(sku);
	}

	/**
	 * Get all products in a category.
	 */
	public List<Product> getProductsByCategory(String category) {
		return catalog.findByCategory(category);
	}

	/**
	 * Get all available categories.
	 */
	public Set<String> getCategories() {
		return catalog.getCategories();
	}

	/**
	 * Get products matching a dietary requirement (e.g., "vegetarian", "vegan").
	 */
	public List<Product> getProductsByDiet(String dietaryFlag) {
		return catalog.findByDietaryFlag(dietaryFlag);
	}

	/**
	 * Get products that are safe for someone with specific allergens.
	 */
	public List<Product> getSafeProducts(Set<String> excludeAllergens) {
		return catalog.findWithoutAllergens(excludeAllergens);
	}

	/**
	 * Get all products in the catalog.
	 */
	public List<Product> getAllProducts() {
		return catalog.getAllProducts();
	}

	// ========== INVENTORY QUERIES ==========

	/**
	 * Check availability for a product by name and quantity.
	 */
	public AvailabilityResult checkAvailability(String productName, int quantity) {
		return inventory.checkAvailability(productName, quantity);
	}

	/**
	 * Check availability for a product by SKU and quantity.
	 */
	public AvailabilityResult checkAvailabilityBySku(String sku, int quantity) {
		return inventory.checkAvailabilityBySku(sku, quantity);
	}

	/**
	 * Get the stock level for a product by SKU.
	 */
	public Optional<StockLevel> getStockLevel(String sku) {
		return inventory.getStockLevel(sku);
	}

	/**
	 * Get the available quantity for a product by SKU.
	 */
	public int getAvailableQuantity(String sku) {
		return inventory.getAvailableQuantity(sku);
	}

	/**
	 * Find alternative products that are in stock.
	 */
	public List<Product> getAlternatives(String productName) {
		return inventory.findAlternatives(productName);
	}

	/**
	 * Reserve stock for basket operations.
	 */
	public boolean reserveStock(String sku, int quantity) {
		return inventory.reserveStock(sku, quantity);
	}

	/**
	 * Release previously reserved stock.
	 */
	public void releaseStock(String sku, int quantity) {
		inventory.releaseStock(sku, quantity);
	}

	/**
	 * Commit basket reservations after checkout.
	 */
	public void commitReservations(Map<String, Integer> basketItems) {
		inventory.commitReservations(basketItems);
	}

	// ========== PRICING ==========

	/**
	 * Get the unit price for a product by SKU.
	 */
	public Optional<BigDecimal> getPrice(String sku) {
		return pricing.getUnitPrice(sku);
	}

	/**
	 * Get the unit price for a product by name.
	 */
	public Optional<BigDecimal> getPriceByName(String productName) {
		return catalog.findByName(productName)
				.flatMap(p -> pricing.getUnitPrice(p.sku()));
	}

	/**
	 * Calculate the complete pricing breakdown for a basket (by SKU).
	 */
	public PricingBreakdown calculateTotal(Map<String, Integer> basket) {
		return pricing.calculateBasketTotal(basket);
	}

	/**
	 * Calculate the complete pricing breakdown for a basket (by product name).
	 */
	public PricingBreakdown calculateTotalByName(Map<String, Integer> basketByName) {
		return pricing.calculateBasketTotalByName(basketByName);
	}

	/**
	 * Get all currently active special offers.
	 */
	public List<SpecialOffer> getActiveOffers() {
		return pricing.getActiveOffers();
	}

	/**
	 * Get offers applicable to the products in a basket.
	 */
	public List<SpecialOffer> getApplicableOffers(Set<String> basketSkus) {
		return pricing.getApplicableOffers(basketSkus);
	}

	/**
	 * Get a specific offer by ID.
	 */
	public Optional<SpecialOffer> getOffer(String offerId) {
		return pricing.getOffer(offerId);
	}

	// ========== COMBINED OPERATIONS ==========

	/**
	 * Check if a product can be added to basket with the given quantity.
	 * Returns a result indicating availability and any warnings.
	 */
	public AddToBasketResult canAddToBasket(String productName, int quantity) {
		Optional<Product> productOpt = catalog.findByName(productName);
		if (productOpt.isEmpty()) {
			List<Product> suggestions = catalog.searchProducts(productName).stream()
					.limit(3)
					.toList();
			return new AddToBasketResult(false, null, 0,
					"Product not found: " + productName, suggestions);
		}

		Product product = productOpt.get();
		AvailabilityResult availability = inventory.checkAvailabilityBySku(product.sku(), quantity);

		return switch (availability) {
			case AvailabilityResult.Available(int qty, boolean lowStock) -> {
				String warning = lowStock ? "Stock is running low" : null;
				yield new AddToBasketResult(true, product, qty, warning, List.of());
			}
			case AvailabilityResult.PartiallyAvailable(int available, int requested, List<Product> alts) ->
					new AddToBasketResult(false, product, available,
							String.format("Only %d available (requested %d)", available, requested), alts);
			case AvailabilityResult.OutOfStock(List<Product> alts) ->
					new AddToBasketResult(false, product, 0, "Out of stock", alts);
			case AvailabilityResult.Discontinued(List<Product> alts) ->
					new AddToBasketResult(false, product, 0, "Product discontinued", alts);
			case AvailabilityResult.NotFound(String term, List<Product> suggestions) ->
					new AddToBasketResult(false, null, 0, "Product not found: " + term, suggestions);
		};
	}

	/**
	 * Result of checking if a product can be added to basket.
	 */
	public record AddToBasketResult(
			boolean canAdd,
			Product product,
			int availableQuantity,
			String message,
			List<Product> alternatives
	) {}

	// ========== UTILITY ==========

	/**
	 * Reset the store to initial state (for testing).
	 */
	public void reset() {
		inventory.resetStock();
	}

	/**
	 * Get direct access to the catalog (for advanced queries).
	 */
	public ProductCatalog getCatalog() {
		return catalog;
	}

	/**
	 * Get direct access to inventory service (for advanced operations).
	 */
	public InventoryService getInventory() {
		return inventory;
	}

	/**
	 * Get direct access to pricing service (for advanced operations).
	 */
	public PricingService getPricing() {
		return pricing;
	}
}

