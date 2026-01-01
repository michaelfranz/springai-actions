package org.javai.springai.scenarios.shopping.store.model;

/**
 * Represents the stock level for a product.
 *
 * @param sku Product SKU
 * @param quantityAvailable Current available quantity
 * @param lowStockThreshold Threshold below which stock is considered low
 * @param discontinued Whether the product has been discontinued
 */
public record StockLevel(
		String sku,
		int quantityAvailable,
		int lowStockThreshold,
		boolean discontinued
) {

	/**
	 * Check if stock is at or below the low threshold.
	 */
	public boolean isLowStock() {
		return !discontinued && quantityAvailable > 0 && quantityAvailable <= lowStockThreshold;
	}

	/**
	 * Check if product is out of stock or discontinued.
	 */
	public boolean isOutOfStock() {
		return quantityAvailable == 0 || discontinued;
	}

	/**
	 * Create a new StockLevel with reduced quantity.
	 */
	public StockLevel withReducedQuantity(int amount) {
		return new StockLevel(sku, Math.max(0, quantityAvailable - amount), lowStockThreshold, discontinued);
	}

	/**
	 * Create a new StockLevel with increased quantity.
	 */
	public StockLevel withIncreasedQuantity(int amount) {
		return new StockLevel(sku, quantityAvailable + amount, lowStockThreshold, discontinued);
	}
}

