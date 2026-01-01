package org.javai.springai.scenarios.shopping.store.model;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Represents a product in the store's catalog.
 *
 * @param sku Unique stock-keeping unit identifier
 * @param name Human-readable product name
 * @param category Product category (e.g., "beverages", "snacks", "dairy")
 * @param unitPrice Price per unit
 * @param unit Unit of sale (e.g., "bottle", "pack", "kg", "each")
 * @param dietaryFlags Dietary attributes (e.g., "vegetarian", "vegan", "gluten-free")
 * @param allergens Allergens present (e.g., "peanuts", "dairy", "gluten")
 * @param description Product description
 */
public record Product(
		String sku,
		String name,
		String category,
		BigDecimal unitPrice,
		String unit,
		Set<String> dietaryFlags,
		Set<String> allergens,
		String description
) {

	/**
	 * Check if this product matches a search query (case-insensitive).
	 */
	public boolean matchesQuery(String query) {
		if (query == null || query.isBlank()) {
			return true;
		}
		String lowerQuery = query.toLowerCase();
		return name.toLowerCase().contains(lowerQuery)
				|| description.toLowerCase().contains(lowerQuery)
				|| category.toLowerCase().contains(lowerQuery);
	}

	/**
	 * Check if this product is safe for someone with the given allergens.
	 */
	public boolean isSafeFor(Set<String> excludeAllergens) {
		if (excludeAllergens == null || excludeAllergens.isEmpty()) {
			return true;
		}
		for (String allergen : excludeAllergens) {
			if (allergens.contains(allergen.toLowerCase())) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Check if this product has a specific dietary flag.
	 */
	public boolean hasDietaryFlag(String flag) {
		return dietaryFlags.contains(flag.toLowerCase());
	}
}

