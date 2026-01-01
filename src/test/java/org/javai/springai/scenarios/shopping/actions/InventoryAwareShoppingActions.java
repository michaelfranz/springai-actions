package org.javai.springai.scenarios.shopping.actions;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.api.ActionParam;
import org.javai.springai.scenarios.shopping.store.CustomerProfileService;
import org.javai.springai.scenarios.shopping.store.MockStoreApi;
import org.javai.springai.scenarios.shopping.store.model.AvailabilityResult;
import org.javai.springai.scenarios.shopping.store.model.CustomerProfile;
import org.javai.springai.scenarios.shopping.store.model.LineItem;
import org.javai.springai.scenarios.shopping.store.model.MissionPlan;
import org.javai.springai.scenarios.shopping.store.model.MissionRequest;
import org.javai.springai.scenarios.shopping.store.model.PricingBreakdown;
import org.javai.springai.scenarios.shopping.store.model.Product;
import org.javai.springai.scenarios.shopping.store.model.ShoppingSession;

/**
 * Enhanced shopping actions with inventory awareness and customer personalisation.
 * Validates stock levels before adding items and provides warnings/alternatives.
 * Supports customer-specific recommendations and allergen safety checks.
 */
public class InventoryAwareShoppingActions {

	private final MockStoreApi storeApi;

	// Tracking flags for test assertions
	private final AtomicBoolean startSessionInvoked = new AtomicBoolean(false);
	private final AtomicBoolean presentOffersInvoked = new AtomicBoolean(false);
	private final AtomicBoolean addItemInvoked = new AtomicBoolean(false);
	private final AtomicBoolean updateQuantityInvoked = new AtomicBoolean(false);
	private final AtomicBoolean viewBasketInvoked = new AtomicBoolean(false);
	private final AtomicBoolean removeItemInvoked = new AtomicBoolean(false);
	private final AtomicBoolean computeTotalInvoked = new AtomicBoolean(false);
	private final AtomicBoolean checkoutInvoked = new AtomicBoolean(false);
	private final AtomicBoolean requestFeedbackInvoked = new AtomicBoolean(false);
	private final AtomicBoolean showRecommendationsInvoked = new AtomicBoolean(false);
	private final AtomicBoolean setBudgetInvoked = new AtomicBoolean(false);
	private final AtomicBoolean startMissionInvoked = new AtomicBoolean(false);
	private final AtomicBoolean reviewMissionPlanInvoked = new AtomicBoolean(false);

	// Basket state: maps product SKU to quantity
	private final Map<String, Integer> basket = new HashMap<>();
	private String lastWarning;
	private List<Product> lastAlternatives;
	private AddItemRequest lastAddItem;
	
	// Customer state
	private String currentCustomerId;
	
	// Session state (budget and mission tracking)
	private ShoppingSession currentSession;

	public InventoryAwareShoppingActions(MockStoreApi storeApi) {
		this.storeApi = storeApi;
	}

	@Action(description = """
			Start or reset a shopping session and basket.
			Clears any existing basket contents.
			Optionally provide a customer ID for personalized experience.""")
	public ActionResult startSession(ActionContext context) {
		return startSessionForCustomer(context, null);
	}

	@Action(description = """
			Start a shopping session for a specific customer.
			Loads customer preferences and provides personalized recommendations.""")
	public ActionResult startSessionForCustomer(
			ActionContext context,
			@ActionParam(description = "Customer ID for personalization") String customerId) {
		startSessionInvoked.set(true);
		basket.clear();
		lastWarning = null;
		lastAlternatives = null;
		currentCustomerId = customerId;
		
		// Create a new shopping session
		currentSession = ShoppingSession.create(
				java.util.UUID.randomUUID().toString(),
				customerId
		);
		
		if (context != null) {
			context.put("basket", basket);
			context.put("session", currentSession);
			if (customerId != null) {
				context.put("customerId", customerId);
			}
		}
		storeApi.reset(); // Reset any stock reservations

		if (customerId != null) {
			Optional<CustomerProfile> profileOpt = storeApi.getCustomers().getProfile(customerId);
			if (profileOpt.isPresent()) {
				CustomerProfile profile = profileOpt.get();
				StringBuilder greeting = new StringBuilder();
				greeting.append(String.format("Welcome back, %s! ", profile.name()));
				
				if (!profile.allergens().isEmpty()) {
					greeting.append(String.format("I'll keep your %s allergy in mind. ",
							String.join("/", profile.allergens())));
				}
				
				greeting.append("Your basket is empty.");
				return ActionResult.success(greeting.toString());
			}
		}
		
		return ActionResult.success("Shopping session started. Your basket is empty.");
	}

