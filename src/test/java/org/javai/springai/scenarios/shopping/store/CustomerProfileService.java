package org.javai.springai.scenarios.shopping.store;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.javai.springai.scenarios.shopping.store.model.CustomerProfile;
import org.javai.springai.scenarios.shopping.store.model.Product;
import org.javai.springai.scenarios.shopping.store.model.PurchaseHistory;
import org.javai.springai.scenarios.shopping.store.model.PurchaseHistory.PastOrder;

/**
 * Service for managing customer profiles and purchase history.
 * Provides personalisation capabilities including recommendations.
 */
public class CustomerProfileService {

	private final ProductCatalog catalog;
	private final Map<String, CustomerProfile> profiles = new HashMap<>();
	private final Map<String, PurchaseHistory> histories = new HashMap<>();

	public CustomerProfileService(ProductCatalog catalog) {
		this.catalog = catalog;
		initializeSampleCustomers();
	}

	// ========== Profile Management ==========

	/**
	 * Get a customer profile by ID.
	 */
	public Optional<CustomerProfile> getProfile(String customerId) {
		return Optional.ofNullable(profiles.get(customerId));
	}

	/**
	 * Create or update a customer profile.
	 */
	public void saveProfile(CustomerProfile profile) {
		profiles.put(profile.customerId(), profile);
		// Ensure purchase history exists
		histories.computeIfAbsent(profile.customerId(), PurchaseHistory::empty);
	}

	/**
	 * Get all customer IDs.
	 */
	public Set<String> getAllCustomerIds() {
		return Set.copyOf(profiles.keySet());
	}

	// ========== Purchase History ==========

	/**
	 * Get purchase history for a customer.
	 */
	public PurchaseHistory getPurchaseHistory(String customerId) {
		return histories.getOrDefault(customerId, PurchaseHistory.empty(customerId));
	}

	/**
	 * Record a new purchase for a customer.
	 */
	public void recordPurchase(String customerId, Map<String, Integer> items, BigDecimal total) {
		String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
		PastOrder order = new PastOrder(orderId, Instant.now(), new HashMap<>(items), total);

		PurchaseHistory current = histories.getOrDefault(customerId, PurchaseHistory.empty(customerId));
		histories.put(customerId, current.withOrder(order));
	}

	// ========== Recommendations ==========

	/**
	 * Get frequently bought items for a customer.
	 *
	 * @param customerId Customer ID
	 * @param limit Maximum items to return
	 * @return List of products frequently purchased
	 */
	public List<Product> getFrequentlyBoughtItems(String customerId, int limit) {
		PurchaseHistory history = getPurchaseHistory(customerId);
		Map<String, Integer> frequentSkus = history.getFrequentlyBoughtSkus(limit);

		return frequentSkus.keySet().stream()
				.map(catalog::findBySku)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.collect(Collectors.toList());
	}

