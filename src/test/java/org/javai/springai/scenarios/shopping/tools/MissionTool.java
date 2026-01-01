package org.javai.springai.scenarios.shopping.tools;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.javai.springai.scenarios.shopping.store.MockStoreApi;
import org.javai.springai.scenarios.shopping.store.model.MissionItem;
import org.javai.springai.scenarios.shopping.store.model.MissionPlan;
import org.javai.springai.scenarios.shopping.store.model.MissionRequest;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Tool for planning and refining shopping missions.
 * 
 * A mission is a structured shopping goal (e.g., "party for 10 vegetarians").
 * The tool generates a plan that respects constraints and can be refined.
 */
public class MissionTool {

	private final MockStoreApi storeApi;

	private final AtomicBoolean planMissionInvoked = new AtomicBoolean(false);
	private final AtomicBoolean refineMissionInvoked = new AtomicBoolean(false);
	private final AtomicBoolean comparePlanToBasketInvoked = new AtomicBoolean(false);

	public MissionTool(MockStoreApi storeApi) {
		this.storeApi = storeApi;
	}

	@Tool(name = "planMission", description = """
			Create a shopping plan for a specific goal or occasion.
			
			Takes a description of what the customer wants to achieve (e.g., "party for 10 vegetarians")
			and generates a list of recommended products with quantities.
			
			The plan respects:
			- Headcount (scales quantities appropriately)
			- Dietary requirements (vegetarian, vegan, gluten-free, etc.)
			- Allergen exclusions (peanuts, dairy, gluten, etc.)
			- Budget limits (if specified)
			- Occasion type (party, dinner, picnic, etc.)
			
			Returns a structured plan that can be reviewed and refined.""")
	public String planMission(
			@ToolParam(description = "Description of the shopping goal") String description,
			@ToolParam(description = "Number of people") int headcount,
			@ToolParam(description = "Occasion: PARTY, DINNER, PICNIC, SNACKS, MEETING") String occasion,
			@ToolParam(description = "Dietary requirements, comma-separated") String dietaryRequirements,
			@ToolParam(description = "Allergens to exclude, comma-separated") String allergenExclusions,
			@ToolParam(description = "Optional budget limit") BigDecimal budgetLimit) {

		planMissionInvoked.set(true);

		// Parse occasion
		MissionRequest.Occasion parsedOccasion;
		try {
			parsedOccasion = MissionRequest.Occasion.valueOf(occasion.toUpperCase());
		} catch (IllegalArgumentException e) {
			parsedOccasion = MissionRequest.Occasion.PARTY;
		}

		// Parse dietary requirements
		Set<String> dietary = parseCommaSeparated(dietaryRequirements);

		// Parse allergen exclusions
		Set<String> allergens = parseCommaSeparated(allergenExclusions);

		MissionRequest request = MissionRequest.builder()
				.description(description)
				.headcount(headcount)
				.occasion(parsedOccasion)
				.dietaryRequirements(dietary)
				.allergenExclusions(allergens)
				.budgetLimit(budgetLimit)
				.build();

		MissionPlan plan = storeApi.planMission(request);

		return formatMissionPlan(plan);
	}

	@Tool(name = "adjustMissionQuantities", description = """
			Adjust quantities in an existing mission plan.
			
			Provide a map of product names/SKUs to their new quantities.
			Use 0 to remove an item from the plan.
			
			This is useful when the customer wants more or fewer of specific items.""")
	public String adjustMissionQuantities(
			@ToolParam(description = "The current mission plan") MissionPlan currentPlan,
			@ToolParam(description = "Map of product names to new quantities") 
			java.util.Map<String, Integer> adjustments) {

		refineMissionInvoked.set(true);

		if (currentPlan == null) {
			return "No current plan to adjust. Create a new plan first.";
		}

		if (adjustments == null || adjustments.isEmpty()) {
			return "No adjustments provided.";
		}

		MissionPlan refined = storeApi.refineMissionPlan(currentPlan, adjustments);
		return formatMissionPlan(refined);
	}

	@Tool(name = "suggestMissionAdditions", description = """
			Suggest additional items that would complement the mission plan.
			
			Based on the current plan's constraints and goals, suggests products
			that would enhance the shopping mission.""")
	public String suggestMissionAdditions(
			@ToolParam(description = "The current mission plan") MissionPlan currentPlan,
			@ToolParam(description = "Maximum number of suggestions") int maxSuggestions) {

		if (currentPlan == null) {
			return "No current plan. Create a plan first to get suggestions.";
		}

		java.util.List<org.javai.springai.scenarios.shopping.store.model.Product> suggestions = 
				storeApi.suggestMissionAdditions(currentPlan, maxSuggestions);

		if (suggestions.isEmpty()) {
			return "No additional suggestions - the plan looks comprehensive!";
		}

		StringBuilder sb = new StringBuilder();
		sb.append("💡 **Suggested Additions**:\n\n");
		for (var product : suggestions) {
			sb.append(String.format("• %s @ £%.2f - %s\n",
					product.name(), product.unitPrice(), product.description()));
		}
		return sb.toString();
	}

