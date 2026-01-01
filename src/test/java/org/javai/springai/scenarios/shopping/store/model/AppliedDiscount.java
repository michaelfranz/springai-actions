package org.javai.springai.scenarios.shopping.store.model;

import java.math.BigDecimal;

/**
 * Represents a discount that has been applied to the basket.
 *
 * @param offer The special offer that was applied
 * @param productSku The product SKU the discount was applied to (or null for basket-wide)
 * @param discountAmount The monetary value of the discount
 */
public record AppliedDiscount(
		SpecialOffer offer,
		String productSku,
		BigDecimal discountAmount
) {
}

