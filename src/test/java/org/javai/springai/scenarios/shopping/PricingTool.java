package org.javai.springai.scenarios.shopping;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.javai.springai.scenarios.shopping.store.MockStoreApi;
import org.javai.springai.scenarios.shopping.store.model.AppliedDiscount;
import org.javai.springai.scenarios.shopping.store.model.LineItem;
import org.javai.springai.scenarios.shopping.store.model.PricingBreakdown;
import org.javai.springai.scenarios.shopping.store.model.Product;
import org.javai.springai.scenarios.shopping.store.model.SpecialOffer;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Tool for calculating prices and checking applicable offers.
 * Provides the assistant with pricing capabilities for basket operations.
 */
public class PricingTool {

	private final MockStoreApi storeApi;
	private final AtomicBoolean calculateTotalInvoked = new AtomicBoolean(false);
	private final AtomicBoolean getApplicableOffersInvoked = new AtomicBoolean(false);
	private final AtomicBoolean estimateCostInvoked = new AtomicBoolean(false);

	public PricingTool(MockStoreApi storeApi) {
		this.storeApi = storeApi;
	}

	@Tool(name = "calculateBasketTotal", description = """
			Calculate the total cost of items in a basket including any applicable discounts.
			Provide a comma-separated list of items in format 'product:quantity'.
			Example: 'Coke Zero:3, Sea Salt Crisps:2'""")
	public String calculateBasketTotal(
			@ToolParam(description = "Items in format 'product:quantity' separated by commas") String basketItems) {

		calculateTotalInvoked.set(true);

		Map<String, Integer> basket = parseBasketItems(basketItems);
		if (basket.isEmpty()) {
			return "No valid items specified. Use format: 'Product Name:quantity, Another Product:quantity'";
		}

		PricingBreakdown pricing = storeApi.calculateTotalByName(basket);

		if (pricing.items().isEmpty()) {
			return "No matching products found for the specified items.";
		}

		return formatPricingBreakdown(pricing);
	}

	@Tool(name = "getProductPrice", description = """
			Get the price of a specific product.
			Returns the unit price and any applicable offers.""")
	public String getProductPrice(
			@ToolParam(description = "The product name") String productName) {

		return storeApi.findProduct(productName)
				.map(product -> {
					StringBuilder sb = new StringBuilder();
					sb.append(String.format("**%s**\n", product.name()));
					sb.append(String.format("Price: £%.2f per %s\n", product.unitPrice(), product.unit()));

					// Check for applicable offers
					List<SpecialOffer> offers = storeApi.getApplicableOffers(Set.of(product.sku()));
					if (!offers.isEmpty()) {
						sb.append("Current offers:\n");
						for (SpecialOffer offer : offers) {
							sb.append(String.format("  - %s: %s\n", offer.name(), offer.description()));
						}
					}

					return sb.toString();
				})
				.orElse(String.format("Product '%s' not found.", productName));
	}

	@Tool(name = "estimateCost", description = """
			Estimate the cost for a product and quantity.
			Shows price before and after any applicable discounts.""")
	public String estimateCost(
			@ToolParam(description = "The product name") String productName,
			@ToolParam(description = "The quantity") int quantity) {

		estimateCostInvoked.set(true);

		return storeApi.findProduct(productName)
				.map(product -> {
					BigDecimal lineTotal = product.unitPrice().multiply(BigDecimal.valueOf(quantity));

					// Calculate with discounts
					Map<String, Integer> singleItemBasket = Map.of(product.sku(), quantity);
					PricingBreakdown pricing = storeApi.calculateTotal(singleItemBasket);

					StringBuilder sb = new StringBuilder();
					sb.append(String.format("%d × %s @ £%.2f = £%.2f\n",
							quantity, product.name(), product.unitPrice(), lineTotal));

					if (pricing.totalDiscount().compareTo(BigDecimal.ZERO) > 0) {
						for (AppliedDiscount discount : pricing.discounts()) {
							sb.append(String.format("  Discount (%s): -£%.2f\n",
									discount.offer().name(), discount.discountAmount()));
						}
						sb.append(String.format("**Total after discount: £%.2f**", pricing.total()));
					} else {
						sb.append(String.format("**Total: £%.2f**", pricing.total()));
					}

					return sb.toString();
				})
				.orElse(String.format("Product '%s' not found.", productName));
	}

	@Tool(name = "getApplicableOffers", description = """
			Get all offers that would apply to a set of products.
			Useful for showing customers potential savings before adding to basket.""")
	public String getApplicableOffers(
			@ToolParam(description = "Comma-separated list of product names") String productNames) {

		getApplicableOffersInvoked.set(true);

		Set<String> skus = Set.of(productNames.split("\\s*,\\s*")).stream()
				.map(name -> storeApi.findProduct(name.trim()))
				.filter(opt -> opt.isPresent())
				.map(opt -> opt.get().sku())
				.collect(Collectors.toSet());

		if (skus.isEmpty()) {
			return "No valid products specified.";
		}

		List<SpecialOffer> offers = storeApi.getApplicableOffers(skus);

		if (offers.isEmpty()) {
			return "No special offers currently apply to these products.";
		}

		StringBuilder sb = new StringBuilder("Applicable offers:\n");
		for (SpecialOffer offer : offers) {
			sb.append(String.format("- **%s**: %s (%s)\n",
					offer.name(), offer.description(), formatDiscountValue(offer)));
		}

		return sb.toString();
	}

