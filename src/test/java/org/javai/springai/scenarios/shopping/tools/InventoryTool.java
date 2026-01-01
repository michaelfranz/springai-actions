package org.javai.springai.scenarios.shopping.tools;

import java.util.List;
import java.util.stream.Collectors;
import org.javai.springai.scenarios.shopping.store.MockStoreApi;
import org.javai.springai.scenarios.shopping.store.model.AvailabilityResult;
import org.javai.springai.scenarios.shopping.store.model.Product;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Tool for checking product availability and stock levels.
 * Provides the assistant with real-time inventory information.
 */
public class InventoryTool {

	private final MockStoreApi storeApi;

	public InventoryTool(MockStoreApi storeApi) {
		this.storeApi = storeApi;
	}

	@Tool(name = "checkAvailability", description = """
			Check if a product is available in the requested quantity.
			Returns availability status including:
			- Whether the product can be added to basket
			- Current stock level warnings (low stock, out of stock)
			- Alternative product suggestions if unavailable
			Use this before adding items to verify stock.""")
	public String checkAvailability(
			@ToolParam(description = "The product name to check") String productName,
			@ToolParam(description = "The quantity needed") int quantity) {

		AvailabilityResult result = storeApi.checkAvailability(productName, quantity);

		return switch (result) {
			case AvailabilityResult.Available(int qty, boolean lowStock) -> {
				if (lowStock) {
					yield String.format("✓ %s is available (%d units). ⚠️ Stock is running low - only %d left.",
							productName, qty, storeApi.findProduct(productName)
									.map(p -> storeApi.getAvailableQuantity(p.sku()))
									.orElse(qty));
				}
				yield String.format("✓ %s is available (%d units requested).", productName, qty);
			}

			case AvailabilityResult.PartiallyAvailable(int available, int requested, List<Product> alternatives) -> {
				String altText = formatAlternatives(alternatives);
				yield String.format("⚠️ Only %d of %d %s available.%s",
						available, requested, productName, altText);
			}

			case AvailabilityResult.OutOfStock(List<Product> alternatives) -> {
				String altText = formatAlternatives(alternatives);
				yield String.format("✗ %s is out of stock.%s", productName, altText);
			}

			case AvailabilityResult.Discontinued(List<Product> alternatives) -> {
				String altText = formatAlternatives(alternatives);
				yield String.format("✗ %s has been discontinued.%s", productName, altText);
			}

			case AvailabilityResult.NotFound(String searchTerm, List<Product> suggestions) -> {
				if (suggestions.isEmpty()) {
					yield String.format("✗ Product '%s' not found in catalog.", searchTerm);
				}
				String suggestionText = suggestions.stream()
						.map(Product::name)
						.collect(Collectors.joining(", "));
				yield String.format("✗ Product '%s' not found. Did you mean: %s?", searchTerm, suggestionText);
			}
		};
	}

	@Tool(name = "getAlternatives", description = """
			Get alternative products for an item that is out of stock or unavailable.
			Returns a list of similar in-stock products the customer might consider.""")
	public String getAlternatives(
			@ToolParam(description = "The product name to find alternatives for") String productName) {

		List<Product> alternatives = storeApi.getAlternatives(productName);

		if (alternatives.isEmpty()) {
			return String.format("No alternatives found for %s.", productName);
		}

		StringBuilder sb = new StringBuilder();
		sb.append(String.format("Alternatives to %s:\n", productName));

		for (Product alt : alternatives) {
			int available = storeApi.getAvailableQuantity(alt.sku());
			sb.append(String.format("- %s (£%.2f per %s, %d in stock)\n",
					alt.name(), alt.unitPrice(), alt.unit(), available));
		}

		return sb.toString();
	}

	@Tool(name = "getStockLevel", description = """
			Get the current stock level for a specific product.
			Returns the available quantity and whether stock is low.""")
	public String getStockLevel(
			@ToolParam(description = "The product name to check") String productName) {

		return storeApi.findProduct(productName)
				.map(product -> {
					int available = storeApi.getAvailableQuantity(product.sku());
					var stockLevel = storeApi.getStockLevel(product.sku());

					if (stockLevel.isEmpty()) {
						return String.format("%s: %d available", product.name(), available);
					}

					var stock = stockLevel.get();
					String status = stock.isOutOfStock() ? "OUT OF STOCK"
							: stock.isLowStock() ? "LOW STOCK"
							: "In stock";

					return String.format("%s: %d available (%s)", product.name(), available, status);
				})
				.orElse(String.format("Product '%s' not found.", productName));
	}

	private String formatAlternatives(List<Product> alternatives) {
		if (alternatives.isEmpty()) {
			return "";
		}
		String altNames = alternatives.stream()
				.map(Product::name)
				.collect(Collectors.joining(", "));
		return String.format(" Consider: %s", altNames);
	}
}

