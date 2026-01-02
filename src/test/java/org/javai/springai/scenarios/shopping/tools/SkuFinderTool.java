package org.javai.springai.scenarios.shopping.tools;

import java.util.List;
import java.util.stream.Collectors;
import org.javai.springai.scenarios.shopping.store.MockStoreApi;
import org.javai.springai.scenarios.shopping.store.model.Product;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Tool for looking up precise SKU identifiers for products.
 * <p>
 * This is a critical tool for the planning workflow:
 * <ol>
 *   <li>User mentions a product vaguely (e.g., "coke", "chips")</li>
 *   <li>LLM invokes this tool to find matching SKUs</li>
 *   <li>Tool returns structured list of matches with SKU, name, description</li>
 *   <li>LLM selects the best match and uses the SKU in the plan</li>
 * </ol>
 * 
 * <p>Actions like {@code addItem} require precise SKU identifiers, not fuzzy
 * product names. This tool bridges user intent to system identifiers.</p>
 */
public class SkuFinderTool {

	private final MockStoreApi storeApi;

	public SkuFinderTool(MockStoreApi storeApi) {
		this.storeApi = storeApi;
	}

	@Tool(name = "findProductSku", description = """
			IMPORTANT: Use this tool BEFORE adding items to basket.
			Searches for products matching a term and returns their SKU identifiers.
			The SKU is required for addItem, removeItem, and updateQuantity actions.
			
			Returns: List of matching products with SKU, name, price, unit, and dietary info.
			If no matches found, returns an empty result - inform user the product wasn't found.""")
	public String findProductSku(
			@ToolParam(description = "Search term from user input (e.g., 'coke', 'chips', 'orange juice')") 
			String searchTerm) {

		List<Product> matches = storeApi.searchProducts(searchTerm);

		if (matches.isEmpty()) {
			return String.format(
					"NO_MATCHES: No products found for '%s'. Ask user to clarify or try different terms.",
					searchTerm);
		}

		StringBuilder sb = new StringBuilder();
		sb.append(String.format("FOUND %d product(s) matching '%s':\n\n", matches.size(), searchTerm));

		for (Product p : matches) {
			int available = storeApi.getAvailableQuantity(p.sku());
			sb.append(String.format("SKU: %s\n", p.sku()));
			sb.append(String.format("  Name: %s\n", p.name()));
			sb.append(String.format("  Price: £%.2f per %s\n", p.unitPrice(), p.unit()));
			sb.append(String.format("  Description: %s\n", p.description()));
			
			if (!p.dietaryFlags().isEmpty()) {
				sb.append(String.format("  Dietary: %s\n", String.join(", ", p.dietaryFlags())));
			}
			if (!p.allergens().isEmpty()) {
				sb.append(String.format("  Allergens: %s\n", String.join(", ", p.allergens())));
			}
			
			sb.append(String.format("  Available: %d units%s\n", 
					available, available == 0 ? " (OUT OF STOCK)" : ""));
			sb.append("\n");
		}

		sb.append("Use the SKU value in your plan's addItem/removeItem/updateQuantity action.");
		return sb.toString();
	}

	@Tool(name = "getProductBySku", description = """
			Get detailed information about a product by its SKU identifier.
			Use this to verify a SKU before using it in a plan.""")
	public String getProductBySku(
			@ToolParam(description = "The SKU identifier (e.g., 'COKE-ZERO-330ML')") String sku) {

		return storeApi.findProductBySku(sku)
				.map(p -> {
					int available = storeApi.getAvailableQuantity(p.sku());
					return String.format(
							"SKU: %s\nName: %s\nPrice: £%.2f/%s\nAvailable: %d units",
							p.sku(), p.name(), p.unitPrice(), p.unit(), available);
				})
				.orElse(String.format("SKU '%s' not found in catalog.", sku));
	}
}

