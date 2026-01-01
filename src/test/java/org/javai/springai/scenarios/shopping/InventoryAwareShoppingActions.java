package org.javai.springai.scenarios.shopping;

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
import org.javai.springai.scenarios.shopping.store.MockStoreApi;
import org.javai.springai.scenarios.shopping.store.model.AvailabilityResult;
import org.javai.springai.scenarios.shopping.store.model.LineItem;
import org.javai.springai.scenarios.shopping.store.model.PricingBreakdown;
import org.javai.springai.scenarios.shopping.store.model.Product;

/**
 * Enhanced shopping actions with inventory awareness.
 * Validates stock levels before adding items and provides warnings/alternatives.
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

	// Basket state: maps product SKU to quantity
	private final Map<String, Integer> basket = new HashMap<>();
	private String lastWarning;
	private List<Product> lastAlternatives;
	private AddItemRequest lastAddItem;

	public InventoryAwareShoppingActions(MockStoreApi storeApi) {
		this.storeApi = storeApi;
	}

	@Action(description = """
			Start or reset a shopping session and basket.
			Clears any existing basket contents.""")
	public ActionResult startSession(ActionContext context) {
		startSessionInvoked.set(true);
		basket.clear();
		lastWarning = null;
		lastAlternatives = null;
		if (context != null) {
			context.put("basket", basket);
		}
		storeApi.reset(); // Reset any stock reservations
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
			Commits the stock reservations.""")
	public ActionResult checkoutBasket() {
		checkoutInvoked.set(true);

		if (basket.isEmpty()) {
			return ActionResult.error("Cannot checkout - your basket is empty.");
		}

		PricingBreakdown pricing = storeApi.calculateTotal(basket);
		storeApi.commitReservations(basket);

		String summary = String.format("Order complete! Total: £%.2f for %d item(s).",
				pricing.total(), basket.size());

		basket.clear();
		return ActionResult.success(summary);
	}

	@Action(description = "Request end-of-session feedback from the shopper.")
	public ActionResult requestFeedback() {
		requestFeedbackInvoked.set(true);
		return ActionResult.success("Thank you for shopping with us! How was your experience today?");
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

	public boolean requestFeedbackInvoked() {
		return requestFeedbackInvoked.get();
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

