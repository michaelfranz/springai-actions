package org.javai.springai.scenarios.shopping.store;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;

import org.javai.springai.scenarios.shopping.store.model.BudgetStatus;
import org.javai.springai.scenarios.shopping.store.model.PricingBreakdown;
import org.javai.springai.scenarios.shopping.store.model.Product;
import org.javai.springai.scenarios.shopping.store.model.ShoppingSession;

/**
 * Service for budget tracking and validation.
 * Works with the PricingService to calculate basket totals and check budget constraints.
 */
public class BudgetService {

	private static final BigDecimal APPROACHING_LIMIT_THRESHOLD = new BigDecimal("0.80"); // 80%

	private final PricingService pricingService;
	private final ProductCatalog catalog;

	public BudgetService(PricingService pricingService, ProductCatalog catalog) {
		this.pricingService = pricingService;
		this.catalog = catalog;
	}

	/**
	 * Get the current budget status for a session.
	 *
	 * @param session The shopping session
	 * @return Budget status indicating remaining budget or warnings
	 */
	public BudgetStatus getBudgetStatus(ShoppingSession session) {
		if (!session.hasBudget()) {
			return new BudgetStatus.NoBudget();
		}

		BigDecimal limit = session.budgetLimit();
		BigDecimal spent = calculateCurrentSpend(session.basket());
		BigDecimal remaining = limit.subtract(spent);

		if (remaining.compareTo(BigDecimal.ZERO) < 0) {
			return new BudgetStatus.Exceeded(limit, spent, remaining.abs());
		}

		BigDecimal usedRatio = spent.divide(limit, 4, RoundingMode.HALF_UP);
		if (usedRatio.compareTo(APPROACHING_LIMIT_THRESHOLD) >= 0) {
			return new BudgetStatus.ApproachingLimit(limit, spent, remaining);
		}

		return new BudgetStatus.WithinBudget(limit, spent, remaining);
	}

	/**
	 * Check if adding an item would exceed the budget.
	 *
	 * @param session The shopping session
	 * @param productName Product to add
	 * @param quantity Quantity to add
	 * @return Budget status after adding the item
	 */
	public BudgetStatus checkAddition(ShoppingSession session, String productName, int quantity) {
		if (!session.hasBudget()) {
			return new BudgetStatus.NoBudget();
		}

		Optional<Product> productOpt = catalog.findByName(productName);
		if (productOpt.isEmpty()) {
			return getBudgetStatus(session); // Product not found, return current status
		}

		Product product = productOpt.get();
		BigDecimal itemCost = product.unitPrice().multiply(BigDecimal.valueOf(quantity));
		BigDecimal limit = session.budgetLimit();
		BigDecimal currentSpend = calculateCurrentSpend(session.basket());
		BigDecimal projectedSpend = currentSpend.add(itemCost);
		BigDecimal remaining = limit.subtract(projectedSpend);

		if (remaining.compareTo(BigDecimal.ZERO) < 0) {
			return new BudgetStatus.WouldExceed(limit, currentSpend, itemCost, remaining.abs());
		}

		BigDecimal usedRatio = projectedSpend.divide(limit, 4, RoundingMode.HALF_UP);
		if (usedRatio.compareTo(APPROACHING_LIMIT_THRESHOLD) >= 0) {
			return new BudgetStatus.ApproachingLimit(limit, projectedSpend, remaining);
		}

		return new BudgetStatus.WithinBudget(limit, projectedSpend, remaining);
	}

	/**
	 * Check if adding an item by SKU would exceed the budget.
	 *
	 * @param session The shopping session
	 * @param sku Product SKU to add
	 * @param quantity Quantity to add
	 * @return Budget status after adding the item
	 */
	public BudgetStatus checkAdditionBySku(ShoppingSession session, String sku, int quantity) {
		if (!session.hasBudget()) {
			return new BudgetStatus.NoBudget();
		}

		Optional<Product> productOpt = catalog.findBySku(sku);
		if (productOpt.isEmpty()) {
			return getBudgetStatus(session);
		}

		return checkAddition(session, productOpt.get().name(), quantity);
	}

	/**
	 * Get the remaining budget for a session.
	 *
	 * @param session The shopping session
	 * @return Remaining budget, or empty if no budget set
	 */
	public Optional<BigDecimal> getRemainingBudget(ShoppingSession session) {
		if (!session.hasBudget()) {
			return Optional.empty();
		}

		BigDecimal spent = calculateCurrentSpend(session.basket());
		return Optional.of(session.budgetLimit().subtract(spent));
	}

	/**
	 * Check if the session would exceed budget with the given additional cost.
	 *
	 * @param session The shopping session
	 * @param additionalCost Cost to add
	 * @return true if adding would exceed budget
	 */
	public boolean wouldExceedBudget(ShoppingSession session, BigDecimal additionalCost) {
		if (!session.hasBudget()) {
			return false;
		}

		BigDecimal currentSpend = calculateCurrentSpend(session.basket());
		BigDecimal projectedSpend = currentSpend.add(additionalCost);
		return projectedSpend.compareTo(session.budgetLimit()) > 0;
	}

	/**
	 * Calculate the maximum quantity of a product that fits within remaining budget.
	 *
	 * @param session The shopping session
	 * @param productName Product to check
	 * @return Maximum affordable quantity, or -1 if no budget set
	 */
	public int getMaxAffordableQuantity(ShoppingSession session, String productName) {
		if (!session.hasBudget()) {
			return -1; // No limit
		}

		Optional<Product> productOpt = catalog.findByName(productName);
		if (productOpt.isEmpty()) {
			return 0;
		}

		Optional<BigDecimal> remaining = getRemainingBudget(session);
		if (remaining.isEmpty() || remaining.get().compareTo(BigDecimal.ZERO) <= 0) {
			return 0;
		}

		Product product = productOpt.get();
		return remaining.get()
				.divide(product.unitPrice(), 0, RoundingMode.DOWN)
				.intValue();
	}

	/**
	 * Calculate the current spend based on basket contents.
	 * Uses pricing service to account for discounts.
	 */
	public BigDecimal calculateCurrentSpend(Map<String, Integer> basket) {
		if (basket.isEmpty()) {
			return BigDecimal.ZERO;
		}

		PricingBreakdown breakdown = pricingService.calculateBasketTotal(basket);
		return breakdown.total();
	}

	/**
	 * Format budget status as a human-readable message.
	 */
	public String formatBudgetStatus(BudgetStatus status) {
		return switch (status) {
			case BudgetStatus.NoBudget() -> "No budget set.";
			case BudgetStatus.WithinBudget(var limit, var spent, var remaining) ->
					String.format("Budget: £%.2f | Spent: £%.2f | Remaining: £%.2f",
							limit, spent, remaining);
			case BudgetStatus.ApproachingLimit(var limit, var spent, var remaining) ->
					String.format("⚠️ Approaching budget limit! Spent: £%.2f of £%.2f (£%.2f remaining)",
							spent, limit, remaining);
			case BudgetStatus.WouldExceed(var limit, var current, var itemCost, var exceeds) ->
					String.format("❌ Adding this item (£%.2f) would exceed budget by £%.2f",
							itemCost, exceeds);
			case BudgetStatus.Exceeded(var limit, var spent, var exceeds) ->
					String.format("❌ Over budget! Spent £%.2f, exceeds limit of £%.2f by £%.2f",
							spent, limit, exceeds);
		};
	}
}

