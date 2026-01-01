package org.javai.springai.scenarios.shopping.store;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.javai.springai.scenarios.shopping.store.model.AppliedDiscount;
import org.javai.springai.scenarios.shopping.store.model.DiscountType;
import org.javai.springai.scenarios.shopping.store.model.LineItem;
import org.javai.springai.scenarios.shopping.store.model.PricingBreakdown;
import org.javai.springai.scenarios.shopping.store.model.Product;
import org.javai.springai.scenarios.shopping.store.model.SpecialOffer;

/**
 * Service for calculating prices and applying discounts.
 */
public class PricingService {

	private final ProductCatalog catalog;
	private final Map<String, SpecialOffer> offers = new HashMap<>();

	public PricingService(ProductCatalog catalog) {
		this.catalog = catalog;
		initializeOffers();
	}

	/**
	 * Get the unit price for a product by SKU.
	 */
	public Optional<BigDecimal> getUnitPrice(String sku) {
		return catalog.findBySku(sku).map(Product::unitPrice);
	}

	/**
	 * Calculate the line total for a product and quantity (before discounts).
	 */
	public Optional<BigDecimal> calculateLineTotal(String sku, int quantity) {
		return getUnitPrice(sku).map(price -> price.multiply(BigDecimal.valueOf(quantity)));
	}

	/**
	 * Get all currently active special offers.
	 */
	public List<SpecialOffer> getActiveOffers() {
		return new ArrayList<>(offers.values());
	}

	/**
	 * Get offers applicable to a specific set of product SKUs.
	 */
	public List<SpecialOffer> getApplicableOffers(Set<String> basketSkus) {
		List<SpecialOffer> applicable = new ArrayList<>();

		for (String sku : basketSkus) {
			Optional<Product> productOpt = catalog.findBySku(sku);
			if (productOpt.isPresent()) {
				Product product = productOpt.get();
				for (SpecialOffer offer : offers.values()) {
					if (offer.appliesTo(product) && !applicable.contains(offer)) {
						applicable.add(offer);
					}
				}
			}
		}

		return applicable;
	}

	/**
	 * Calculate complete pricing breakdown for a basket.
	 * The basket maps product SKU to quantity.
	 */
	public PricingBreakdown calculateBasketTotal(Map<String, Integer> basket) {
		if (basket == null || basket.isEmpty()) {
			return PricingBreakdown.empty();
		}

		List<LineItem> lineItems = new ArrayList<>();
		BigDecimal subtotal = BigDecimal.ZERO;

		// Build line items
		for (Map.Entry<String, Integer> entry : basket.entrySet()) {
			String sku = entry.getKey();
			int quantity = entry.getValue();

			Optional<Product> productOpt = catalog.findBySku(sku);
			if (productOpt.isPresent()) {
				LineItem item = LineItem.of(productOpt.get(), quantity);
				lineItems.add(item);
				subtotal = subtotal.add(item.lineTotal());
			}
		}

		// Calculate discounts
		List<AppliedDiscount> appliedDiscounts = calculateDiscounts(lineItems);
		BigDecimal totalDiscount = appliedDiscounts.stream()
				.map(AppliedDiscount::discountAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		BigDecimal total = subtotal.subtract(totalDiscount).setScale(2, RoundingMode.HALF_UP);

		return new PricingBreakdown(lineItems, subtotal, appliedDiscounts, totalDiscount, total);
	}

	/**
	 * Calculate pricing breakdown for a basket using product names (for natural language input).
	 * The basket maps product name to quantity.
	 */
	public PricingBreakdown calculateBasketTotalByName(Map<String, Integer> basketByName) {
		Map<String, Integer> basketBySku = new HashMap<>();

		for (Map.Entry<String, Integer> entry : basketByName.entrySet()) {
			String productName = entry.getKey();
			int quantity = entry.getValue();

			catalog.findByName(productName).ifPresent(product ->
					basketBySku.merge(product.sku(), quantity, Integer::sum)
			);
		}

		return calculateBasketTotal(basketBySku);
	}

	/**
	 * Get a specific offer by ID.
	 */
	public Optional<SpecialOffer> getOffer(String offerId) {
		return Optional.ofNullable(offers.get(offerId));
	}

	private List<AppliedDiscount> calculateDiscounts(List<LineItem> lineItems) {
		List<AppliedDiscount> applied = new ArrayList<>();

		for (LineItem item : lineItems) {
			Product product = item.product();

			for (SpecialOffer offer : offers.values()) {
				if (offer.appliesTo(product)) {
					BigDecimal discountAmount = calculateDiscountAmount(offer, item);
					if (discountAmount.compareTo(BigDecimal.ZERO) > 0) {
						applied.add(new AppliedDiscount(offer, product.sku(), discountAmount));
					}
				}
			}
		}

		return applied;
	}

	private BigDecimal calculateDiscountAmount(SpecialOffer offer, LineItem item) {
		return switch (offer.type()) {
			case PERCENTAGE -> item.lineTotal()
					.multiply(offer.discountValue())
					.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

			case FIXED_AMOUNT -> offer.discountValue()
					.multiply(BigDecimal.valueOf(item.quantity()))
					.min(item.lineTotal()); // Don't discount more than line total

			case BUY_X_GET_Y -> {
				// Buy X get 1 free: every (X+1) items, one is free
				int buyX = offer.discountValue().intValue();
				int freeItems = item.quantity() / (buyX + 1);
				yield item.unitPrice().multiply(BigDecimal.valueOf(freeItems));
			}
		};
	}

	private void initializeOffers() {
		// Summer Refresh - 10% off Coca Cola and Coke Zero
		offers.put("OFFER-001", new SpecialOffer(
				"OFFER-001",
				"Summer Refresh",
				"10% off Coca Cola and Coke Zero",
				Set.of("BEV-001", "BEV-002"),
				null,
				DiscountType.PERCENTAGE,
				BigDecimal.valueOf(10)
		));

		// Party Pack - 15% off all snacks category
		offers.put("OFFER-002", new SpecialOffer(
				"OFFER-002",
				"Party Pack",
				"15% off all snacks",
				Set.of(),
				"snacks",
				DiscountType.PERCENTAGE,
				BigDecimal.valueOf(15)
		));

		// Dairy Deal - £0.20 off milk and yogurt per item
		offers.put("OFFER-003", new SpecialOffer(
				"OFFER-003",
				"Dairy Deal",
				"20p off milk and yogurt",
				Set.of("DAI-001", "DAI-003"),
				null,
				DiscountType.FIXED_AMOUNT,
				new BigDecimal("0.20")
		));

		// Bakery Bundle - Buy 2 get 1 free on croissants
		offers.put("OFFER-004", new SpecialOffer(
				"OFFER-004",
				"Bakery Bundle",
				"Buy 2 get 1 free on croissants",
				Set.of("BAK-002"),
				null,
				DiscountType.BUY_X_GET_Y,
				BigDecimal.valueOf(2)
		));

		// Healthy Choice - 10% off produce
		offers.put("OFFER-005", new SpecialOffer(
				"OFFER-005",
				"Healthy Choice",
				"10% off fresh produce",
				Set.of(),
				"produce",
				DiscountType.PERCENTAGE,
				BigDecimal.valueOf(10)
		));

		// Party Platter Special - 20% off party items
		offers.put("OFFER-006", new SpecialOffer(
				"OFFER-006",
				"Party Platter Special",
				"20% off party platters and boards",
				Set.of(),
				"party",
				DiscountType.PERCENTAGE,
				BigDecimal.valueOf(20)
		));
	}
}

