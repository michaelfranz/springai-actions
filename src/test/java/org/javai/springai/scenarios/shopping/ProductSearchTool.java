package org.javai.springai.scenarios.shopping;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.javai.springai.scenarios.shopping.store.MockStoreApi;
import org.javai.springai.scenarios.shopping.store.model.Product;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Tool for searching and browsing the product catalog.
 * Provides the assistant with product discovery capabilities.
 */
public class ProductSearchTool {

	private final MockStoreApi storeApi;

	public ProductSearchTool(MockStoreApi storeApi) {
		this.storeApi = storeApi;
	}

	@Tool(name = "searchProducts", description = """
			Search for products by name, description, or keyword.
			Returns matching products with prices and availability.""")
	public String searchProducts(
			@ToolParam(description = "Search query (product name, description, or keyword)") String query) {

		List<Product> results = storeApi.searchProducts(query);

		if (results.isEmpty()) {
			return String.format("No products found matching '%s'.", query);
		}

		return formatProductList(results, String.format("Products matching '%s'", query));
	}

	@Tool(name = "getProductsByCategory", description = """
			Get all products in a specific category.
			Available categories: beverages, snacks, dairy, produce, party, bakery.""")
	public String getProductsByCategory(
			@ToolParam(description = "Category name (e.g., 'beverages', 'snacks', 'party')") String category) {

		List<Product> products = storeApi.getProductsByCategory(category);

		if (products.isEmpty()) {
			Set<String> available = storeApi.getCategories();
			return String.format("Category '%s' not found. Available categories: %s",
					category, String.join(", ", available));
		}

		return formatProductList(products, String.format("%s category", capitalize(category)));
	}

	@Tool(name = "getCategories", description = """
			Get all available product categories in the store.""")
	public String getCategories() {
		Set<String> categories = storeApi.getCategories();
		return "Available categories: " + categories.stream()
				.map(this::capitalize)
				.collect(Collectors.joining(", "));
	}

	@Tool(name = "getVegetarianProducts", description = """
			Get all vegetarian-friendly products.
			Useful for customers with dietary requirements.""")
	public String getVegetarianProducts() {
		List<Product> products = storeApi.getProductsByDiet("vegetarian");
		return formatProductList(products, "Vegetarian products");
	}

	@Tool(name = "getVeganProducts", description = """
			Get all vegan-friendly products.
			Useful for customers with dietary requirements.""")
	public String getVeganProducts() {
		List<Product> products = storeApi.getProductsByDiet("vegan");
		return formatProductList(products, "Vegan products");
	}

	@Tool(name = "getAllergenFreeProducts", description = """
			Get products that are free from specific allergens.
			Common allergens: peanuts, tree nuts, dairy, gluten, sesame.""")
	public String getAllergenFreeProducts(
			@ToolParam(description = "Comma-separated list of allergens to avoid (e.g., 'peanuts, dairy')") String allergens) {

		Set<String> allergenSet = Set.of(allergens.toLowerCase().split("\\s*,\\s*"));
		List<Product> products = storeApi.getSafeProducts(allergenSet);

		String allergenList = String.join(", ", allergenSet);
		return formatProductList(products, String.format("Products free from %s", allergenList));
	}

	@Tool(name = "getProductDetails", description = """
			Get detailed information about a specific product including
			price, dietary information, allergens, and current stock.""")
	public String getProductDetails(
			@ToolParam(description = "The product name") String productName) {

		return storeApi.findProduct(productName)
				.map(product -> {
					int available = storeApi.getAvailableQuantity(product.sku());
					var stockLevel = storeApi.getStockLevel(product.sku());

					StringBuilder sb = new StringBuilder();
					sb.append(String.format("**%s**\n", product.name()));
					sb.append(String.format("Price: £%.2f per %s\n", product.unitPrice(), product.unit()));
					sb.append(String.format("Category: %s\n", capitalize(product.category())));
					sb.append(String.format("Description: %s\n", product.description()));

					if (!product.dietaryFlags().isEmpty()) {
						sb.append(String.format("Dietary: %s\n", String.join(", ", product.dietaryFlags())));
					}

					if (!product.allergens().isEmpty()) {
						sb.append(String.format("Contains: %s\n", String.join(", ", product.allergens())));
					} else {
						sb.append("Allergen-free\n");
					}

					String stockStatus = stockLevel.map(s -> {
						if (s.isOutOfStock()) return "OUT OF STOCK";
						if (s.isLowStock()) return "LOW STOCK";
						return "In stock";
					}).orElse("Unknown");

					sb.append(String.format("Availability: %d units (%s)", available, stockStatus));

					return sb.toString();
				})
				.orElse(String.format("Product '%s' not found.", productName));
	}

	private String formatProductList(List<Product> products, String title) {
		if (products.isEmpty()) {
			return "No products found.";
		}

		StringBuilder sb = new StringBuilder();
		sb.append(String.format("%s (%d items):\n", title, products.size()));

		for (Product product : products) {
			int available = storeApi.getAvailableQuantity(product.sku());
			String stockIndicator = available == 0 ? " [OUT OF STOCK]"
					: available <= 10 ? " [LOW STOCK]"
					: "";

			sb.append(String.format("- %s: £%.2f/%s%s\n",
					product.name(), product.unitPrice(), product.unit(), stockIndicator));
		}

		return sb.toString();
	}

	private String capitalize(String s) {
		if (s == null || s.isEmpty()) return s;
		return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
	}
}