	@Tool(name = "comparePlanToBasket", description = """
			Compare the mission plan to the current basket contents.
			
			Returns structured information about:
			- What's covered (items in basket that satisfy the plan)
			- What's missing (planned items not yet in basket)
			- What's extra (basket items not in the plan)
			
			Use this information to guide the customer toward completing their mission.""")
	public String comparePlanToBasket(
			@ToolParam(description = "The mission plan") MissionPlan plan,
			@ToolParam(description = "Current basket contents as SKU:quantity pairs") java.util.Map<String, Integer> basket) {

		comparePlanToBasketInvoked.set(true);

		if (plan == null) {
			return "No mission plan to compare against.";
		}

		if (basket == null || basket.isEmpty()) {
			return formatFullGap(plan);
		}

		StringBuilder sb = new StringBuilder();
		sb.append("📊 **Mission Progress**\n\n");

		int coveredCount = 0;
		int missingCount = 0;

		sb.append("**Covered**:\n");
		for (MissionItem item : plan.items()) {
			String sku = item.product().sku();
			if (basket.containsKey(sku)) {
				int inBasket = basket.get(sku);
				int needed = item.quantity();
				if (inBasket >= needed) {
					sb.append(String.format("✅ %s: %d/%d\n", item.product().name(), inBasket, needed));
					coveredCount++;
				} else {
					sb.append(String.format("⚠️ %s: %d/%d (need %d more)\n", 
							item.product().name(), inBasket, needed, needed - inBasket));
				}
			}
		}

		sb.append("\n**Missing**:\n");
		for (MissionItem item : plan.items()) {
			String sku = item.product().sku();
			if (!basket.containsKey(sku)) {
				sb.append(String.format("❌ %d × %s\n", item.quantity(), item.product().name()));
				missingCount++;
			}
		}

		// Check for extras
		Set<String> plannedSkus = plan.items().stream()
				.map(i -> i.product().sku())
				.collect(Collectors.toSet());

		boolean hasExtras = basket.keySet().stream().anyMatch(sku -> !plannedSkus.contains(sku));
		if (hasExtras) {
			sb.append("\n**Extra items** (not in plan but in basket):\n");
			for (String sku : basket.keySet()) {
				if (!plannedSkus.contains(sku)) {
					storeApi.findProductBySku(sku).ifPresent(p -> 
							sb.append(String.format("➕ %d × %s\n", basket.get(sku), p.name())));
				}
			}
		}

		int total = plan.items().size();
		int percentComplete = total > 0 ? (coveredCount * 100) / total : 0;
		sb.append(String.format("\n**Progress**: %d%% complete (%d/%d items)\n", 
				percentComplete, coveredCount, total));

		if (missingCount == 0) {
			sb.append("\n🎉 Mission plan is fully covered! Ready for checkout?");
		}

		return sb.toString();
	}

	// ========== Helpers ==========

	private Set<String> parseCommaSeparated(String input) {
		if (input == null || input.isBlank()) {
			return Collections.emptySet();
		}
		return Arrays.stream(input.split(","))
				.map(String::trim)
				.map(String::toLowerCase)
				.filter(s -> !s.isEmpty())
				.collect(Collectors.toSet());
	}

	private String formatMissionPlan(MissionPlan plan) {
		StringBuilder sb = new StringBuilder();
		sb.append("📋 **Mission Plan**\n\n");

		MissionRequest req = plan.request();
		sb.append(String.format("**Goal**: %s\n", req.description()));
		sb.append(String.format("**For**: %d people (%s)\n", 
				req.headcount(), req.occasion().name().toLowerCase()));

		if (!req.dietaryRequirements().isEmpty()) {
			sb.append(String.format("**Dietary**: %s\n", String.join(", ", req.dietaryRequirements())));
		}
		if (!req.allergenExclusions().isEmpty()) {
			sb.append(String.format("**Allergen-free**: %s\n", String.join(", ", req.allergenExclusions())));
		}
		if (req.budgetLimit() != null) {
			sb.append(String.format("**Budget**: £%.2f\n", req.budgetLimit()));
		}

		sb.append("\n**Recommended Items**:\n");
		for (MissionItem item : plan.items()) {
			sb.append(String.format("• %d × %s @ £%.2f - %s\n",
					item.quantity(),
					item.product().name(),
					item.product().unitPrice(),
					item.rationale()));
		}

		sb.append(String.format("\n**Estimated Total**: £%.2f", plan.estimatedTotal()));

		if (!plan.notes().isEmpty()) {
			sb.append("\n\n**Notes**:");
			for (String note : plan.notes()) {
				sb.append(String.format("\n• %s", note));
			}
		}

		if (!plan.warnings().isEmpty()) {
			sb.append("\n\n⚠️ **Warnings**:");
			for (String warning : plan.warnings()) {
				sb.append(String.format("\n• %s", warning));
			}
		}

		return sb.toString();
	}

	private String formatFullGap(MissionPlan plan) {
		StringBuilder sb = new StringBuilder();
		sb.append("📊 **Mission Progress**: 0% complete\n\n");
		sb.append("Your basket is empty. Here's what's needed:\n\n");

		for (MissionItem item : plan.items()) {
			sb.append(String.format("❌ %d × %s\n", item.quantity(), item.product().name()));
		}

		sb.append(String.format("\n**Total items needed**: %d", plan.items().size()));
		return sb.toString();
	}

	// ========== Test Assertion Helpers ==========

	public boolean planMissionInvoked() {
		return planMissionInvoked.get();
	}

	public boolean refineMissionInvoked() {
		return refineMissionInvoked.get();
	}

	public boolean comparePlanToBasketInvoked() {
		return comparePlanToBasketInvoked.get();
	}

	public void reset() {
		planMissionInvoked.set(false);
		refineMissionInvoked.set(false);
		comparePlanToBasketInvoked.set(false);
	}
}

