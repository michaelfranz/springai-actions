package org.javai.springai.scenarios.shopping.store;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.javai.springai.scenarios.shopping.store.model.AvailabilityResult;
import org.javai.springai.scenarios.shopping.store.model.Product;
import org.javai.springai.scenarios.shopping.store.model.StockLevel;

/**
 * Service for managing inventory and stock levels.
 * Tracks available quantities and supports reservation/release for basket operations.
 */
public class InventoryService {

	private final ProductCatalog catalog;
	private final Map<String, StockLevel> stockLevels = new HashMap<>();
	private final Map<String, Integer> reservations = new HashMap<>();

	public InventoryService(ProductCatalog catalog) {
		this.catalog = catalog;
		initializeStockLevels();
	}

	/**
	 * Get the current stock level for a product.
	 */
	public Optional<StockLevel> getStockLevel(String sku) {
		return Optional.ofNullable(stockLevels.get(sku));
	}

	/**
	 * Check availability for a product by name and quantity.
	 */
	public AvailabilityResult checkAvailability(String productName, int requestedQuantity) {
		Optional<Product> productOpt = catalog.findByName(productName);

		if (productOpt.isEmpty()) {
			// Product not found - suggest similar products
			List<Product> suggestions = catalog.searchProducts(productName).stream()
					.limit(3)
					.toList();
			return new AvailabilityResult.NotFound(productName, suggestions);
		}

		Product product = productOpt.get();
		return checkAvailabilityBySku(product.sku(), requestedQuantity);
	}

	/**
	 * Check availability for a product by SKU and quantity.
	 */
	public AvailabilityResult checkAvailabilityBySku(String sku, int requestedQuantity) {
		Optional<Product> productOpt = catalog.findBySku(sku);
		if (productOpt.isEmpty()) {
			return new AvailabilityResult.NotFound(sku, List.of());
		}

		Product product = productOpt.get();
		StockLevel stock = stockLevels.get(sku);

		if (stock == null) {
			// No stock record means unlimited availability (shouldn't happen with proper init)
			return new AvailabilityResult.Available(requestedQuantity, false);
		}

		if (stock.discontinued()) {
			List<Product> alternatives = findAlternatives(product);
			return new AvailabilityResult.Discontinued(alternatives);
		}

		int available = getAvailableQuantity(sku);

		if (available == 0) {
			List<Product> alternatives = findAlternatives(product);
			return new AvailabilityResult.OutOfStock(alternatives);
		}

		if (available < requestedQuantity) {
			List<Product> alternatives = findAlternatives(product);
			return new AvailabilityResult.PartiallyAvailable(available, requestedQuantity, alternatives);
		}

		// Fully available - check if low stock warning needed
		boolean lowStock = stock.isLowStock() || (available - requestedQuantity) <= stock.lowStockThreshold();
		return new AvailabilityResult.Available(requestedQuantity, lowStock);
	}

	/**
	 * Reserve stock for a basket (reduces available quantity).
	 * Returns true if reservation was successful.
	 */
	public boolean reserveStock(String sku, int quantity) {
		int available = getAvailableQuantity(sku);
		if (available < quantity) {
			return false;
		}
		reservations.merge(sku, quantity, Integer::sum);
		return true;
	}

	/**
	 * Release previously reserved stock (increases available quantity).
	 */
	public void releaseStock(String sku, int quantity) {
		reservations.computeIfPresent(sku, (k, current) -> {
			int newValue = current - quantity;
			return newValue <= 0 ? null : newValue;
		});
	}

	/**
	 * Commit reservations (permanently reduce stock after checkout).
	 */
	public void commitReservations(Map<String, Integer> basketItems) {
		for (Map.Entry<String, Integer> entry : basketItems.entrySet()) {
			String sku = entry.getKey();
			int quantity = entry.getValue();

			StockLevel current = stockLevels.get(sku);
			if (current != null) {
				stockLevels.put(sku, current.withReducedQuantity(quantity));
			}
			// Clear reservation
			releaseStock(sku, quantity);
		}
	}

