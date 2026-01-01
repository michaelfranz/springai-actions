package org.javai.springai.scenarios.shopping;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.javai.springai.scenarios.shopping.store.MockStoreApi;
import org.javai.springai.scenarios.shopping.store.model.MissionItem;
import org.javai.springai.scenarios.shopping.store.model.MissionPlan;
import org.javai.springai.scenarios.shopping.store.model.MissionRequest;
import org.javai.springai.scenarios.shopping.store.model.Product;
import org.javai.springai.scenarios.shopping.store.model.ShoppingSession;

/**
 * Contributes dynamic content to the system prompt based on shopping session state.
 * 
 * Per design principles:
 * - Mission plan is a living contribution to the system prompt
 * - Gap computation is LLM-driven - we provide the data, LLM reasons about it
 * - Conflict detection is LLM-driven - we provide allergen/dietary data
 * - We inject structured information the LLM can use for reasoning
 */
public class ShoppingPromptContributor {

	private final MockStoreApi storeApi;

	public ShoppingPromptContributor(MockStoreApi storeApi) {
		this.storeApi = storeApi;
	}

	/**
	 * Build a dynamic prompt contribution based on the current session state.
	 * 
	 * @param session The current shopping session (may be null)
	 * @param basket The current basket contents (SKU to quantity)
	 * @return A string to append to the system prompt
	 */
	public String buildPromptContribution(ShoppingSession session, Map<String, Integer> basket) {
		StringBuilder sb = new StringBuilder();

		// Budget section
		if (session != null && session.budgetLimit() != null) {
			sb.append(buildBudgetSection(session, basket));
			sb.append("\n\n");
		}

		// Mission section
		if (session != null && session.activeMission().isPresent()) {
			sb.append(buildMissionSection(session.activeMission().get()));
			sb.append("\n\n");
		}

		// Basket section (always include if there are items)
		if (basket != null && !basket.isEmpty()) {
			sb.append(buildBasketSection(basket));
			sb.append("\n\n");
		}

		// Guidance section (always include if there's context)
		if (session != null && (session.budgetLimit() != null || session.activeMission().isPresent())) {
			sb.append(buildGuidanceSection(session));
		}

		return sb.toString();
	}

	// ========== Section Builders ==========

	private String buildBudgetSection(ShoppingSession session, Map<String, Integer> basket) {
		BigDecimal limit = session.budgetLimit();
		BigDecimal spent = calculateBasketTotal(basket);
		BigDecimal remaining = limit.subtract(spent);
		int percentUsed = limit.compareTo(BigDecimal.ZERO) > 0
				? spent.multiply(BigDecimal.valueOf(100)).divide(limit, 0, java.math.RoundingMode.HALF_UP).intValue()
				: 0;

		StringBuilder sb = new StringBuilder();
		sb.append("BUDGET STATUS:\n");
		sb.append(String.format("- Limit: £%.2f\n", limit));
		sb.append(String.format("- Spent: £%.2f\n", spent));
		sb.append(String.format("- Remaining: £%.2f\n", remaining));
		sb.append(String.format("- Usage: %d%%", percentUsed));

		if (remaining.compareTo(BigDecimal.ZERO) < 0) {
			sb.append("\n⚠️ BUDGET EXCEEDED by £").append(remaining.abs());
		} else if (percentUsed >= 80) {
			sb.append("\n⚠️ APPROACHING BUDGET LIMIT");
		}

		return sb.toString();
	}

	private String buildMissionSection(MissionPlan plan) {
		MissionRequest req = plan.request();

		StringBuilder sb = new StringBuilder();
		sb.append("CURRENT MISSION:\n");
		sb.append(String.format("- Description: %s\n", req.description()));
		sb.append(String.format("- Headcount: %d people\n", req.headcount()));
		sb.append(String.format("- Occasion: %s\n", req.occasion().name().toLowerCase()));

		if (!req.dietaryRequirements().isEmpty()) {
			sb.append(String.format("- Dietary requirements: %s\n", String.join(", ", req.dietaryRequirements())));
		}
		if (!req.allergenExclusions().isEmpty()) {
			sb.append(String.format("- Allergens to EXCLUDE: %s\n", String.join(", ", req.allergenExclusions())));
		}
		if (req.budgetLimit() != null) {
			sb.append(String.format("- Mission budget: £%.2f\n", req.budgetLimit()));
		}

		sb.append("\nMISSION PLAN ITEMS:\n");
		for (MissionItem item : plan.items()) {
			Product p = item.product();
			sb.append(String.format("- %d × %s (%s) - %s\n",
					item.quantity(), p.name(), p.category(), item.rationale()));

			// Include safety info for LLM awareness
			if (!p.dietaryFlags().isEmpty()) {
				sb.append(String.format("  Dietary: %s\n", String.join(", ", p.dietaryFlags())));
			}
			if (!p.allergens().isEmpty()) {
				sb.append(String.format("  Contains: %s\n", String.join(", ", p.allergens())));
			}
		}

		sb.append(String.format("\nEstimated total: £%.2f", plan.estimatedTotal()));

		if (!plan.warnings().isEmpty()) {
			sb.append("\n\n⚠️ MISSION WARNINGS:\n");
			for (String warning : plan.warnings()) {
				sb.append(String.format("- %s\n", warning));
			}
		}

		return sb.toString();
	}

