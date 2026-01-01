package org.javai.springai.scenarios.shopping;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.javai.springai.scenarios.shopping.store.CustomerProfileService;
import org.javai.springai.scenarios.shopping.store.MockStoreApi;
import org.javai.springai.scenarios.shopping.store.model.CustomerProfile;
import org.javai.springai.scenarios.shopping.store.model.Product;
import org.javai.springai.scenarios.shopping.store.model.PurchaseHistory;
import org.javai.springai.scenarios.shopping.store.model.SpecialOffer;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Tool for accessing customer profile information and personalized recommendations.
 * Enables the assistant to provide tailored shopping experiences.
 */
public class CustomerTool {

	private final MockStoreApi storeApi;
	private final CustomerProfileService customerService;

	private final AtomicBoolean getProfileInvoked = new AtomicBoolean(false);
	private final AtomicBoolean getRecommendationsInvoked = new AtomicBoolean(false);
	private final AtomicBoolean getFrequentPurchasesInvoked = new AtomicBoolean(false);
	private final AtomicBoolean getPersonalizedOffersInvoked = new AtomicBoolean(false);

	public CustomerTool(MockStoreApi storeApi, CustomerProfileService customerService) {
		this.storeApi = storeApi;
		this.customerService = customerService;
	}

	@Tool(name = "getCustomerProfile", description = """
			Get a customer's profile including their preferences, dietary requirements,
			allergens, and favourite categories. Use this to personalize the shopping experience.""")
	public String getCustomerProfile(
			@ToolParam(description = "The customer ID") String customerId) {

		getProfileInvoked.set(true);

		Optional<CustomerProfile> profileOpt = customerService.getProfile(customerId);

		if (profileOpt.isEmpty()) {
			return String.format("Customer '%s' not found.", customerId);
		}

		CustomerProfile profile = profileOpt.get();
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("**Customer: %s** (%s)\n", profile.name(), profile.customerId()));

		if (!profile.dietaryPreferences().isEmpty()) {
			sb.append(String.format("Dietary preferences: %s\n", String.join(", ", profile.dietaryPreferences())));
		}

		if (!profile.allergens().isEmpty()) {
			sb.append(String.format("⚠️ Allergens: %s\n", String.join(", ", profile.allergens())));
		}

		if (profile.hasBudget()) {
			sb.append(String.format("Default budget: £%.2f\n", profile.defaultBudget()));
		}

		if (!profile.favouriteCategories().isEmpty()) {
			sb.append(String.format("Favourite categories: %s", String.join(", ", profile.favouriteCategories())));
		}

