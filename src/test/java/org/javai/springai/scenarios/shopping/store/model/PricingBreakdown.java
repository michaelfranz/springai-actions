package org.javai.springai.scenarios.shopping.store.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Complete pricing breakdown for a basket.
 *
 * @param items Line items with individual pricing
 * @param subtotal Total before discounts
 * @param discounts Applied discounts
 * @param totalDiscount Sum of all discounts
 * @param total Final total after discounts
 */
public record PricingBreakdown(
		List<LineItem> items,
		BigDecimal subtotal,
		List<AppliedDiscount> discounts,
		BigDecimal totalDiscount,
		BigDecimal total
) {

	/**
	 * Create an empty pricing breakdown.
	 */
	public static PricingBreakdown empty() {
		return new PricingBreakdown(
				List.of(),
				BigDecimal.ZERO,
				List.of(),
				BigDecimal.ZERO,
				BigDecimal.ZERO
		);
	}
}

