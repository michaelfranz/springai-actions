package org.javai.springai.scenarios.shopping.store.model;

import java.math.BigDecimal;

/**
 * Represents a single item in a mission plan.
 *
 * @param product The product to include
 * @param quantity Recommended quantity
 * @param rationale Explanation for the quantity (e.g., "2 per person")
 * @param lineTotal Total cost for this line item
 */
public record MissionItem(
		Product product,
		int quantity,
		String rationale,
		BigDecimal lineTotal
) {

	/**
	 * Create a mission item with automatic line total calculation.
	 */
	public static MissionItem of(Product product, int quantity, String rationale) {
		BigDecimal total = product.unitPrice().multiply(BigDecimal.valueOf(quantity));
		return new MissionItem(product, quantity, rationale, total);
	}

	/**
	 * Create a mission item with a specific line total (for discounted items).
	 */
	public static MissionItem withTotal(Product product, int quantity, String rationale, BigDecimal lineTotal) {
		return new MissionItem(product, quantity, rationale, lineTotal);
	}

	/**
	 * Get the product SKU.
	 */
	public String sku() {
		return product.sku();
	}

	/**
	 * Get the product name.
	 */
	public String productName() {
		return product.name();
	}

	/**
	 * Get the unit price.
	 */
	public BigDecimal unitPrice() {
		return product.unitPrice();
	}

	/**
	 * Get the category.
	 */
	public String category() {
		return product.category();
	}
}