	/**
	 * Get personalized product recommendations based on:
	 * - Purchase history
	 * - Dietary preferences
	 * - Allergen restrictions
	 * - Favourite categories
	 *
	 * @param customerId Customer ID
	 * @param currentBasket Current basket SKUs to avoid recommending duplicates
	 * @param limit Maximum recommendations
	 * @return List of recommended products
	 */
	public List<Product> getRecommendations(String customerId, Set<String> currentBasket, int limit) {
		Optional<CustomerProfile> profileOpt = getProfile(customerId);
		PurchaseHistory history = getPurchaseHistory(customerId);

		// Get all products the customer hasn't bought recently or in current basket
		Set<String> recentPurchases = history.getAllPurchasedSkus();
		Set<String> excludeSkus = new HashSet<>(currentBasket);

		List<Product> candidates = new ArrayList<>();

		if (profileOpt.isPresent()) {
			CustomerProfile profile = profileOpt.get();

			// 1. Products from favourite categories (safe for allergens)
			for (String category : profile.favouriteCategories()) {
				catalog.findByCategory(category).stream()
						.filter(p -> p.isSafeFor(profile.allergens()))
						.filter(p -> !excludeSkus.contains(p.sku()))
						.forEach(candidates::add);
			}

			// 2. Products matching dietary preferences
			for (String pref : profile.dietaryPreferences()) {
				catalog.findByDietaryFlag(pref).stream()
						.filter(p -> p.isSafeFor(profile.allergens()))
						.filter(p -> !excludeSkus.contains(p.sku()))
						.filter(p -> !candidates.contains(p))
						.forEach(candidates::add);
			}

			// 3. Previously purchased items (replenishment)
			recentPurchases.stream()
					.filter(sku -> !excludeSkus.contains(sku))
					.map(catalog::findBySku)
					.filter(Optional::isPresent)
					.map(Optional::get)
					.filter(p -> p.isSafeFor(profile.allergens()))
					.filter(p -> !candidates.contains(p))
					.forEach(candidates::add);
		} else {
			// No profile - recommend popular items from various categories
			for (String category : catalog.getCategories()) {
				catalog.findByCategory(category).stream()
						.filter(p -> !excludeSkus.contains(p.sku()))
						.limit(2)
						.forEach(candidates::add);
			}
		}

		return candidates.stream()
				.distinct()
				.limit(limit)
				.collect(Collectors.toList());
	}

	/**
	 * Get products similar to what the customer usually buys.
	 */
	public List<Product> getSimilarToUsual(String customerId, int limit) {
		List<Product> frequent = getFrequentlyBoughtItems(customerId, 3);
		Set<String> frequentSkus = frequent.stream().map(Product::sku).collect(Collectors.toSet());

		List<Product> similar = new ArrayList<>();
		for (Product product : frequent) {
			catalog.findSimilar(product, 3).stream()
					.filter(p -> !frequentSkus.contains(p.sku()))
					.filter(p -> !similar.contains(p))
					.forEach(similar::add);
		}

		return similar.stream().limit(limit).collect(Collectors.toList());
	}

	/**
	 * Check if a product is safe for a customer based on their allergen profile.
	 */
	public boolean isProductSafeForCustomer(String customerId, String productSku) {
		Optional<CustomerProfile> profile = getProfile(customerId);
		Optional<Product> product = catalog.findBySku(productSku);

		if (profile.isEmpty() || product.isEmpty()) {
			return true; // If we don't know, assume it's safe
		}

		return product.get().isSafeFor(profile.get().allergens());
	}

	/**
	 * Get products that match a customer's dietary requirements and are safe.
	 */
	public List<Product> getSafeProductsForCustomer(String customerId) {
		Optional<CustomerProfile> profileOpt = getProfile(customerId);
		if (profileOpt.isEmpty()) {
			return catalog.getAllProducts();
		}

		CustomerProfile profile = profileOpt.get();
		return catalog.findWithoutAllergens(profile.allergens());
	}

	// ========== Sample Data Initialization ==========

