package org.javai.springai.scenarios.shopping.actions;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.api.ActionParam;

/**
 * Actions supporting a shopping application scenario.
 * Manages a shopping basket with items, quantities, and session lifecycle.
 */
public class ShoppingActions {
	private final AtomicBoolean startSessionInvoked = new AtomicBoolean(false);
	private final AtomicBoolean presentOffersInvoked = new AtomicBoolean(false);
	private final AtomicBoolean addItemInvoked = new AtomicBoolean(false);
	private final AtomicBoolean addPartySnacksInvoked = new AtomicBoolean(false);
	private final AtomicBoolean computeTotalInvoked = new AtomicBoolean(false);
	private final AtomicBoolean checkoutInvoked = new AtomicBoolean(false);
	private final AtomicBoolean requestFeedbackInvoked = new AtomicBoolean(false);
	private final AtomicBoolean viewBasketInvoked = new AtomicBoolean(false);
	private final AtomicBoolean removeItemInvoked = new AtomicBoolean(false);
	private AddItemRequest lastAddItem;
	private final Map<String, Integer> basket = new HashMap<>();

	@Action(description = """
			Start or reset a shopping session and basket.""")
	public void startSession(ActionContext context) {
		startSessionInvoked.set(true);
		basket.clear();
		context.put("basket", basket);
	}

	@Action(description = "Present current special offers to the shopper.")
	public void presentOffers() {
		presentOffersInvoked.set(true);
	}

	@Action(description = """
			Add a product and quantity to the current basket.""")
	public void addItem(
			@ActionParam(description = "Product name") String product,
			@ActionParam(description = "Quantity", allowedRegex = "[0-9]+") int quantity) {
		addItemInvoked.set(true);
		lastAddItem = new AddItemRequest(product, quantity);
		basket.merge(product, quantity, (existing, add) -> existing + add);
	}

	@Action(description = """
			Add snacks for a party of a given size (e.g., crisps and nuts).""")
	public void addPartySnacks(
			@ActionParam(description = "Party size", allowedRegex = "[0-9]+") int partySize) {
		addPartySnacksInvoked.set(true);
		basket.merge("crisps (party)", partySize, (existing, add) -> existing + add);
		basket.merge("nuts (party)", partySize, (existing, add) -> existing + add);
	}

	@Action(description = """
			View the current contents of the shopping basket.""")
	public void viewBasketSummary() {
		viewBasketInvoked.set(true);
	}

	@Action(description = """
			Remove a product from the current basket.""")
	public void removeItem(
			@ActionParam(description = "Product name to remove") String product) {
		removeItemInvoked.set(true);
		basket.remove(product);
	}

	@Action(description = """
			Compute or retrieve the basket total.""")
	public void computeTotal() {
		computeTotalInvoked.set(true);
	}

	@Action(description = """
			Checkout the basket and end the shopping session.""")
	public void checkoutBasket() {
		checkoutInvoked.set(true);
	}

	@Action(description = "Request end-of-session feedback from the shopper.")
	public void requestFeedback() {
		requestFeedbackInvoked.set(true);
	}

	public boolean startSessionInvoked() {
		return startSessionInvoked.get();
	}

	public boolean presentOffersInvoked() {
		return presentOffersInvoked.get();
	}

	public boolean addItemInvoked() {
		return addItemInvoked.get();
	}

	public boolean addPartySnacksInvoked() {
		return addPartySnacksInvoked.get();
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

	public boolean viewBasketInvoked() {
		return viewBasketInvoked.get();
	}

	public boolean removeItemInvoked() {
		return removeItemInvoked.get();
	}

	public AddItemRequest lastAddItem() {
		return lastAddItem;
	}

	public Map<String, Integer> getBasketState() {
		return new HashMap<>(basket);
	}
}

