package org.javai.springai.scenarios.shopping.store.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents a proposed shopping plan for a mission.
 *
 * @param request The original mission request
 * @param items List of recommended items with quantities
 * @param estimatedTotal Total cost before any basket-level discounts
 * @param notes Advisory notes about the plan
 * @param warnings Any constraints that could not be fully satisfied
 */
public record MissionPlan(
		MissionRequest request,
		List<MissionItem> items,
		BigDecimal estimatedTotal,
		List<String> notes,
		List<String> warnings
) {

	/**
	 * Check if the plan has any items.
	 */
	public boolean hasItems() {
		return items != null && !items.isEmpty();
	}

	/**
	 * Check if the plan is within budget.
	 */
	public boolean isWithinBudget() {
		if (!request.hasBudget()) {
			return true;
		}
		return estimatedTotal.compareTo(request.budgetLimit()) <= 0;
	}

	/**
	 * Get budget remaining after this plan.
	 */
	public BigDecimal getBudgetRemaining() {
		if (!request.hasBudget()) {
			return null;
		}
		return request.budgetLimit().subtract(estimatedTotal);
	}

	/**
	 * Get the total number of individual items.
	 */
	public int getTotalItemCount() {
		return items.stream().mapToInt(MissionItem::quantity).sum();
	}

	/**
	 * Get items grouped by category.
	 */
	public Map<String, List<MissionItem>> getItemsByCategory() {
		return items.stream()
				.collect(Collectors.groupingBy(MissionItem::category));
	}

	/**
	 * Convert the plan to a basket map (SKU to quantity).
	 */
	public Map<String, Integer> toBasketMap() {
		Map<String, Integer> basket = new HashMap<>();
		for (MissionItem item : items) {
			basket.merge(item.sku(), item.quantity(), Integer::sum);
		}
		return basket;
	}

	/**
	 * Check if there are any warnings.
	 */
	public boolean hasWarnings() {
		return warnings != null && !warnings.isEmpty();
	}

	/**
	 * Builder for creating MissionPlan instances.
	 */
	public static Builder builder(MissionRequest request) {
		return new Builder(request);
	}

	public static class Builder {
		private final MissionRequest request;
		private final List<MissionItem> items = new ArrayList<>();
		private final List<String> notes = new ArrayList<>();
		private final List<String> warnings = new ArrayList<>();
		private BigDecimal runningTotal = BigDecimal.ZERO;

		public Builder(MissionRequest request) {
			this.request = request;
		}

		public Builder addItem(Product product, int quantity, String rationale) {
			MissionItem item = MissionItem.of(product, quantity, rationale);
			items.add(item);
			runningTotal = runningTotal.add(item.lineTotal());
			return this;
		}

		public Builder addNote(String note) {
			notes.add(note);
			return this;
		}

		public Builder addWarning(String warning) {
			warnings.add(warning);
			return this;
		}

		public BigDecimal getCurrentTotal() {
			return runningTotal;
		}

		public BigDecimal getRemainingBudget() {
			if (!request.hasBudget()) {
				return null;
			}
			return request.budgetLimit().subtract(runningTotal);
		}

		public boolean wouldExceedBudget(BigDecimal additionalCost) {
			if (!request.hasBudget()) {
				return false;
			}
			return runningTotal.add(additionalCost).compareTo(request.budgetLimit()) > 0;
		}

		public MissionPlan build() {
			// Add budget note if applicable
			if (request.hasBudget()) {
				BigDecimal remaining = request.budgetLimit().subtract(runningTotal);
				if (remaining.compareTo(BigDecimal.ZERO) > 0) {
					notes.add(String.format("Budget remaining: £%.2f", remaining));
				} else if (remaining.compareTo(BigDecimal.ZERO) < 0) {
					warnings.add(String.format("Plan exceeds budget by £%.2f", remaining.abs()));
				}
			}

			return new MissionPlan(request, List.copyOf(items), runningTotal,
					List.copyOf(notes), List.copyOf(warnings));
		}
	}
}