	/**
	 * Find alternative products that are in stock.
	 */
	public List<Product> findAlternatives(Product product) {
		return catalog.findSimilar(product, 5).stream()
				.filter(p -> {
					StockLevel stock = stockLevels.get(p.sku());
					return stock != null && !stock.isOutOfStock();
				})
				.limit(3)
				.toList();
	}

	/**
	 * Find alternative products by product name.
	 */
	public List<Product> findAlternatives(String productName) {
		return catalog.findByName(productName)
				.map(this::findAlternatives)
				.orElse(List.of());
	}

	/**
	 * Get available quantity (stock minus reservations).
	 */
	public int getAvailableQuantity(String sku) {
		StockLevel stock = stockLevels.get(sku);
		if (stock == null || stock.discontinued()) {
			return 0;
		}
		int reserved = reservations.getOrDefault(sku, 0);
		return Math.max(0, stock.quantityAvailable() - reserved);
	}

	/**
	 * Reset all stock levels to initial state (for testing).
	 */
	public void resetStock() {
		reservations.clear();
		initializeStockLevels();
	}

	private void initializeStockLevels() {
		stockLevels.clear();

		// Beverages - mostly well stocked
		stockLevels.put("BEV-001", new StockLevel("BEV-001", 100, 10, false)); // Coca Cola
		stockLevels.put("BEV-002", new StockLevel("BEV-002", 50, 10, false));  // Coke Zero
		stockLevels.put("BEV-003", new StockLevel("BEV-003", 8, 10, false));   // Sparkling Water - LOW STOCK
		stockLevels.put("BEV-004", new StockLevel("BEV-004", 30, 5, false));   // Orange Juice
		stockLevels.put("BEV-005", new StockLevel("BEV-005", 25, 5, false));   // Lemonade

		// Snacks - varied stock
		stockLevels.put("SNK-001", new StockLevel("SNK-001", 40, 10, false));  // Sea Salt Crisps
		stockLevels.put("SNK-002", new StockLevel("SNK-002", 35, 10, false));  // Cheese & Onion Crisps
		stockLevels.put("SNK-003", new StockLevel("SNK-003", 0, 5, false));    // Mixed Nuts - OUT OF STOCK
		stockLevels.put("SNK-004", new StockLevel("SNK-004", 20, 5, false));   // Hummus
		stockLevels.put("SNK-005", new StockLevel("SNK-005", 15, 5, false));   // Guacamole
		stockLevels.put("SNK-006", new StockLevel("SNK-006", 30, 10, false));  // Salted Pretzels

		// Dairy
		stockLevels.put("DAI-001", new StockLevel("DAI-001", 50, 10, false));  // Milk
		stockLevels.put("DAI-002", new StockLevel("DAI-002", 25, 5, false));   // Cheddar
		stockLevels.put("DAI-003", new StockLevel("DAI-003", 20, 5, false));   // Greek Yogurt

		// Produce
		stockLevels.put("PRD-001", new StockLevel("PRD-001", 10, 3, false));   // Fruit Salad
		stockLevels.put("PRD-002", new StockLevel("PRD-002", 8, 3, false));    // Vegetable Crudités
		stockLevels.put("PRD-003", new StockLevel("PRD-003", 30, 10, false));  // Cherry Tomatoes

		// Party items
		stockLevels.put("PTY-001", new StockLevel("PTY-001", 15, 5, false));   // Caprese Skewers
		stockLevels.put("PTY-002", new StockLevel("PTY-002", 12, 4, false));   // Bruschetta
		stockLevels.put("PTY-003", new StockLevel("PTY-003", 8, 3, false));    // Cheese Board
		stockLevels.put("PTY-004", new StockLevel("PTY-004", 10, 3, false));   // Hummus Platter
		stockLevels.put("PTY-005", new StockLevel("PTY-005", 20, 5, false));   // Crisps Party Pack

		// Bakery
		stockLevels.put("BAK-001", new StockLevel("BAK-001", 30, 10, false));  // Baguette
		stockLevels.put("BAK-002", new StockLevel("BAK-002", 20, 5, false));   // Croissants
		stockLevels.put("BAK-003", new StockLevel("BAK-003", 15, 5, false));   // Brownie Bites

		// Discontinued product
		stockLevels.put("DIS-001", new StockLevel("DIS-001", 0, 0, true));     // Discontinued Soda
	}
}