	@Tool(name = "comparePrices", description = """
			Compare prices for multiple products to help customers make choices.
			Returns a comparison table with prices and value indicators.""")
	public String comparePrices(
			@ToolParam(description = "Comma-separated list of product names to compare") String productNames) {

		List<Product> products = Set.of(productNames.split("\\s*,\\s*")).stream()
				.map(name -> storeApi.findProduct(name.trim()))
				.filter(opt -> opt.isPresent())
				.map(opt -> opt.get())
				.collect(Collectors.toList());

		if (products.isEmpty()) {
			return "No valid products found for comparison.";
		}

		StringBuilder sb = new StringBuilder("Price comparison:\n");
		for (Product product : products) {
			List<SpecialOffer> offers = storeApi.getApplicableOffers(Set.of(product.sku()));
			String offerTag = offers.isEmpty() ? "" : " 🏷️";

			sb.append(String.format("- %s: £%.2f/%s%s\n",
					product.name(), product.unitPrice(), product.unit(), offerTag));
		}

		if (products.stream().anyMatch(p -> !storeApi.getApplicableOffers(Set.of(p.sku())).isEmpty())) {
			sb.append("\n🏷️ = has active discount");
		}

		return sb.toString();
	}

	@Tool(name = "calculateSavings", description = """
			Calculate how much a customer would save with current offers on their basket.
			Helps highlight the value of current promotions.""")
	public String calculateSavings(
			@ToolParam(description = "Items in format 'product:quantity' separated by commas") String basketItems) {

		Map<String, Integer> basket = parseBasketItems(basketItems);
		if (basket.isEmpty()) {
			return "No valid items specified.";
		}

		PricingBreakdown pricing = storeApi.calculateTotalByName(basket);

		if (pricing.items().isEmpty()) {
			return "No matching products found.";
		}

		if (pricing.totalDiscount().compareTo(BigDecimal.ZERO) == 0) {
			return String.format("No discounts apply to this basket. Total: £%.2f", pricing.subtotal());
		}

		StringBuilder sb = new StringBuilder();
		sb.append("Your savings today:\n");

		for (AppliedDiscount discount : pricing.discounts()) {
			sb.append(String.format("- %s: £%.2f saved\n",
					discount.offer().name(), discount.discountAmount()));
		}

		sb.append(String.format("\n**Total savings: £%.2f**\n", pricing.totalDiscount()));
		sb.append(String.format("Basket total: £%.2f (was £%.2f)", pricing.total(), pricing.subtotal()));

		return sb.toString();
	}

	// ========== Helper Methods ==========

	private Map<String, Integer> parseBasketItems(String basketItems) {
		Map<String, Integer> basket = new HashMap<>();

		if (basketItems == null || basketItems.isBlank()) {
			return basket;
		}

		for (String item : basketItems.split("\\s*,\\s*")) {
			String[] parts = item.split("\\s*:\\s*");
			if (parts.length == 2) {
				try {
					String productName = parts[0].trim();
					int quantity = Integer.parseInt(parts[1].trim());
					if (!productName.isEmpty() && quantity > 0) {
						basket.merge(productName, quantity, Integer::sum);
					}
				} catch (NumberFormatException e) {
					// Skip invalid quantity
				}
			}
		}

		return basket;
	}

	private String formatPricingBreakdown(PricingBreakdown pricing) {
		StringBuilder sb = new StringBuilder();
		sb.append("Basket breakdown:\n");

		for (LineItem item : pricing.items()) {
			sb.append(String.format("- %d × %s @ £%.2f = £%.2f\n",
					item.quantity(), item.product().name(),
					item.unitPrice(), item.lineTotal()));
		}

		sb.append(String.format("\nSubtotal: £%.2f", pricing.subtotal()));

		if (!pricing.discounts().isEmpty()) {
			sb.append("\n\nDiscounts applied:");
			for (AppliedDiscount discount : pricing.discounts()) {
				sb.append(String.format("\n- %s: -£%.2f",
						discount.offer().name(), discount.discountAmount()));
			}
			sb.append(String.format("\nTotal savings: £%.2f", pricing.totalDiscount()));
		}

		sb.append(String.format("\n\n**Total: £%.2f**", pricing.total()));

		return sb.toString();
	}

	private String formatDiscountValue(SpecialOffer offer) {
		return switch (offer.type()) {
			case PERCENTAGE -> String.format("%.0f%% off", offer.discountValue());
			case FIXED_AMOUNT -> String.format("£%.2f off", offer.discountValue());
			case BUY_X_GET_Y -> String.format("Buy %d get 1 free", offer.discountValue().intValue());
		};
	}

	// ========== Test Assertion Helpers ==========

	public boolean calculateTotalInvoked() {
		return calculateTotalInvoked.get();
	}

	public boolean getApplicableOffersInvoked() {
		return getApplicableOffersInvoked.get();
	}

	public boolean estimateCostInvoked() {
		return estimateCostInvoked.get();
	}
}