	private String buildBasketSection(Map<String, Integer> basket) {
		StringBuilder sb = new StringBuilder();
		sb.append("CURRENT BASKET:\n");

		BigDecimal total = BigDecimal.ZERO;
		for (Map.Entry<String, Integer> entry : basket.entrySet()) {
			Optional<Product> productOpt = storeApi.findProductBySku(entry.getKey());
			if (productOpt.isPresent()) {
				Product p = productOpt.get();
				int qty = entry.getValue();
				BigDecimal lineTotal = p.unitPrice().multiply(BigDecimal.valueOf(qty));
				total = total.add(lineTotal);

				sb.append(String.format("- %d × %s @ £%.2f = £%.2f\n",
						qty, p.name(), p.unitPrice(), lineTotal));

				// Include allergen info for conflict detection
				if (!p.allergens().isEmpty()) {
					sb.append(String.format("  [Contains: %s]\n", String.join(", ", p.allergens())));
				}
			} else {
				sb.append(String.format("- %d × %s (unknown product)\n", entry.getValue(), entry.getKey()));
			}
		}

		sb.append(String.format("\nBasket total: £%.2f", total));
		return sb.toString();
	}

	private String buildGuidanceSection(ShoppingSession session) {
		StringBuilder sb = new StringBuilder();
		sb.append("GUIDANCE FOR THIS SESSION:\n");

		// Mission-specific guidance
		if (session.activeMission().isPresent()) {
			MissionPlan plan = session.activeMission().get();
			MissionRequest req = plan.request();

			sb.append("\n## Gap Analysis\n");
			sb.append("- Compare the MISSION PLAN ITEMS to the CURRENT BASKET\n");
			sb.append("- Identify items that are in the plan but not in the basket (missing)\n");
			sb.append("- Note items in the basket that are not in the plan (extras - these are OK)\n");
			sb.append("- Suggest next items to add based on what's missing\n");

			if (!req.allergenExclusions().isEmpty()) {
				sb.append("\n## Allergen Safety\n");
				sb.append(String.format("- The customer has specified allergens to EXCLUDE: %s\n",
						String.join(", ", req.allergenExclusions())));
				sb.append("- WARN (do not block) if the customer tries to add items containing these allergens\n");
				sb.append("- Check the [Contains: ...] tags in basket items for conflicts\n");
			}

			if (!req.dietaryRequirements().isEmpty()) {
				sb.append("\n## Dietary Requirements\n");
				sb.append(String.format("- The customer has specified dietary requirements: %s\n",
						String.join(", ", req.dietaryRequirements())));
				sb.append("- WARN (do not block) if adding items that don't meet these requirements\n");
				sb.append("- Mission plan items should already satisfy these requirements\n");
			}

			sb.append("\n## Completion\n");
			sb.append("- When the basket covers most/all of the mission plan, suggest proceeding to checkout\n");
			sb.append("- The customer decides when the mission is complete, not the system\n");
		}

		// Budget guidance
		if (session.budgetLimit() != null) {
			sb.append("\n## Budget Awareness\n");
			sb.append("- Inform the customer about budget status when relevant\n");
			sb.append("- WARN but DO NOT BLOCK if an action would exceed the budget\n");
			sb.append("- The customer has final say on all purchases\n");
			sb.append("- Suggest alternatives if requested items exceed remaining budget\n");
		}

		// Universal guidance
		sb.append("\n## General Principles\n");
		sb.append("- Always allow the customer to proceed after warnings\n");
		sb.append("- Never invent products or prices - use the provided tools\n");
		sb.append("- Be helpful and proactive about surfacing relevant information\n");
		sb.append("- Keep responses concise and action-focused\n");

		return sb.toString();
	}

	// ========== Helpers ==========

	private BigDecimal calculateBasketTotal(Map<String, Integer> basket) {
		if (basket == null || basket.isEmpty()) {
			return BigDecimal.ZERO;
		}

		BigDecimal total = BigDecimal.ZERO;
		for (Map.Entry<String, Integer> entry : basket.entrySet()) {
			Optional<Product> productOpt = storeApi.findProductBySku(entry.getKey());
			if (productOpt.isPresent()) {
				BigDecimal lineTotal = productOpt.get().unitPrice()
						.multiply(BigDecimal.valueOf(entry.getValue()));
				total = total.add(lineTotal);
			}
		}
		return total;
	}
}