	@Action(description = "Present current special offers to the shopper.")
	public ActionResult presentOffers() {
		presentOffersInvoked.set(true);
		var offers = storeApi.getActiveOffers();

		if (offers.isEmpty()) {
			return ActionResult.success("No special offers available at the moment.");
		}

		StringBuilder sb = new StringBuilder("Today's special offers:\n");
		for (var offer : offers) {
			sb.append(String.format("- %s: %s\n", offer.name(), offer.description()));
		}
		return ActionResult.success(sb.toString());
	}

	@Action(description = """
			Add a product and quantity to the current basket.
			Validates stock availability before adding.
			Returns warnings if stock is low or suggests alternatives if unavailable.""")
	public ActionResult addItem(
			@ActionParam(description = "Product name") String product,
			@ActionParam(description = "Quantity", allowedRegex = "[0-9]+") int quantity) {

		addItemInvoked.set(true);
		lastAddItem = new AddItemRequest(product, quantity);
		lastWarning = null;
		lastAlternatives = null;

		// Check availability
		AvailabilityResult availability = storeApi.checkAvailability(product, quantity);

		return switch (availability) {
			case AvailabilityResult.Available(int qty, boolean lowStock) -> {
				// Find the product to get the SKU
				Optional<Product> productOpt = storeApi.findProduct(product);
				if (productOpt.isEmpty()) {
					yield ActionResult.error("Product not found: " + product);
				}

				Product p = productOpt.get();
				basket.merge(p.sku(), quantity, Integer::sum);
				storeApi.reserveStock(p.sku(), quantity);
				
				// Keep session basket in sync
				if (currentSession != null) {
					currentSession = currentSession.withItemAdded(p.sku(), quantity);
				}

				if (lowStock) {
					int remaining = storeApi.getAvailableQuantity(p.sku());
					lastWarning = String.format("Stock is running low - only %d left after this order.", remaining);
					yield ActionResult.success(String.format(
							"Added %d × %s to basket. ⚠️ %s", quantity, p.name(), lastWarning));
				}
				yield ActionResult.success(String.format("Added %d × %s to basket.", quantity, p.name()));
			}

			case AvailabilityResult.PartiallyAvailable(int available, int requested, List<Product> alternatives) -> {
				lastAlternatives = alternatives;
				String altText = formatAlternatives(alternatives);
				yield ActionResult.error(String.format(
						"Only %d of %d %s available.%s Would you like to add %d instead?",
						available, requested, product, altText, available));
			}

			case AvailabilityResult.OutOfStock(List<Product> alternatives) -> {
				lastAlternatives = alternatives;
				String altText = formatAlternatives(alternatives);
				yield ActionResult.error(String.format(
						"%s is out of stock.%s", product, altText));
			}

			case AvailabilityResult.Discontinued(List<Product> alternatives) -> {
				lastAlternatives = alternatives;
				String altText = formatAlternatives(alternatives);
				yield ActionResult.error(String.format(
						"%s has been discontinued.%s", product, altText));
			}

			case AvailabilityResult.NotFound(String searchTerm, List<Product> suggestions) -> {
				lastAlternatives = suggestions;
				if (suggestions.isEmpty()) {
					yield ActionResult.error(String.format("Product '%s' not found in catalog.", searchTerm));
				}
				String suggestionText = suggestions.stream()
						.map(Product::name)
						.collect(Collectors.joining(", "));
				yield ActionResult.error(String.format(
						"Product '%s' not found. Did you mean: %s?", searchTerm, suggestionText));
			}
		};
	}

