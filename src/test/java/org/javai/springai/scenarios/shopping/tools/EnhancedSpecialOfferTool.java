package org.javai.springai.scenarios.shopping.tools;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.javai.springai.scenarios.shopping.store.MockStoreApi;
import org.javai.springai.scenarios.shopping.store.model.SpecialOffer;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Enhanced tool for accessing special offers and discounts.
 * Uses the PricingService to provide real-time offer information.
 */
public class EnhancedSpecialOfferTool {

	private final MockStoreApi storeApi;
	private final AtomicBoolean listInvoked = new AtomicBoolean(false);
	private final AtomicBoolean getApplicableInvoked = new AtomicBoolean(false);

	public EnhancedSpecialOfferTool(MockStoreApi storeApi) {
		this.storeApi = storeApi;
	}

	@Tool(name = "listSpecialOffers", description = """
			List all current special offers and discounts available in the store.
			Use this to inform customers about deals before they start shopping.""")
	public String listSpecialOffers() {
		listInvoked.set(true);

		List<SpecialOffer> offers = storeApi.getActiveOffers();

		if (offers.isEmpty()) {
			return "No special offers available at the moment.";
		}

		StringBuilder sb = new StringBuilder("Today's special offers:\n");
		for (SpecialOffer offer : offers) {
			sb.append(String.format("- **%s**: %s\n", offer.name(), offer.description()));
		}

		return sb.toString();
	}

	@Tool(name = "getOffersForProducts", description = """
			Get special offers that apply to specific products in a basket.
			Use this to show customers relevant deals for items they're considering.""")
	public String getOffersForProducts(
			@ToolParam(description = "Comma-separated list of product names") String productNames) {

		getApplicableInvoked.set(true);

		// Convert product names to SKUs
		Set<String> skus = Set.of(productNames.split("\\s*,\\s*")).stream()
				.map(name -> storeApi.findProduct(name.trim()))
				.filter(opt -> opt.isPresent())
				.map(opt -> opt.get().sku())
				.collect(Collectors.toSet());

		if (skus.isEmpty()) {
			return "No valid products specified.";
		}

		List<SpecialOffer> applicable = storeApi.getApplicableOffers(skus);

		if (applicable.isEmpty()) {
			return "No special offers apply to these products.";
		}

		StringBuilder sb = new StringBuilder("Applicable offers:\n");
		for (SpecialOffer offer : applicable) {
			sb.append(String.format("- **%s**: %s\n", offer.name(), offer.description()));
		}

		return sb.toString();
	}

	@Tool(name = "getOfferDetails", description = """
			Get detailed information about a specific offer by name.""")
	public String getOfferDetails(
			@ToolParam(description = "The offer name (e.g., 'Summer Refresh', 'Party Pack')") String offerName) {

		List<SpecialOffer> offers = storeApi.getActiveOffers();

		return offers.stream()
				.filter(o -> o.name().equalsIgnoreCase(offerName.trim()))
				.findFirst()
				.map(offer -> {
					StringBuilder sb = new StringBuilder();
					sb.append(String.format("**%s**\n", offer.name()));
					sb.append(String.format("%s\n", offer.description()));
					sb.append(String.format("Discount type: %s\n", offer.type()));
					sb.append(String.format("Value: %s", formatDiscountValue(offer)));

					if (!offer.applicableSkus().isEmpty()) {
						String products = offer.applicableSkus().stream()
								.map(sku -> storeApi.findProductBySku(sku))
								.filter(opt -> opt.isPresent())
								.map(opt -> opt.get().name())
								.collect(Collectors.joining(", "));
						sb.append(String.format("\nApplies to: %s", products));
					} else if (offer.applicableCategory() != null) {
						sb.append(String.format("\nApplies to: All %s products", offer.applicableCategory()));
					}

					return sb.toString();
				})
				.orElse(String.format("Offer '%s' not found.", offerName));
	}

	private String formatDiscountValue(SpecialOffer offer) {
		return switch (offer.type()) {
			case PERCENTAGE -> String.format("%.0f%% off", offer.discountValue());
			case FIXED_AMOUNT -> String.format("£%.2f off", offer.discountValue());
			case BUY_X_GET_Y -> String.format("Buy %d get 1 free", offer.discountValue().intValue());
		};
	}

	// Test assertion helpers
	public boolean listInvoked() {
		return listInvoked.get();
	}

	public boolean getApplicableInvoked() {
		return getApplicableInvoked.get();
	}
}

