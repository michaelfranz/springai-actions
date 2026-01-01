package org.javai.springai.scenarios.shopping.store;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.javai.springai.scenarios.shopping.store.model.Product;

/**
 * Product catalog containing all available products in the store.
 * Provides search, filtering, and lookup capabilities.
 */
public class ProductCatalog {

	private final Map<String, Product> productsBySku = new HashMap<>();
	private final Map<String, List<Product>> productsByCategory = new HashMap<>();

	public ProductCatalog() {
		initializeCatalog();
	}

	/**
	 * Find a product by its exact SKU.
	 */
	public Optional<Product> findBySku(String sku) {
		return Optional.ofNullable(productsBySku.get(sku));
	}

	/**
	 * Find a product by name (case-insensitive, supports partial matching).
	 */
	public Optional<Product> findByName(String name) {
		if (name == null || name.isBlank()) {
			return Optional.empty();
		}
		String lowerName = name.toLowerCase().trim();

		// First try exact match
		for (Product product : productsBySku.values()) {
			if (product.name().equalsIgnoreCase(lowerName)) {
				return Optional.of(product);
			}
		}

		// Then try contains match
		for (Product product : productsBySku.values()) {
			if (product.name().toLowerCase().contains(lowerName)) {
				return Optional.of(product);
			}
		}

		// Finally try fuzzy matching on key terms
		return productsBySku.values().stream()
				.filter(p -> fuzzyMatch(p.name(), lowerName))
				.findFirst();
	}

	/**
	 * Get all products in a category.
	 */
	public List<Product> findByCategory(String category) {
		return productsByCategory.getOrDefault(category.toLowerCase(), List.of());
	}

	/**
	 * Find products matching a dietary flag (e.g., "vegetarian", "vegan").
	 */
	public List<Product> findByDietaryFlag(String flag) {
		return productsBySku.values().stream()
				.filter(p -> p.hasDietaryFlag(flag))
				.collect(Collectors.toList());
	}

	/**
	 * Find products that don't contain any of the specified allergens.
	 */
	public List<Product> findWithoutAllergens(Set<String> allergens) {
		return productsBySku.values().stream()
				.filter(p -> p.isSafeFor(allergens))
				.collect(Collectors.toList());
	}

	/**
	 * Search products by query (matches name, description, or category).
	 */
	public List<Product> searchProducts(String query) {
		return productsBySku.values().stream()
				.filter(p -> p.matchesQuery(query))
				.collect(Collectors.toList());
	}

	/**
	 * Get all products in the catalog.
	 */
	public List<Product> getAllProducts() {
		return new ArrayList<>(productsBySku.values());
	}

	/**
	 * Get all category names.
	 */
	public Set<String> getCategories() {
		return Set.copyOf(productsByCategory.keySet());
	}

	/**
	 * Find similar products in the same category.
	 */
	public List<Product> findSimilar(Product product, int limit) {
		return productsByCategory.getOrDefault(product.category().toLowerCase(), List.of())
				.stream()
				.filter(p -> !p.sku().equals(product.sku()))
				.limit(limit)
				.collect(Collectors.toList());
	}

	private void addProduct(Product product) {
		productsBySku.put(product.sku(), product);
		productsByCategory
				.computeIfAbsent(product.category().toLowerCase(), k -> new ArrayList<>())
				.add(product);
	}

	private boolean fuzzyMatch(String productName, String query) {
		// Simple fuzzy matching: check if most words in query appear in product name
		String[] queryWords = query.split("\\s+");
		String lowerProduct = productName.toLowerCase();
		int matches = 0;
		for (String word : queryWords) {
			if (word.length() > 2 && lowerProduct.contains(word)) {
				matches++;
			}
		}
		return matches > 0 && matches >= queryWords.length / 2;
	}

