package org.javai.springai.scenarios.shopping.store.model;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Represents a special offer or promotion.
 *
 * @param offerId Unique offer identifier
 * @param name Short name for the offer
 * @param description Human-readable description
 * @param applicableSkus Specific product SKUs this applies to (empty for category-wide)
 * @param applicableCategory Category this applies to (null for SKU-specific)
 * @param type Type of discount
 * @param discountValue The discount value (percentage or fixed amount)
 */
public record SpecialOffer(
		String offerId,
		String name,
		String description,
		Set<String> applicableSkus,
		String applicableCategory,
		DiscountType type,
		BigDecimal discountValue
) {

	/**
	 * Check if this offer applies to a specific product.
	 */
	public boolean appliesTo(Product product) {
		// Check SKU-specific offers
		if (!applicableSkus.isEmpty() && applicableSkus.contains(product.sku())) {
			return true;
		}
		// Check category-wide offers
		return applicableCategory != null && applicableCategory.equalsIgnoreCase(product.category());
	}

	/**
	 * Calculate the discount amount for a given line total.
	 */
	public BigDecimal calculateDiscount(BigDecimal lineTotal, int quantity) {
		return switch (type) {
			case PERCENTAGE -> lineTotal.multiply(discountValue).divide(BigDecimal.valueOf(100));
			case FIXED_AMOUNT -> discountValue.multiply(BigDecimal.valueOf(quantity));
			case BUY_X_GET_Y -> 
				// BUY_X_GET_Y requires unit price context; handled in PricingService
				BigDecimal.ZERO;
		};
	}
}

