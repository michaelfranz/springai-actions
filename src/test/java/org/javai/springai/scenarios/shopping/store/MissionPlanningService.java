package org.javai.springai.scenarios.shopping.store;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.javai.springai.scenarios.shopping.store.model.AvailabilityResult;
import org.javai.springai.scenarios.shopping.store.model.MissionPlan;
import org.javai.springai.scenarios.shopping.store.model.MissionRequest;
import org.javai.springai.scenarios.shopping.store.model.MissionRequest.Occasion;
import org.javai.springai.scenarios.shopping.store.model.Product;

/**
 * Service for planning shopping missions.
 * Selects products and quantities based on occasion, headcount, dietary needs, and budget.
 */
public class MissionPlanningService {

	private final ProductCatalog catalog;
	private final InventoryService inventory;
	private final PricingService pricing;

	// Category priorities for different occasions (lowercase to match catalog)
	private static final Map<Occasion, List<String>> OCCASION_CATEGORY_PRIORITIES = Map.of(
			Occasion.PARTY, List.of("party", "snacks", "beverages", "bakery"),
			Occasion.DINNER, List.of("produce", "dairy", "bakery", "beverages"),
			Occasion.PICNIC, List.of("snacks", "beverages", "bakery", "produce"),
			Occasion.SNACKS, List.of("snacks", "beverages"),
			Occasion.MEETING, List.of("beverages", "snacks")
	);

	// Base quantities per person for each category (adjusted by occasion multiplier)
	private static final Map<String, PortionRule> CATEGORY_PORTIONS = Map.of(
			"beverages", new PortionRule(2, "drinks per person"),
			"snacks", new PortionRule(1, "snack item per person"),
			"party", new PortionRule(0.5, "shared item per 2 people"),
			"bakery", new PortionRule(0.3, "shared bakery item"),
			"produce", new PortionRule(0.5, "shared produce item"),
			"dairy", new PortionRule(0.25, "shared dairy item")
	);

	public MissionPlanningService(ProductCatalog catalog, InventoryService inventory, PricingService pricing) {
		this.catalog = catalog;
		this.inventory = inventory;
		this.pricing = pricing;
	}

	/**
	 * Plan a shopping mission based on the request constraints.
	 *
	 * @param request The mission request with constraints
	 * @return A mission plan with recommended products and quantities
	 */
	public MissionPlan planMission(MissionRequest request) {
		MissionPlan.Builder builder = MissionPlan.builder(request);

		// Get priority categories for this occasion
		List<String> priorityCategories = OCCASION_CATEGORY_PRIORITIES.getOrDefault(
				request.occasion(), List.of("Snacks", "Beverages"));

		// Track which categories we've added products from
		Set<String> coveredCategories = new HashSet<>();

		// Process each category in priority order
		for (String category : priorityCategories) {
			List<Product> candidates = getEligibleProducts(request, category);
			if (candidates.isEmpty()) {
				continue;
			}

			// Select best products from this category
			List<ProductSelection> selections = selectProductsForCategory(
					request, category, candidates, builder);

			for (ProductSelection selection : selections) {
				// Check budget before adding
				if (request.hasBudget()) {
					BigDecimal itemCost = selection.product().unitPrice()
							.multiply(BigDecimal.valueOf(selection.quantity()));
					if (builder.wouldExceedBudget(itemCost)) {
						builder.addWarning("Budget limit reached - some items omitted");
						break;
					}
				}

				builder.addItem(selection.product(), selection.quantity(), selection.rationale());
				coveredCategories.add(category);
			}

			// If we're over budget, stop adding more categories
			if (request.hasBudget() && builder.getRemainingBudget() != null 
					&& builder.getRemainingBudget().compareTo(BigDecimal.ZERO) <= 0) {
				break;
			}
		}

		// Add notes about the plan
		addPlanNotes(builder, request, coveredCategories, priorityCategories);

		return builder.build();
	}

	/**
	 * Get products eligible for the mission (matching dietary/allergen constraints).
	 */
	private List<Product> getEligibleProducts(MissionRequest request, String category) {
		List<Product> products = catalog.findByCategory(category);

		return products.stream()
				// Filter by dietary requirements
				.filter(p -> matchesDietaryRequirements(p, request.dietaryRequirements()))
				// Filter by allergen exclusions
				.filter(p -> isSafeFromAllergens(p, request.allergenExclusions()))
				// Filter by availability (must have at least 1 in stock)
				.filter(this::isInStock)
				// Sort by price (prefer mid-range options)
				.sorted(Comparator.comparing(Product::unitPrice))
				.collect(Collectors.toList());
	}

