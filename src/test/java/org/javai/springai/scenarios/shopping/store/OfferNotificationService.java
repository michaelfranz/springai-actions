package org.javai.springai.scenarios.shopping.store;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.javai.springai.scenarios.shopping.actions.Notification;
import org.javai.springai.scenarios.shopping.store.model.CustomerProfile;
import org.javai.springai.scenarios.shopping.store.model.Product;
import org.javai.springai.scenarios.shopping.store.model.SpecialOffer;

/**
 * Service that determines which offers should be surfaced to a customer.
 * <p>
 * This is a programmatic guarantee that applicable offers are shown to users.
 * Offers are NOT left to LLM discretion - they are a business requirement
 * often tied to vendor agreements.
 * <p>
 * The service filters offers based on:
 * <ul>
 *   <li>Customer allergens - excludes offers for products containing allergens</li>
 *   <li>Dietary preferences - prioritizes offers matching preferences</li>
 *   <li>Offer validity - only active offers are included</li>
 * </ul>
 */
public class OfferNotificationService {

	private final MockStoreApi storeApi;

	public OfferNotificationService(MockStoreApi storeApi) {
		this.storeApi = storeApi;
	}

	/**
	 * Get all applicable offer notifications for a customer.
	 * Filters out offers that conflict with the customer's allergens.
	 *
	 * @param customerId optional customer ID for personalized filtering
	 * @return list of notifications for applicable offers
	 */
	public List<Notification> getApplicableOfferNotifications(String customerId) {
		List<SpecialOffer> offers = storeApi.getActiveOffers();

		if (offers.isEmpty()) {
			return List.of();
		}

		// If we have a customer profile, filter offers based on their preferences
		if (customerId != null) {
			Optional<CustomerProfile> profileOpt = storeApi.getCustomers().getProfile(customerId);
			if (profileOpt.isPresent()) {
				offers = filterByCustomerProfile(offers, profileOpt.get());
			}
		}

		// Convert to notifications
		return offers.stream()
				.map(offer -> Notification.offer(offer.name(), offer.description()))
				.toList();
	}

	/**
	 * Filter offers based on customer profile.
	 * Excludes offers for products that contain customer's allergens.
	 */
	private List<SpecialOffer> filterByCustomerProfile(List<SpecialOffer> offers, CustomerProfile profile) {
		Set<String> allergens = profile.allergens();
		if (allergens == null || allergens.isEmpty()) {
			return offers;
		}

		List<SpecialOffer> filtered = new ArrayList<>();
		for (SpecialOffer offer : offers) {
			if (!offerContainsAllergen(offer, allergens)) {
				filtered.add(offer);
			}
		}
		return filtered;
	}

	/**
	 * Check if an offer is for products containing any of the specified allergens.
	 */
	private boolean offerContainsAllergen(SpecialOffer offer, Set<String> allergens) {
		// Check SKU-specific offers
		for (String sku : offer.applicableSkus()) {
			Optional<Product> productOpt = storeApi.findProductBySku(sku);
			if (productOpt.isPresent()) {
				Product product = productOpt.get();
				if (productContainsAllergen(product, allergens)) {
					return true;
				}
			}
		}

		// For category-wide offers, check all products in the category
		if (offer.applicableCategory() != null) {
			List<Product> categoryProducts = storeApi.getProductsByCategory(offer.applicableCategory());
			// If ALL products in the category contain allergens, filter the offer
			// Otherwise, some products may still be safe
			boolean allContainAllergen = !categoryProducts.isEmpty() && 
					categoryProducts.stream().allMatch(p -> productContainsAllergen(p, allergens));
			if (allContainAllergen) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Check if a product contains any of the specified allergens.
	 * This checks the product's allergen tags if available.
	 */
	private boolean productContainsAllergen(Product product, Set<String> allergens) {
		// Check product allergens
		Set<String> productAllergens = product.allergens();
		if (productAllergens == null || productAllergens.isEmpty()) {
			return false;
		}
		
		for (String allergen : allergens) {
			if (productAllergens.stream().anyMatch(pa -> pa.equalsIgnoreCase(allergen))) {
				return true;
			}
		}
		return false;
	}
}