	private void initializeSampleCustomers() {
		// Customer 1: Alex - Vegetarian, health-conscious
		CustomerProfile alex = CustomerProfile.builder()
				.customerId("cust-001")
				.name("Alex")
				.dietaryPreferences(Set.of("vegetarian"))
				.allergens(Set.of())
				.defaultBudget(new BigDecimal("50.00"))
				.favouriteCategories(List.of("produce", "snacks", "beverages"))
				.build();
		saveProfile(alex);

		// Alex's purchase history
		addHistoricalOrder("cust-001", 
				Map.of("BEV-002", 6, "SNK-004", 2, "PRD-001", 1), // Coke Zero, Hummus, Fruit Salad
				new BigDecimal("18.00"), 30);
		addHistoricalOrder("cust-001",
				Map.of("BEV-002", 4, "PRD-002", 1, "SNK-005", 1), // Coke Zero, Crudités, Guacamole
				new BigDecimal("15.50"), 14);
		addHistoricalOrder("cust-001",
				Map.of("BEV-003", 3, "SNK-001", 2, "PRD-001", 1), // Sparkling Water, Crisps, Fruit Salad
				new BigDecimal("13.50"), 7);

		// Customer 2: Jordan - No restrictions, loves snacks
		CustomerProfile jordan = CustomerProfile.builder()
				.customerId("cust-002")
				.name("Jordan")
				.dietaryPreferences(Set.of())
				.allergens(Set.of())
				.defaultBudget(null)
				.favouriteCategories(List.of("snacks", "beverages", "bakery"))
				.build();
		saveProfile(jordan);

		// Jordan's purchase history
		addHistoricalOrder("cust-002",
				Map.of("BEV-001", 6, "SNK-002", 3, "DAI-002", 1), // Coca Cola, Cheese Crisps, Cheddar
				new BigDecimal("20.50"), 21);
		addHistoricalOrder("cust-002",
				Map.of("SNK-003", 2, "BAK-003", 1, "BEV-001", 4), // Mixed Nuts, Brownies, Coca Cola
				new BigDecimal("17.00"), 10);

		// Customer 3: Sam - Vegan with nut allergy
		CustomerProfile sam = CustomerProfile.builder()
				.customerId("cust-003")
				.name("Sam")
				.dietaryPreferences(Set.of("vegan"))
				.allergens(Set.of("peanuts", "tree nuts"))
				.defaultBudget(new BigDecimal("30.00"))
				.favouriteCategories(List.of("produce", "beverages"))
				.build();
		saveProfile(sam);

		// Sam's purchase history
		addHistoricalOrder("cust-003",
				Map.of("BEV-003", 4, "SNK-005", 2, "PRD-002", 1), // Sparkling Water, Guacamole, Crudités
				new BigDecimal("15.00"), 14);
		addHistoricalOrder("cust-003",
				Map.of("PRD-001", 2, "PRD-003", 1, "BEV-004", 2), // Fruit Salad, Tomatoes, Orange Juice
				new BigDecimal("18.50"), 5);

		// Customer 4: Taylor - Party planner, big orders
		CustomerProfile taylor = CustomerProfile.builder()
				.customerId("cust-004")
				.name("Taylor")
				.dietaryPreferences(Set.of())
				.allergens(Set.of())
				.defaultBudget(new BigDecimal("100.00"))
				.favouriteCategories(List.of("party", "beverages", "snacks"))
				.build();
		saveProfile(taylor);

		// Taylor's purchase history - large party orders
		addHistoricalOrder("cust-004",
				Map.of("PTY-001", 3, "PTY-002", 2, "PTY-004", 2, "BEV-002", 12), 
				new BigDecimal("62.00"), 30);
		addHistoricalOrder("cust-004",
				Map.of("PTY-003", 2, "PTY-005", 4, "SNK-001", 5, "BEV-001", 10),
				new BigDecimal("59.00"), 60);

		// Customer 5: Morgan - Budget-conscious, dairy-free
		CustomerProfile morgan = CustomerProfile.builder()
				.customerId("cust-005")
				.name("Morgan")
				.dietaryPreferences(Set.of())
				.allergens(Set.of("dairy"))
				.defaultBudget(new BigDecimal("25.00"))
				.favouriteCategories(List.of("beverages", "bakery"))
				.build();
		saveProfile(morgan);

		// Morgan's purchase history
		addHistoricalOrder("cust-005",
				Map.of("BEV-002", 4, "BAK-001", 2, "SNK-001", 1),
				new BigDecimal("11.00"), 7);
	}

	private void addHistoricalOrder(String customerId, Map<String, Integer> items, BigDecimal total, int daysAgo) {
		String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
		Instant timestamp = Instant.now().minus(daysAgo, ChronoUnit.DAYS);
		PastOrder order = new PastOrder(orderId, timestamp, items, total);

		PurchaseHistory current = histories.getOrDefault(customerId, PurchaseHistory.empty(customerId));
		histories.put(customerId, current.withOrder(order));
	}
}

