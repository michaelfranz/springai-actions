package org.javai.springai.scenarios.shopping.store.model;

import java.math.BigDecimal;

/**
 * Represents the current budget status for a shopping session.
 */
public sealed interface BudgetStatus {

	/**
	 * No budget has been set for this session.
	 */
	record NoBudget() implements BudgetStatus {}

	/**
	 * Session is within budget with comfortable margin.
	 *
	 * @param limit The budget limit
	 * @param spent Amount already spent/committed
	 * @param remaining Amount remaining
	 */
	record WithinBudget(
			BigDecimal limit,
			BigDecimal spent,
			BigDecimal remaining
	) implements BudgetStatus {

		public BigDecimal percentUsed() {
			if (limit.compareTo(BigDecimal.ZERO) == 0) {
				return BigDecimal.ZERO;
			}
			return spent.divide(limit, 4, java.math.RoundingMode.HALF_UP)
					.multiply(BigDecimal.valueOf(100));
		}
	}

	/**
	 * Session is approaching the budget limit (>80% used).
	 *
	 * @param limit The budget limit
	 * @param spent Amount already spent/committed
	 * @param remaining Amount remaining
	 */
	record ApproachingLimit(
			BigDecimal limit,
			BigDecimal spent,
			BigDecimal remaining
	) implements BudgetStatus {}

	/**
	 * Adding the proposed item would exceed the budget.
	 *
	 * @param limit The budget limit
	 * @param currentSpend Current basket total
	 * @param proposedItemCost Cost of item being added
	 * @param wouldExceedBy Amount over budget if added
	 */
	record WouldExceed(
			BigDecimal limit,
			BigDecimal currentSpend,
			BigDecimal proposedItemCost,
			BigDecimal wouldExceedBy
	) implements BudgetStatus {}

	/**
	 * Session has already exceeded the budget.
	 *
	 * @param limit The budget limit
	 * @param spent Amount already spent/committed
	 * @param exceededBy Amount over budget
	 */
	record Exceeded(
			BigDecimal limit,
			BigDecimal spent,
			BigDecimal exceededBy
	) implements BudgetStatus {}
}

