package org.javai.springai.scenarios.shopping.store.model;

import java.math.BigDecimal;

/**
 * Represents a line item in the basket with pricing.
 *
 * @param product The product
 * @param quantity Quantity in basket
 * @param unitPrice Price per unit
 * @param lineTotal Total before discounts (quantity × unitPrice)
 */
public record LineItem(
		Product product,
		int quantity,
		BigDecimal unitPrice,
		BigDecimal lineTotal
) {

	/**
	 * Create a line item from a product and quantity.
	 */
	public static LineItem of(Product product, int quantity) {
		BigDecimal lineTotal = product.unitPrice().multiply(BigDecimal.valueOf(quantity));
		return new LineItem(product, quantity, product.unitPrice(), lineTotal);
	}
}