	private void initializeCatalog() {
		// ===== BEVERAGES =====
		addProduct(new Product(
				"BEV-001", "Coca Cola", "beverages",
				new BigDecimal("1.50"), "bottle",
				Set.of("vegetarian", "vegan"), Set.of(),
				"Classic Coca Cola 500ml bottle"
		));
		addProduct(new Product(
				"BEV-002", "Coke Zero", "beverages",
				new BigDecimal("1.50"), "bottle",
				Set.of("vegetarian", "vegan"), Set.of(),
				"Coke Zero Sugar 500ml bottle"
		));
		addProduct(new Product(
				"BEV-003", "Sparkling Water", "beverages",
				new BigDecimal("1.00"), "bottle",
				Set.of("vegetarian", "vegan", "gluten-free"), Set.of(),
				"Sparkling mineral water 500ml"
		));
		addProduct(new Product(
				"BEV-004", "Orange Juice", "beverages",
				new BigDecimal("2.50"), "carton",
				Set.of("vegetarian", "vegan", "gluten-free"), Set.of(),
				"Fresh squeezed orange juice 1L"
		));
		addProduct(new Product(
				"BEV-005", "Lemonade", "beverages",
				new BigDecimal("1.75"), "bottle",
				Set.of("vegetarian", "vegan"), Set.of(),
				"Traditional cloudy lemonade 500ml"
		));

		// ===== SNACKS =====
		addProduct(new Product(
				"SNK-001", "Sea Salt Crisps", "snacks",
				new BigDecimal("2.00"), "pack",
				Set.of("vegetarian", "vegan", "gluten-free"), Set.of(),
				"Lightly salted potato crisps 150g"
		));
		addProduct(new Product(
				"SNK-002", "Cheese & Onion Crisps", "snacks",
				new BigDecimal("2.00"), "pack",
				Set.of("vegetarian"), Set.of("dairy"),
				"Cheese and onion flavoured crisps 150g"
		));
		addProduct(new Product(
				"SNK-003", "Mixed Nuts", "snacks",
				new BigDecimal("3.50"), "pack",
				Set.of("vegetarian", "vegan", "gluten-free"), Set.of("peanuts", "tree nuts"),
				"Premium mixed nuts including peanuts, almonds, cashews 200g"
		));
		addProduct(new Product(
				"SNK-004", "Hummus", "snacks",
				new BigDecimal("2.25"), "tub",
				Set.of("vegetarian", "vegan", "gluten-free"), Set.of("sesame"),
				"Classic chickpea hummus 200g"
		));
		addProduct(new Product(
				"SNK-005", "Guacamole", "snacks",
				new BigDecimal("3.00"), "tub",
				Set.of("vegetarian", "vegan", "gluten-free"), Set.of(),
				"Fresh avocado guacamole 200g"
		));
		addProduct(new Product(
				"SNK-006", "Salted Pretzels", "snacks",
				new BigDecimal("2.50"), "pack",
				Set.of("vegetarian", "vegan"), Set.of("gluten"),
				"Crunchy salted pretzels 200g"
		));

		// ===== DAIRY =====
		addProduct(new Product(
				"DAI-001", "Semi-skimmed Milk", "dairy",
				new BigDecimal("1.20"), "bottle",
				Set.of("vegetarian", "gluten-free"), Set.of("dairy"),
				"Semi-skimmed milk 1L"
		));
		addProduct(new Product(
				"DAI-002", "Cheddar Cheese", "dairy",
				new BigDecimal("3.50"), "pack",
				Set.of("vegetarian", "gluten-free"), Set.of("dairy"),
				"Mature cheddar cheese 400g"
		));
		addProduct(new Product(
				"DAI-003", "Greek Yogurt", "dairy",
				new BigDecimal("2.00"), "pot",
				Set.of("vegetarian", "gluten-free"), Set.of("dairy"),
				"Thick Greek-style yogurt 500g"
		));

		// ===== PRODUCE =====
		addProduct(new Product(
				"PRD-001", "Fruit Salad Bowl", "produce",
				new BigDecimal("4.50"), "bowl",
				Set.of("vegetarian", "vegan", "gluten-free"), Set.of(),
				"Fresh mixed fruit salad serves 10"
		));
		addProduct(new Product(
				"PRD-002", "Vegetable Crudités Tray", "produce",
				new BigDecimal("5.00"), "tray",
				Set.of("vegetarian", "vegan", "gluten-free"), Set.of(),
				"Selection of fresh cut vegetables with dip"
		));
		addProduct(new Product(
				"PRD-003", "Cherry Tomatoes", "produce",
				new BigDecimal("2.00"), "punnet",
				Set.of("vegetarian", "vegan", "gluten-free"), Set.of(),
				"Sweet cherry tomatoes 250g"
		));

		// ===== PARTY =====
		addProduct(new Product(
				"PTY-001", "Caprese Skewers", "party",
				new BigDecimal("6.00"), "pack",
				Set.of("vegetarian", "gluten-free"), Set.of("dairy"),
				"Mozzarella, tomato and basil skewers (20 pack)"
		));
		addProduct(new Product(
				"PTY-002", "Bruschetta Platter", "party",
				new BigDecimal("8.00"), "platter",
				Set.of("vegetarian"), Set.of("gluten"),
				"Classic tomato bruschetta (12 pieces)"
		));
		addProduct(new Product(
				"PTY-003", "Cheese Board Selection", "party",
				new BigDecimal("12.00"), "board",
				Set.of("vegetarian", "gluten-free"), Set.of("dairy"),
				"Assorted cheeses with crackers"
		));
		addProduct(new Product(
				"PTY-004", "Hummus Platter", "party",
				new BigDecimal("7.00"), "platter",
				Set.of("vegetarian", "vegan", "gluten-free"), Set.of("sesame"),
				"Hummus trio with pitta bread serves 12"
		));
		addProduct(new Product(
				"PTY-005", "Crisps Party Pack", "party",
				new BigDecimal("5.00"), "pack",
				Set.of("vegetarian", "vegan"), Set.of(),
				"Assorted crisps in sharing bags"
		));

		// ===== BAKERY =====
		addProduct(new Product(
				"BAK-001", "Baguette", "bakery",
				new BigDecimal("1.50"), "each",
				Set.of("vegetarian", "vegan"), Set.of("gluten"),
				"Fresh French baguette"
		));
		addProduct(new Product(
				"BAK-002", "Croissants", "bakery",
				new BigDecimal("3.00"), "pack",
				Set.of("vegetarian"), Set.of("gluten", "dairy"),
				"Butter croissants (4 pack)"
		));
		addProduct(new Product(
				"BAK-003", "Brownie Bites", "bakery",
				new BigDecimal("4.00"), "box",
				Set.of("vegetarian"), Set.of("gluten", "dairy"),
				"Chocolate brownie bites (12 pack)"
		));

		// ===== DISCONTINUED (for testing) =====
		addProduct(new Product(
				"DIS-001", "Discontinued Soda", "beverages",
				new BigDecimal("1.00"), "bottle",
				Set.of("vegetarian", "vegan"), Set.of(),
				"This product has been discontinued"
		));
	}
}

