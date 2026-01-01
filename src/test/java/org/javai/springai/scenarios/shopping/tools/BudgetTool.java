package org.javai.springai.scenarios.shopping.tools;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicBoolean;
import org.javai.springai.scenarios.shopping.store.BudgetService;
import org.javai.springai.scenarios.shopping.store.MockStoreApi;
import org.javai.springai.scenarios.shopping.store.model.BudgetStatus;
import org.javai.springai.scenarios.shopping.store.model.ShoppingSession;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Tool for querying budget status during a shopping session.
 * 
 * Enables the assistant to inform customers about their budget without blocking actions.
 * Per design principles: the assistant should INFORM about budget status but NOT BLOCK actions.
 */
public class BudgetTool {

	private final MockStoreApi storeApi;
	private final BudgetService budgetService;

	private final AtomicBoolean getBudgetStatusInvoked = new AtomicBoolean(false);
	private final AtomicBoolean checkAdditionInvoked = new AtomicBoolean(false);
	private final AtomicBoolean getMaxAffordableInvoked = new AtomicBoolean(false);

	public BudgetTool(MockStoreApi storeApi) {
		this.storeApi = storeApi;
		this.budgetService = storeApi.getBudget();
	}

	@Tool(name = "getBudgetStatus", description = """
			Get the current budget status for a shopping session.
			Returns information about:
			- Whether a budget limit has been set
			- The budget limit amount
			- Current spending total
			- Remaining budget
			- Whether approaching the limit or exceeded
			
			Use this to inform customers about their budget status when relevant.""")
	public String getBudgetStatus(
			@ToolParam(description = "The shopping session to check") ShoppingSession session) {

		getBudgetStatusInvoked.set(true);

		if (session == null) {
			return "No active session. Start a shopping session first.";
		}

		BudgetStatus status = storeApi.getBudgetStatus(session);

		return formatBudgetStatus(status, session);
	}

	@Tool(name = "checkBudgetForAddition", description = """
			Check what adding a specific product would do to the budget.
			Returns:
			- Whether the addition would exceed the budget
			- The new remaining budget if added
			- The maximum affordable quantity within budget
			
			Use this before adding items to proactively inform customers about budget impact.
			Note: This is for INFORMATION only - do not block additions based on budget.""")
	public String checkBudgetForAddition(
			@ToolParam(description = "The shopping session") ShoppingSession session,
			@ToolParam(description = "The product SKU or name") String productIdentifier,
			@ToolParam(description = "The quantity to add") int quantity) {

		checkAdditionInvoked.set(true);

		if (session == null) {
			return "No active session.";
		}

		if (session.budgetLimit() == null) {
			return "No budget limit set. The customer can add any amount.";
		}

		// Try to find product by name first, then by SKU
		String sku = storeApi.findProduct(productIdentifier)
				.map(p -> p.sku())
				.orElse(productIdentifier);

		BudgetStatus status = budgetService.checkAddition(session, sku, quantity);
		BigDecimal remaining = budgetService.getRemainingBudget(session).orElse(BigDecimal.ZERO);
		int maxAffordable = budgetService.getMaxAffordableQuantity(session, sku);

		return formatAdditionCheck(status, remaining, maxAffordable, productIdentifier, quantity);
	}

	@Tool(name = "getMaxAffordableQuantity", description = """
			Get the maximum quantity of a product that fits within the remaining budget.
			Returns the maximum units that can be added without exceeding the budget.
			
			Use this to suggest quantity limits when a customer asks about budget-friendly options.""")
	public String getMaxAffordableQuantity(
			@ToolParam(description = "The shopping session") ShoppingSession session,
			@ToolParam(description = "The product SKU or name") String productIdentifier) {

		getMaxAffordableInvoked.set(true);

		if (session == null) {
			return "No active session.";
		}

		if (session.budgetLimit() == null) {
			return "No budget limit set. Any quantity is affordable.";
		}

		// Try to find product by name first, then by SKU
		String sku = storeApi.findProduct(productIdentifier)
				.map(p -> p.sku())
				.orElse(productIdentifier);

		int maxQty = budgetService.getMaxAffordableQuantity(session, sku);
		BigDecimal remaining = budgetService.getRemainingBudget(session).orElse(BigDecimal.ZERO);

		if (maxQty == 0) {
			return String.format("Cannot afford any %s within the remaining budget of £%.2f.",
					productIdentifier, remaining);
		}

		return String.format("Can afford up to %d units of %s within the remaining budget of £%.2f.",
				maxQty, productIdentifier, remaining);
	}

	// ========== Formatting Helpers ==========

