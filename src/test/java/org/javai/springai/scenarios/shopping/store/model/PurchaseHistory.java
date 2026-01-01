package org.javai.springai.scenarios.shopping.store.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents a customer's purchase history.
 *
 * @param customerId Customer identifier
 * @param orders List of past orders
 */
public record PurchaseHistory(
		String customerId,
		List<PastOrder> orders
) {

	/**
	 * Get the most frequently purchased product SKUs.
	 *
	 * @param limit Maximum number of products to return
	 * @return Map of SKU to purchase count, sorted by frequency
	 */
	public Map<String, Integer> getFrequentlyBoughtSkus(int limit) {
		Map<String, Integer> frequency = new HashMap<>();

		for (PastOrder order : orders) {
			for (Map.Entry<String, Integer> item : order.items().entrySet()) {
				frequency.merge(item.getKey(), item.getValue(), Integer::sum);
			}
		}

		return frequency.entrySet().stream()
				.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
				.limit(limit)
				.collect(Collectors.toMap(
						Map.Entry::getKey,
						Map.Entry::getValue,
						(a, b) -> a,
						java.util.LinkedHashMap::new
				));
	}

	/**
	 * Get the total amount spent across all orders.
	 */
	public BigDecimal getTotalSpent() {
		return orders.stream()
				.map(PastOrder::total)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	/**
	 * Get average order value.
	 */
	public BigDecimal getAverageOrderValue() {
		if (orders.isEmpty()) {
			return BigDecimal.ZERO;
		}
		return getTotalSpent().divide(BigDecimal.valueOf(orders.size()), 2, java.math.RoundingMode.HALF_UP);
	}

	/**
	 * Get the most recent orders.
	 *
	 * @param limit Maximum number of orders to return
	 */
	public List<PastOrder> getRecentOrders(int limit) {
		return orders.stream()
				.sorted(Comparator.comparing(PastOrder::timestamp).reversed())
				.limit(limit)
				.collect(Collectors.toList());
	}

	/**
	 * Check if customer has previously purchased a product.
	 */
	public boolean hasPurchased(String sku) {
		return orders.stream()
				.anyMatch(order -> order.items().containsKey(sku));
	}

	/**
	 * Get all unique product SKUs ever purchased.
	 */
	public java.util.Set<String> getAllPurchasedSkus() {
		return orders.stream()
				.flatMap(order -> order.items().keySet().stream())
				.collect(Collectors.toSet());
	}

	/**
	 * Create an empty purchase history for a customer.
	 */
	public static PurchaseHistory empty(String customerId) {
		return new PurchaseHistory(customerId, new ArrayList<>());
	}

	/**
	 * Create a new purchase history with an additional order.
	 */
	public PurchaseHistory withOrder(PastOrder order) {
		List<PastOrder> newOrders = new ArrayList<>(orders);
		newOrders.add(order);
		return new PurchaseHistory(customerId, newOrders);
	}

	/**
	 * Represents a single past order.
	 *
	 * @param orderId Unique order identifier
	 * @param timestamp When the order was placed
	 * @param items Map of product SKU to quantity
	 * @param total Order total after discounts
	 */
	public record PastOrder(
			String orderId,
			Instant timestamp,
			Map<String, Integer> items,
			BigDecimal total
	) {

		/**
		 * Create a new order with current timestamp.
		 */
		public static PastOrder create(String orderId, Map<String, Integer> items, BigDecimal total) {
			return new PastOrder(orderId, Instant.now(), items, total);
		}
	}
}