	@Action(description = """
			Update the quantity of an existing item in the basket.
			Can increase or decrease quantity. Use 0 to remove the item.""")
	public ActionResult updateItemQuantity(
			@ActionParam(description = "Product name") String product,
			@ActionParam(description = "New quantity (0 to remove)", allowedRegex = "[0-9]+") int newQuantity) {

		updateQuantityInvoked.set(true);

		// Find the product
		Optional<Product> productOpt = storeApi.findProduct(product);
		if (productOpt.isEmpty()) {
			return ActionResult.error("Product not found: " + product);
		}

		Product p = productOpt.get();
		Integer currentQuantity = basket.get(p.sku());

		if (currentQuantity == null) {
			return ActionResult.error(String.format("%s is not in your basket.", p.name()));
		}

		// If setting to 0, remove the item
		if (newQuantity == 0) {
			storeApi.releaseStock(p.sku(), currentQuantity);
			basket.remove(p.sku());
			return ActionResult.success(String.format("Removed %s from basket.", p.name()));
		}

		int difference = newQuantity - currentQuantity;

		if (difference > 0) {
			// Increasing quantity - check availability
			AvailabilityResult availability = storeApi.checkAvailabilityBySku(p.sku(), difference);

			if (availability instanceof AvailabilityResult.Available(int qty, boolean lowStock)) {
				basket.put(p.sku(), newQuantity);
				storeApi.reserveStock(p.sku(), difference);

				if (lowStock) {
					return ActionResult.success(String.format(
							"Updated %s quantity to %d. ⚠️ Stock is running low.", p.name(), newQuantity));
				}
				return ActionResult.success(String.format("Updated %s quantity to %d.", p.name(), newQuantity));
			} else {
				int available = storeApi.getAvailableQuantity(p.sku());
				return ActionResult.error(String.format(
						"Cannot increase to %d. Only %d more available (total available: %d).",
						newQuantity, available, currentQuantity + available));
			}
		} else {
			// Decreasing quantity - always allowed
			storeApi.releaseStock(p.sku(), -difference);
			basket.put(p.sku(), newQuantity);
			return ActionResult.success(String.format("Updated %s quantity to %d.", p.name(), newQuantity));
		}
	}

	@Action(description = """
			View the current contents of the shopping basket with prices.""")
	public ActionResult viewBasketSummary() {
		viewBasketInvoked.set(true);

		if (basket.isEmpty()) {
			return ActionResult.success("Your basket is empty.");
		}

		PricingBreakdown pricing = storeApi.calculateTotal(basket);

		StringBuilder sb = new StringBuilder("Your basket:\n");
		for (LineItem item : pricing.items()) {
			sb.append(String.format("- %d × %s @ £%.2f = £%.2f\n",
					item.quantity(), item.product().name(),
					item.unitPrice(), item.lineTotal()));
		}

		sb.append(String.format("\nSubtotal: £%.2f", pricing.subtotal()));

		if (!pricing.discounts().isEmpty()) {
			sb.append("\nDiscounts applied:");
			for (var discount : pricing.discounts()) {
				sb.append(String.format("\n  - %s: -£%.2f",
						discount.offer().name(), discount.discountAmount()));
			}
			sb.append(String.format("\nTotal discount: -£%.2f", pricing.totalDiscount()));
		}

		sb.append(String.format("\n**Total: £%.2f**", pricing.total()));

		return ActionResult.success(sb.toString());
	}

	@Action(description = """
			Remove a product from the current basket.""")
	public ActionResult removeItem(
			@ActionParam(description = "Product name to remove") String product) {

		removeItemInvoked.set(true);

		Optional<Product> productOpt = storeApi.findProduct(product);
		if (productOpt.isEmpty()) {
			return ActionResult.error("Product not found: " + product);
		}

		Product p = productOpt.get();
		Integer quantity = basket.remove(p.sku());

		if (quantity == null) {
			return ActionResult.error(String.format("%s was not in your basket.", p.name()));
		}

		storeApi.releaseStock(p.sku(), quantity);
		return ActionResult.success(String.format("Removed %s from basket.", p.name()));
	}

