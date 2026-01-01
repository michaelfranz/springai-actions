package org.javai.springai.scenarios.shopping.store.model;

import java.util.List;

/**
 * Result of checking product availability.
 */
public sealed interface AvailabilityResult {

	/**
	 * Product is fully available in the requested quantity.
	 *
	 * @param quantity The available quantity
	 * @param lowStock Whether stock is running low
	 */
	record Available(int quantity, boolean lowStock) implements AvailabilityResult {}

	/**
	 * Product is partially available (less than requested).
	 *
	 * @param available The quantity actually available
	 * @param requested The quantity that was requested
	 * @param alternatives Similar products that might satisfy the remaining need
	 */
	record PartiallyAvailable(int available, int requested, List<Product> alternatives) implements AvailabilityResult {}

	/**
	 * Product is completely out of stock.
	 *
	 * @param alternatives Similar products that are in stock
	 */
	record OutOfStock(List<Product> alternatives) implements AvailabilityResult {}

	/**
	 * Product has been discontinued and will not be restocked.
	 *
	 * @param alternatives Replacement products
	 */
	record Discontinued(List<Product> alternatives) implements AvailabilityResult {}

	/**
	 * Product was not found in the catalog.
	 *
	 * @param searchTerm The term that was searched for
	 * @param suggestions Products that might match what was intended
	 */
	record NotFound(String searchTerm, List<Product> suggestions) implements AvailabilityResult {}
}