		return sb.toString();
	}

	@Tool(name = "getRecommendations", description = """
			Get personalized product recommendations for a customer based on their
			purchase history, preferences, and dietary requirements.
			Excludes products in current basket to avoid duplicates.""")
	public String getRecommendations(
			@ToolParam(description = "The customer ID") String customerId,
			@ToolParam(description = "Comma-separated list of product SKUs already in basket (optional)") String currentBasketSkus) {

		getRecommendationsInvoked.set(true);

		Set<String> basketSkus = currentBasketSkus == null || currentBasketSkus.isBlank()
				? Set.of()
				: Set.of(currentBasketSkus.split("\\s*,\\s*"));

		List<Product> recommendations = customerService.getRecommendations(customerId, basketSkus, 6);

		if (recommendations.isEmpty()) {
			return "No recommendations available at this time.";
		}

		Optional<CustomerProfile> profileOpt = customerService.getProfile(customerId);
		String greeting = profileOpt.map(p -> String.format("Recommendations for %s:\n", p.name()))
				.orElse("Recommended for you:\n");

		StringBuilder sb = new StringBuilder(greeting);
		for (Product product : recommendations) {
			String reason = getRecommendationReason(customerId, product);
			sb.append(String.format("- %s (£%.2f) %s\n", product.name(), product.unitPrice(), reason));
		}

		return sb.toString();
	}

	@Tool(name = "getFrequentPurchases", description = """
			Get a customer's most frequently purchased products.
			Useful for quick reordering or basket suggestions.""")
	public String getFrequentPurchases(
			@ToolParam(description = "The customer ID") String customerId) {

		getFrequentPurchasesInvoked.set(true);

		List<Product> frequent = customerService.getFrequentlyBoughtItems(customerId, 5);

		if (frequent.isEmpty()) {
			return String.format("No purchase history found for customer '%s'.", customerId);
		}

		PurchaseHistory history = customerService.getPurchaseHistory(customerId);
		java.util.Map<String, Integer> frequencies = history.getFrequentlyBoughtSkus(5);

		StringBuilder sb = new StringBuilder("Your frequently bought items:\n");
		for (Product product : frequent) {
			int count = frequencies.getOrDefault(product.sku(), 0);
			sb.append(String.format("- %s (bought %d times) - £%.2f/%s\n",
					product.name(), count, product.unitPrice(), product.unit()));
		}

		sb.append(String.format("\nTotal orders: %d | Average order: £%.2f",
				history.orders().size(), history.getAverageOrderValue()));

		return sb.toString();
	}

	@Tool(name = "getPersonalizedOffers", description = """
			Get special offers that are relevant to a customer based on their
			purchase history and favourite categories.""")
	public String getPersonalizedOffers(
			@ToolParam(description = "The customer ID") String customerId) {

		getPersonalizedOffersInvoked.set(true);

		Optional<CustomerProfile> profileOpt = customerService.getProfile(customerId);
		PurchaseHistory history = customerService.getPurchaseHistory(customerId);

		// Get SKUs from purchase history and favourite categories
		Set<String> relevantSkus = history.getAllPurchasedSkus();

		if (profileOpt.isPresent()) {
			CustomerProfile profile = profileOpt.get();
			for (String category : profile.favouriteCategories()) {
				storeApi.getProductsByCategory(category).stream()
						.map(Product::sku)
						.forEach(relevantSkus::add);
			}
		}

		if (relevantSkus.isEmpty()) {
			return "Check out our current offers with the listSpecialOffers tool!";
		}

		List<SpecialOffer> offers = storeApi.getApplicableOffers(relevantSkus);

		if (offers.isEmpty()) {
			return "No special offers currently match your preferences.";
		}

		String name = profileOpt.map(CustomerProfile::name).orElse("you");
		StringBuilder sb = new StringBuilder(String.format("Special offers for %s:\n", name));

		for (SpecialOffer offer : offers) {
			sb.append(String.format("- **%s**: %s\n", offer.name(), offer.description()));
		}

		return sb.toString();
	}

	@Tool(name = "checkProductSafety", description = """
			Check if a product is safe for a customer based on their allergen profile.
			Returns warnings if the product contains allergens the customer is sensitive to.""")
	public String checkProductSafety(
			@ToolParam(description = "The customer ID") String customerId,
			@ToolParam(description = "The product name to check") String productName) {

		Optional<CustomerProfile> profileOpt = customerService.getProfile(customerId);
		Optional<Product> productOpt = storeApi.findProduct(productName);

		if (productOpt.isEmpty()) {
			return String.format("Product '%s' not found.", productName);
		}

		Product product = productOpt.get();

		if (profileOpt.isEmpty()) {
			// No profile - just return product allergen info
			if (product.allergens().isEmpty()) {
				return String.format("%s contains no known allergens.", product.name());
			}
			return String.format("%s contains: %s", product.name(), String.join(", ", product.allergens()));
		}

		CustomerProfile profile = profileOpt.get();

		if (profile.allergens().isEmpty()) {
			return String.format("%s is safe for %s (no allergen restrictions).", product.name(), profile.name());
		}

		// Check for matches
		Set<String> matches = profile.allergens().stream()
				.filter(a -> product.allergens().contains(a.toLowerCase()))
				.collect(Collectors.toSet());

		if (matches.isEmpty()) {
			return String.format("✓ %s is safe for %s.", product.name(), profile.name());
		}

		return String.format("⚠️ WARNING: %s contains %s which %s is allergic to!",
				product.name(), String.join(", ", matches), profile.name());
	}

	@Tool(name = "getSimilarProducts", description = """
			Get products similar to what the customer usually buys.
			Useful for suggesting new products they might like.""")
	public String getSimilarProducts(
			@ToolParam(description = "The customer ID") String customerId) {

		List<Product> similar = customerService.getSimilarToUsual(customerId, 5);

		if (similar.isEmpty()) {
			return "Not enough purchase history to suggest similar products.";
		}

		Optional<CustomerProfile> profileOpt = customerService.getProfile(customerId);
		String intro = profileOpt.map(p -> String.format("Products similar to %s's favourites:\n", p.name()))
				.orElse("Products you might like:\n");

		StringBuilder sb = new StringBuilder(intro);
		for (Product product : similar) {
			sb.append(String.format("- %s (£%.2f/%s)\n", product.name(), product.unitPrice(), product.unit()));
		}

		return sb.toString();
	}

	private String getRecommendationReason(String customerId, Product product) {
		PurchaseHistory history = customerService.getPurchaseHistory(customerId);
		Optional<CustomerProfile> profileOpt = customerService.getProfile(customerId);

		if (history.hasPurchased(product.sku())) {
			return "🔄 bought before";
		}

		if (profileOpt.isPresent()) {
			CustomerProfile profile = profileOpt.get();

			if (profile.favouriteCategories().contains(product.category())) {
				return "⭐ in your favourite category";
			}

			for (String pref : profile.dietaryPreferences()) {
				if (product.hasDietaryFlag(pref)) {
					return "🥗 " + pref;
				}
			}
		}

		return "";
	}

	// ========== Test Assertion Helpers ==========

	public boolean getProfileInvoked() {
		return getProfileInvoked.get();
	}

	public boolean getRecommendationsInvoked() {
		return getRecommendationsInvoked.get();
	}

	public boolean getFrequentPurchasesInvoked() {
		return getFrequentPurchasesInvoked.get();
	}

	public boolean getPersonalizedOffersInvoked() {
		return getPersonalizedOffersInvoked.get();
	}
}