	private String formatBudgetStatus(BudgetStatus status, ShoppingSession session) {
		return switch (status) {
			case BudgetStatus.NoBudget() ->
					"No budget limit has been set for this session.";

			case BudgetStatus.WithinBudget(BigDecimal limit, BigDecimal spent, BigDecimal remaining) ->
					String.format("""
							💰 Budget Status: Within budget
							• Limit: £%.2f
							• Spent: £%.2f
							• Remaining: £%.2f""",
							limit, spent, remaining);

			case BudgetStatus.ApproachingLimit(BigDecimal limit, BigDecimal spent, BigDecimal remaining) -> {
				int percentUsed = limit.compareTo(BigDecimal.ZERO) > 0
						? spent.multiply(BigDecimal.valueOf(100)).divide(limit, 0, java.math.RoundingMode.HALF_UP).intValue()
						: 0;
				yield String.format("""
						⚠️ Budget Status: Approaching limit (%d%% used)
						• Limit: £%.2f
						• Spent: £%.2f
						• Remaining: £%.2f""",
						percentUsed, limit, spent, remaining);
			}

			case BudgetStatus.WouldExceed(BigDecimal limit, BigDecimal currentSpend, BigDecimal proposedCost, BigDecimal wouldExceedBy) ->
					String.format("""
							⚠️ Budget Status: Would exceed limit
							• Limit: £%.2f
							• Current spend: £%.2f
							• This would add: £%.2f
							• Over budget by: £%.2f""",
							limit, currentSpend, proposedCost, wouldExceedBy);

			case BudgetStatus.Exceeded(BigDecimal limit, BigDecimal spent, BigDecimal exceededBy) ->
					String.format("""
							🔴 Budget Status: EXCEEDED
							• Limit: £%.2f
							• Spent: £%.2f
							• Over budget by: £%.2f
							
							Note: Customer has chosen to exceed their budget.""",
							limit, spent, exceededBy);
		};
	}

	private String formatAdditionCheck(BudgetStatus status, BigDecimal remaining,
									   int maxAffordable, String product, int requestedQty) {
		StringBuilder sb = new StringBuilder();

		switch (status) {
			case BudgetStatus.WithinBudget(BigDecimal limit, BigDecimal spent, BigDecimal r) -> {
				sb.append(String.format("✓ Adding %d × %s is within budget.\n", requestedQty, product));
				sb.append(String.format("• Remaining after: £%.2f", r));
			}

			case BudgetStatus.ApproachingLimit(BigDecimal limit, BigDecimal spent, BigDecimal r) -> {
				int pct = limit.compareTo(BigDecimal.ZERO) > 0
						? spent.multiply(BigDecimal.valueOf(100)).divide(limit, 0, java.math.RoundingMode.HALF_UP).intValue()
						: 0;
				sb.append(String.format("⚠️ Adding %d × %s approaches the budget limit (%d%% used).\n",
						requestedQty, product, pct));
				sb.append(String.format("• Remaining after: £%.2f", r));
			}

			case BudgetStatus.WouldExceed(BigDecimal limit, BigDecimal currentSpend, BigDecimal proposedCost, BigDecimal wouldExceedBy) -> {
				sb.append(String.format("⚠️ Adding %d × %s would exceed the budget.\n", requestedQty, product));
				sb.append(String.format("• Budget limit: £%.2f\n", limit));
				sb.append(String.format("• Would exceed by: £%.2f\n", wouldExceedBy));
				if (maxAffordable > 0) {
					sb.append(String.format("• Max affordable: %d units", maxAffordable));
				} else {
					sb.append("• Cannot afford any within budget");
				}
			}

			case BudgetStatus.Exceeded(BigDecimal limit, BigDecimal spent, BigDecimal exceededBy) -> {
				sb.append("🔴 Budget is already exceeded.\n");
				sb.append(String.format("• Over by: £%.2f\n", exceededBy));
				sb.append("• Customer can still add items if they choose.");
			}

			case BudgetStatus.NoBudget() -> {
				sb.append("No budget limit set. Any quantity is allowed.");
			}
		}

		return sb.toString();
	}

	// ========== Test Assertion Helpers ==========

	public boolean getBudgetStatusInvoked() {
		return getBudgetStatusInvoked.get();
	}

	public boolean checkAdditionInvoked() {
		return checkAdditionInvoked.get();
	}

	public boolean getMaxAffordableInvoked() {
		return getMaxAffordableInvoked.get();
	}

	public void reset() {
		getBudgetStatusInvoked.set(false);
		checkAdditionInvoked.set(false);
		getMaxAffordableInvoked.set(false);
	}
}

