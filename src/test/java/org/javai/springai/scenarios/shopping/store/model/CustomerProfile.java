package org.javai.springai.scenarios.shopping.store.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Represents a customer's profile with preferences and constraints.
 *
 * @param customerId Unique customer identifier
 * @param name Customer's display name
 * @param dietaryPreferences Dietary preferences (e.g., "vegetarian", "low-sugar")
 * @param allergens Personal allergen list for safety filtering
 * @param defaultBudget Optional default spending limit
 * @param favouriteCategories Categories the customer frequently shops from
 */
public record CustomerProfile(
		String customerId,
		String name,
		Set<String> dietaryPreferences,
		Set<String> allergens,
		BigDecimal defaultBudget,
		List<String> favouriteCategories
) {

	/**
	 * Check if customer has a dietary preference.
	 */
	public boolean hasDietaryPreference(String preference) {
		return dietaryPreferences != null && dietaryPreferences.contains(preference.toLowerCase());
	}

	/**
	 * Check if customer has a specific allergen.
	 */
	public boolean hasAllergen(String allergen) {
		return allergens != null && allergens.contains(allergen.toLowerCase());
	}

	/**
	 * Check if customer has a budget set.
	 */
	public boolean hasBudget() {
		return defaultBudget != null && defaultBudget.compareTo(BigDecimal.ZERO) > 0;
	}

	/**
	 * Builder for creating CustomerProfile instances.
	 */
	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private String customerId;
		private String name;
		private Set<String> dietaryPreferences = Set.of();
		private Set<String> allergens = Set.of();
		private BigDecimal defaultBudget;
		private List<String> favouriteCategories = List.of();

		public Builder customerId(String customerId) {
			this.customerId = customerId;
			return this;
		}

		public Builder name(String name) {
			this.name = name;
			return this;
		}

		public Builder dietaryPreferences(Set<String> preferences) {
			this.dietaryPreferences = preferences;
			return this;
		}

		public Builder allergens(Set<String> allergens) {
			this.allergens = allergens;
			return this;
		}

		public Builder defaultBudget(BigDecimal budget) {
			this.defaultBudget = budget;
			return this;
		}

		public Builder favouriteCategories(List<String> categories) {
			this.favouriteCategories = categories;
			return this;
		}

		public CustomerProfile build() {
			return new CustomerProfile(customerId, name, dietaryPreferences, allergens, defaultBudget, favouriteCategories);
		}
	}
}