	@Action(description = """
			Compute the basket total including any applicable discounts.""")
	public ActionResult computeTotal() {
		computeTotalInvoked.set(true);

		if (basket.isEmpty()) {
			return ActionResult.success("Your basket is empty. Total: £0.00");
		}

		PricingBreakdown pricing = storeApi.calculateTotal(basket);

		StringBuilder sb = new StringBuilder();
		sb.append(String.format("Subtotal: £%.2f", pricing.subtotal()));

		if (pricing.totalDiscount().compareTo(BigDecimal.ZERO) > 0) {
			sb.append(String.format("\nDiscounts: -£%.2f", pricing.totalDiscount()));
		}

		sb.append(String.format("\n**Total: £%.2f**", pricing.total()));

		return ActionResult.success(sb.toString());
	}

	@Action(description = """
			Checkout the basket and complete the purchase.
			Commits the stock reservations and records purchase history.""")
	public ActionResult checkoutBasket() {
		checkoutInvoked.set(true);

		if (basket.isEmpty()) {
			return ActionResult.error("Cannot checkout - your basket is empty.");
		}

		PricingBreakdown pricing = storeApi.calculateTotal(basket);
		storeApi.commitReservations(basket);

		// Record purchase history if customer is identified
		if (currentCustomerId != null) {
			storeApi.getCustomers().recordPurchase(currentCustomerId, new HashMap<>(basket), pricing.total());
		}

		String summary = String.format("Order complete! Total: £%.2f for %d item(s).",
				pricing.total(), basket.size());

		basket.clear();
		return ActionResult.success(summary);
	}

	@Action(description = """
			Show personalized product recommendations for the current customer.
			Based on purchase history, preferences, and dietary requirements.""")
	public ActionResult showRecommendations() {
		showRecommendationsInvoked.set(true);

		if (currentCustomerId == null) {
			return ActionResult.error("No customer identified. Start a session with a customer ID for recommendations.");
		}

		CustomerProfileService customers = storeApi.getCustomers();
		List<Product> recommendations = customers.getRecommendations(
				currentCustomerId, basket.keySet(), 5);

		if (recommendations.isEmpty()) {
			return ActionResult.success("No specific recommendations at this time. Browse our categories!");
		}

		Optional<CustomerProfile> profileOpt = customers.getProfile(currentCustomerId);
		String intro = profileOpt.map(p -> String.format("Recommended for you, %s:\n", p.name()))
				.orElse("Recommended for you:\n");

		StringBuilder sb = new StringBuilder(intro);
		for (Product product : recommendations) {
			// Check if there's an offer
			List<org.javai.springai.scenarios.shopping.store.model.SpecialOffer> offers = 
					storeApi.getApplicableOffers(java.util.Set.of(product.sku()));
			String offerTag = offers.isEmpty() ? "" : " 🏷️";
			
			sb.append(String.format("- %s (£%.2f)%s\n", product.name(), product.unitPrice(), offerTag));
		}

		return ActionResult.success(sb.toString());
	}

	@Action(description = "Request end-of-session feedback from the shopper.")
	public ActionResult requestFeedback() {
		requestFeedbackInvoked.set(true);
		return ActionResult.success("Thank you for shopping with us! How was your experience today?");
	}

	// ========== Budget Actions (Phase 6) ==========

	@Action(description = """
			Set a spending budget for this shopping session.
			The assistant will track spending against this limit and inform you when approaching or exceeding it.
			Note: Setting a budget does not block additions - it's for information only.""")
	public ActionResult setBudget(
			ActionContext context,
			@ActionParam(description = "The budget limit in pounds") BigDecimal budgetLimit) {
		setBudgetInvoked.set(true);

		if (budgetLimit == null || budgetLimit.compareTo(BigDecimal.ZERO) <= 0) {
			return ActionResult.error("Please provide a valid budget amount greater than £0.");
		}

		if (currentSession == null) {
			currentSession = ShoppingSession.create(
					java.util.UUID.randomUUID().toString(),
					currentCustomerId
			);
		}

		currentSession = currentSession.withBudget(budgetLimit);

		if (context != null) {
			context.put("session", currentSession);
			context.put("budgetLimit", budgetLimit);
		}

		return ActionResult.success(String.format(
				"Budget set to £%.2f. I'll keep you informed of your spending as you shop.",
				budgetLimit));
	}

