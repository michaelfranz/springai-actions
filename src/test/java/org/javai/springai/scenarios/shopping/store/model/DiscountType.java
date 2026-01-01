package org.javai.springai.scenarios.shopping.store.model;

/**
 * Types of discounts that can be applied.
 */
public enum DiscountType {
	/**
	 * Percentage off the price (e.g., 10% off).
	 */
	PERCENTAGE,

	/**
	 * Fixed amount off the price (e.g., £1 off).
	 */
	FIXED_AMOUNT,

	/**
	 * Buy X get Y free (e.g., buy 2 get 1 free).
	 */
	BUY_X_GET_Y
}

