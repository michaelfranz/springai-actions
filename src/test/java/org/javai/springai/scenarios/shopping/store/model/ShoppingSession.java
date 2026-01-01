package org.javai.springai.scenarios.shopping.store.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Represents an active shopping session with optional budget tracking and mission plan.
 * 
 * The mission plan, when present, serves as a "statement of intent" that influences
 * LLM recommendations. The gap between the plan and the basket is computed by the LLM,
 * not programmatically. The user decides when the mission is accomplished.
 *
 * @param sessionId Unique session identifier
 * @param customerId Optional customer ID for personalization
 * @param budgetLimit Optional spending limit set by customer
 * @param basket Current basket contents (SKU to quantity)
 * @param activeMission Optional mission plan guiding this shopping session
 * @param startedAt When the session began
 */
public record ShoppingSession(
		String sessionId,
		String customerId,
		BigDecimal budgetLimit,
		Map<String, Integer> basket,
		Optional<MissionPlan> activeMission,
		Instant startedAt
) {

	/**
	 * Create a new session with optional customer ID.
	 */
	public static ShoppingSession create(String sessionId, String customerId) {
		return new ShoppingSession(
				sessionId,
				customerId,
				null,
				new HashMap<>(),
				Optional.empty(),
				Instant.now()
		);
	}

	/**
	 * Create an anonymous session.
	 */
	public static ShoppingSession anonymous(String sessionId) {
		return create(sessionId, null);
	}

	/**
	 * Check if this session has a budget limit set.
	 */
	public boolean hasBudget() {
		return budgetLimit != null && budgetLimit.compareTo(BigDecimal.ZERO) > 0;
	}

	/**
	 * Check if the session is for an identified customer.
	 */
	public boolean isAuthenticated() {
		return customerId != null && !customerId.isBlank();
	}

	/**
	 * Get the budget limit if set.
	 */
	public Optional<BigDecimal> getBudgetLimit() {
		return Optional.ofNullable(budgetLimit);
	}

	/**
	 * Create a new session with a budget limit set.
	 */
	public ShoppingSession withBudget(BigDecimal limit) {
		return new ShoppingSession(sessionId, customerId, limit, basket, activeMission, startedAt);
	}

	/**
	 * Create a new session with the budget removed.
	 */
	public ShoppingSession withoutBudget() {
		return new ShoppingSession(sessionId, customerId, null, basket, activeMission, startedAt);
	}

	/**
	 * Create a new session with an active mission plan.
	 * The mission plan serves as a reference point for the LLM to compute gaps.
	 */
	public ShoppingSession withMission(MissionPlan mission) {
		return new ShoppingSession(sessionId, customerId, budgetLimit, basket, Optional.ofNullable(mission), startedAt);
	}

	/**
	 * Create a new session with the mission cleared.
	 * Use when the user explicitly resets or abandons the mission.
	 */
	public ShoppingSession withoutMission() {
		return new ShoppingSession(sessionId, customerId, budgetLimit, basket, Optional.empty(), startedAt);
	}

	/**
	 * Create a new session with updated basket.
	 */
	public ShoppingSession withBasket(Map<String, Integer> newBasket) {
		return new ShoppingSession(sessionId, customerId, budgetLimit, new HashMap<>(newBasket), activeMission, startedAt);
	}

	/**
	 * Create a new session with an item added to basket.
	 */
	public ShoppingSession withItemAdded(String sku, int quantity) {
		Map<String, Integer> newBasket = new HashMap<>(basket);
		newBasket.merge(sku, quantity, Integer::sum);
		return withBasket(newBasket);
	}

	/**
	 * Create a new session with an item removed from basket.
	 */
	public ShoppingSession withItemRemoved(String sku) {
		Map<String, Integer> newBasket = new HashMap<>(basket);
		newBasket.remove(sku);
		return withBasket(newBasket);
	}

	/**
	 * Create a new session with item quantity updated.
	 */
	public ShoppingSession withItemQuantity(String sku, int quantity) {
		Map<String, Integer> newBasket = new HashMap<>(basket);
		if (quantity <= 0) {
			newBasket.remove(sku);
		} else {
			newBasket.put(sku, quantity);
		}
		return withBasket(newBasket);
	}

	/**
	 * Check if basket is empty.
	 */
	public boolean isBasketEmpty() {
		return basket.isEmpty();
	}

	/**
	 * Get total item count in basket.
	 */
	public int getItemCount() {
		return basket.values().stream().mapToInt(Integer::intValue).sum();
	}
}