	// ========== Mission Actions (Phase 7) ==========

	@Action(description = """
			Start a shopping mission with specific goals and constraints.
			Describe your shopping goal (e.g., "party for 10 vegetarians") and I'll create a plan.
			The plan will respect dietary requirements, allergen exclusions, and budget if specified.""")
	public ActionResult startMission(
			ActionContext context,
			@ActionParam(description = "Description of your shopping goal") String description,
			@ActionParam(description = "Number of people to cater for") int headcount,
			@ActionParam(description = "Occasion type: party, dinner, picnic, snacks, meeting") String occasion,
			@ActionParam(description = "Dietary requirements (comma-separated, e.g., 'vegetarian,gluten-free')") String dietaryRequirements,
			@ActionParam(description = "Allergens to exclude (comma-separated, e.g., 'peanuts,dairy')") String allergenExclusions,
			@ActionParam(description = "Optional budget limit") BigDecimal budgetLimit) {
		
		startMissionInvoked.set(true);

		if (description == null || description.isBlank()) {
			return ActionResult.error("Please describe your shopping goal.");
		}

		if (headcount <= 0) {
			return ActionResult.error("Please specify how many people you're shopping for.");
		}

		// Parse occasion
		MissionRequest.Occasion parsedOccasion;
		try {
			parsedOccasion = MissionRequest.Occasion.valueOf(occasion.toUpperCase());
		} catch (IllegalArgumentException e) {
			parsedOccasion = MissionRequest.Occasion.PARTY; // Default
		}

		// Parse dietary requirements
		java.util.Set<String> dietary = java.util.Collections.emptySet();
		if (dietaryRequirements != null && !dietaryRequirements.isBlank()) {
			dietary = java.util.Arrays.stream(dietaryRequirements.split(","))
					.map(String::trim)
					.map(String::toLowerCase)
					.collect(java.util.stream.Collectors.toSet());
		}

		// Parse allergen exclusions
		java.util.Set<String> allergens = java.util.Collections.emptySet();
		if (allergenExclusions != null && !allergenExclusions.isBlank()) {
			allergens = java.util.Arrays.stream(allergenExclusions.split(","))
					.map(String::trim)
					.map(String::toLowerCase)
					.collect(java.util.stream.Collectors.toSet());
		}

		// Create mission request
		MissionRequest request = MissionRequest.builder()
				.description(description)
				.headcount(headcount)
				.occasion(parsedOccasion)
				.dietaryRequirements(dietary)
				.allergenExclusions(allergens)
				.budgetLimit(budgetLimit)
				.build();

		// Plan the mission
		MissionPlan plan = storeApi.planMission(request);

		// Store mission in session
		if (currentSession == null) {
			currentSession = ShoppingSession.create(
					java.util.UUID.randomUUID().toString(),
					currentCustomerId
			);
		}
		currentSession = currentSession.withMission(plan);

		if (context != null) {
			context.put("session", currentSession);
			context.put("missionPlan", plan);
		}

		return ActionResult.success(formatMissionPlan(plan));
	}

	@Action(description = """
			Review the current mission plan.
			Shows the proposed items, quantities, and estimated total.
			Use this to check what's planned before adding items to your basket.""")
	public ActionResult reviewMissionPlan(ActionContext context) {
		reviewMissionPlanInvoked.set(true);

		if (currentSession == null || currentSession.activeMission().isEmpty()) {
			return ActionResult.error("No active mission. Use startMission to create a shopping plan.");
		}

		MissionPlan plan = currentSession.activeMission().get();
		return ActionResult.success(formatMissionPlan(plan));
	}