	/**
	 * Check if a product matches required dietary flags.
	 */
	private boolean matchesDietaryRequirements(Product product, Set<String> requirements) {
		if (requirements == null || requirements.isEmpty()) {
			return true;
		}

		for (String requirement : requirements) {
			String req = requirement.toLowerCase();
			if (!product.hasDietaryFlag(req)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Check if a product is safe from specified allergens.
	 */
	private boolean isSafeFromAllergens(Product product, Set<String> allergens) {
		if (allergens == null || allergens.isEmpty()) {
			return true;
		}

		return product.isSafeFor(allergens);
	}

	/**
	 * Check if a product is in stock.
	 */
	private boolean isInStock(Product product) {
		AvailabilityResult availability = inventory.checkAvailabilityBySku(product.sku(), 1);
		return availability instanceof AvailabilityResult.Available;
	}

	/**
	 * Select products and quantities for a category.
	 */
	private List<ProductSelection> selectProductsForCategory(
			MissionRequest request, String category, 
			List<Product> candidates, MissionPlan.Builder builder) {

		List<ProductSelection> selections = new ArrayList<>();

		if (candidates.isEmpty()) {
			return selections;
		}

		PortionRule portionRule = CATEGORY_PORTIONS.getOrDefault(category, 
				new PortionRule(0.5, "item"));

		// Calculate base quantity for this category
		double baseQuantity = portionRule.perPerson() * request.headcount() 
				* request.occasion().portionMultiplier();

		int totalQuantityNeeded = Math.max(1, (int) Math.ceil(baseQuantity));

		// For variety, select multiple products if we need many items
		int productsToSelect = Math.min(candidates.size(), 
				Math.max(1, totalQuantityNeeded / 2));

		int quantityPerProduct = Math.max(1, totalQuantityNeeded / productsToSelect);
		int remainder = totalQuantityNeeded % productsToSelect;

		for (int i = 0; i < productsToSelect; i++) {
			Product product = candidates.get(i);

			// Check available stock
			int maxAvailable = getAvailableQuantity(product.sku());
			int quantity = Math.min(quantityPerProduct + (i < remainder ? 1 : 0), maxAvailable);

			if (quantity > 0) {
				String rationale = formatRationale(quantity, request.headcount(), portionRule);
				selections.add(new ProductSelection(product, quantity, rationale));
			}
		}

		return selections;
	}

	/**
	 * Get available quantity for a product.
	 */
	private int getAvailableQuantity(String sku) {
		return inventory.getAvailableQuantity(sku);
	}

	/**
	 * Format a human-readable rationale for the quantity.
	 */
	private String formatRationale(int quantity, int headcount, PortionRule rule) {
		if (quantity == 1) {
			return "1 " + rule.description();
		}

		double perPerson = (double) quantity / headcount;
		if (perPerson >= 1) {
			return String.format("%d for %d people (%.1f each)", quantity, headcount, perPerson);
		} else {
			int peoplePerItem = (int) Math.ceil(1 / perPerson);
			return String.format("%d to share (1 per %d people)", quantity, peoplePerItem);
		}
	}

	/**
	 * Add explanatory notes to the plan.
	 */
	private void addPlanNotes(MissionPlan.Builder builder, MissionRequest request,
			Set<String> coveredCategories, List<String> priorityCategories) {

		// Note about occasion
		builder.addNote(String.format("Plan for %s for %d people",
				request.occasion().label(), request.headcount()));

		// Note about dietary requirements
		if (request.hasDietaryRequirements()) {
			builder.addNote("All items are " + String.join(", ", request.dietaryRequirements()));
		}

		// Note about allergens
		if (request.hasAllergenExclusions()) {
			builder.addNote("Excluded allergens: " + String.join(", ", request.allergenExclusions()));
		}

		// Warning about missing categories
		List<String> missingCategories = priorityCategories.stream()
				.filter(c -> !coveredCategories.contains(c))
				.collect(Collectors.toList());

		if (!missingCategories.isEmpty()) {
			builder.addWarning("Could not find suitable items in: " + 
					String.join(", ", missingCategories));
		}
	}

	/**
	 * Refine an existing mission plan with adjustments.
	 *
	 * @param originalPlan The plan to refine
	 * @param adjustments Map of product name to new quantity (0 to remove)
	 * @return A new refined plan
	 */
	public MissionPlan refinePlan(MissionPlan originalPlan, Map<String, Integer> adjustments) {
		MissionPlan.Builder builder = MissionPlan.builder(originalPlan.request());

		// Copy items with adjustments
		for (var item : originalPlan.items()) {
			String productName = item.productName();
			int newQuantity = adjustments.getOrDefault(productName, item.quantity());

			if (newQuantity > 0) {
				String rationale = item.rationale();
				if (adjustments.containsKey(productName)) {
					rationale = "Adjusted from " + item.quantity() + " to " + newQuantity;
				}
				builder.addItem(item.product(), newQuantity, rationale);
			}
		}

		builder.addNote("Refined plan with customer adjustments");

		return builder.build();
	}

	/**
	 * Suggest additional items that would complement the current plan.
	 */
	public List<Product> suggestAdditions(MissionPlan plan, int maxSuggestions) {
		MissionRequest request = plan.request();
		Set<String> existingSkus = plan.toBasketMap().keySet();

		List<Product> suggestions = new ArrayList<>();

		// Get all eligible products not in the plan
		for (String category : catalog.getCategories()) {
			catalog.findByCategory(category).stream()
					.filter(p -> !existingSkus.contains(p.sku()))
					.filter(p -> matchesDietaryRequirements(p, request.dietaryRequirements()))
					.filter(p -> isSafeFromAllergens(p, request.allergenExclusions()))
					.filter(this::isInStock)
					.limit(2)
					.forEach(suggestions::add);
		}

		// If budget limited, filter by price
		if (request.hasBudget() && plan.getBudgetRemaining() != null) {
			BigDecimal remaining = plan.getBudgetRemaining();
			suggestions = suggestions.stream()
					.filter(p -> p.unitPrice().compareTo(remaining) <= 0)
					.collect(Collectors.toList());
		}

		return suggestions.stream()
				.limit(maxSuggestions)
				.collect(Collectors.toList());
	}

	// Internal record for portion calculation rules
	private record PortionRule(double perPerson, String description) {}

	// Internal record for product selection
	private record ProductSelection(Product product, int quantity, String rationale) {}
}