	private String formatMissionPlan(MissionPlan plan) {
		StringBuilder sb = new StringBuilder();
		sb.append("📋 **Mission Plan**\n\n");

		MissionRequest req = plan.request();
		sb.append(String.format("**Goal**: %s\n", req.description()));
		sb.append(String.format("**For**: %d people (%s)\n", req.headcount(), req.occasion().name().toLowerCase()));

		if (!req.dietaryRequirements().isEmpty()) {
			sb.append(String.format("**Dietary**: %s\n", String.join(", ", req.dietaryRequirements())));
		}
		if (!req.allergenExclusions().isEmpty()) {
			sb.append(String.format("**Excluding allergens**: %s\n", String.join(", ", req.allergenExclusions())));
		}
		if (req.budgetLimit() != null) {
			sb.append(String.format("**Budget**: £%.2f\n", req.budgetLimit()));
		}

		sb.append("\n**Suggested Items**:\n");
		for (var item : plan.items()) {
			sb.append(String.format("- %d × %s (£%.2f each) - %s\n",
					item.quantity(),
					item.product().name(),
					item.product().unitPrice(),
					item.rationale()));
		}

		sb.append(String.format("\n**Estimated Total**: £%.2f\n", plan.estimatedTotal()));

		if (!plan.notes().isEmpty()) {
			sb.append("\n**Notes**:\n");
			for (String note : plan.notes()) {
				sb.append(String.format("• %s\n", note));
			}
		}

		if (!plan.warnings().isEmpty()) {
			sb.append("\n**⚠️ Warnings**:\n");
			for (String warning : plan.warnings()) {
				sb.append(String.format("• %s\n", warning));
			}
		}

		sb.append("\nWould you like me to add these items to your basket?");
		return sb.toString();
	}

	// ========== Test Assertion Helpers ==========

	public boolean startSessionInvoked() {
		return startSessionInvoked.get();
	}

	public boolean presentOffersInvoked() {
		return presentOffersInvoked.get();
	}

	public boolean addItemInvoked() {
		return addItemInvoked.get();
	}

	public boolean updateQuantityInvoked() {
		return updateQuantityInvoked.get();
	}

	public boolean viewBasketInvoked() {
		return viewBasketInvoked.get();
	}

	public boolean removeItemInvoked() {
		return removeItemInvoked.get();
	}

	public boolean computeTotalInvoked() {
		return computeTotalInvoked.get();
	}

	public boolean checkoutInvoked() {
		return checkoutInvoked.get();
	}

	public boolean showRecommendationsInvoked() {
		return showRecommendationsInvoked.get();
	}

	public String currentCustomerId() {
		return currentCustomerId;
	}

	public boolean requestFeedbackInvoked() {
		return requestFeedbackInvoked.get();
	}

	public boolean setBudgetInvoked() {
		return setBudgetInvoked.get();
	}

	public boolean startMissionInvoked() {
		return startMissionInvoked.get();
	}

	public boolean reviewMissionPlanInvoked() {
		return reviewMissionPlanInvoked.get();
	}

	public ShoppingSession getCurrentSession() {
		return currentSession;
	}

	public AddItemRequest lastAddItem() {
		return lastAddItem;
	}

	public String lastWarning() {
		return lastWarning;
	}

	public List<Product> lastAlternatives() {
		return lastAlternatives;
	}

	public Map<String, Integer> getBasketState() {
		return new HashMap<>(basket);
	}

	/**
	 * Get basket state with product names instead of SKUs.
	 */
	public Map<String, Integer> getBasketStateByName() {
		Map<String, Integer> namedBasket = new HashMap<>();
		for (Map.Entry<String, Integer> entry : basket.entrySet()) {
			storeApi.findProductBySku(entry.getKey())
					.ifPresent(p -> namedBasket.put(p.name(), entry.getValue()));
		}
		return namedBasket;
	}

	private String formatAlternatives(List<Product> alternatives) {
		if (alternatives == null || alternatives.isEmpty()) {
			return "";
		}
		String altNames = alternatives.stream()
				.map(Product::name)
				.collect(Collectors.joining(", "));
		return String.format(" Consider instead: %s.", altNames);
	}
}

