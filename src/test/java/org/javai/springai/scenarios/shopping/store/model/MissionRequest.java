package org.javai.springai.scenarios.shopping.store.model;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Represents a shopping mission request with constraints.
 * Used to plan a complete shopping list for an occasion.
 *
 * @param description Free-text description of the mission (e.g., "midday party")
 * @param headcount Number of people to cater for
 * @param dietaryRequirements Required dietary flags (e.g., "vegetarian", "vegan")
 * @param allergenExclusions Allergens to exclude (e.g., "peanuts", "dairy")
 * @param budgetLimit Optional maximum spend
 * @param occasion Type of occasion for quantity/category hints
 */
public record MissionRequest(
		String description,
		int headcount,
		Set<String> dietaryRequirements,
		Set<String> allergenExclusions,
		BigDecimal budgetLimit,
		Occasion occasion
) {

	/**
	 * Types of occasions with different product and quantity implications.
	 */
	public enum Occasion {
		PARTY("party", 1.5),        // More snacks and drinks per person
		DINNER("dinner", 1.0),       // Balanced portions
		PICNIC("picnic", 1.2),       // Portable foods
		SNACKS("snacks", 0.8),       // Light refreshments
		MEETING("meeting", 0.5);     // Minimal catering

		private final String label;
		private final double portionMultiplier;

		Occasion(String label, double portionMultiplier) {
			this.label = label;
			this.portionMultiplier = portionMultiplier;
		}

		public String label() {
			return label;
		}

		public double portionMultiplier() {
			return portionMultiplier;
		}

		public static Occasion fromString(String s) {
			if (s == null || s.isBlank()) {
				return SNACKS;
			}
			String lower = s.toLowerCase();
			for (Occasion o : values()) {
				if (o.label.equals(lower) || o.name().equalsIgnoreCase(lower)) {
					return o;
				}
			}
			return SNACKS;
		}
	}

	/**
	 * Check if mission has dietary requirements.
	 */
	public boolean hasDietaryRequirements() {
		return dietaryRequirements != null && !dietaryRequirements.isEmpty();
	}

	/**
	 * Check if mission has allergen exclusions.
	 */
	public boolean hasAllergenExclusions() {
		return allergenExclusions != null && !allergenExclusions.isEmpty();
	}

	/**
	 * Check if mission has a budget limit.
	 */
	public boolean hasBudget() {
		return budgetLimit != null && budgetLimit.compareTo(BigDecimal.ZERO) > 0;
	}

	/**
	 * Builder for creating MissionRequest instances.
	 */
	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private String description = "";
		private int headcount = 1;
		private Set<String> dietaryRequirements = Set.of();
		private Set<String> allergenExclusions = Set.of();
		private BigDecimal budgetLimit = null;
		private Occasion occasion = Occasion.SNACKS;

		public Builder description(String description) {
			this.description = description;
			return this;
		}

		public Builder headcount(int headcount) {
			this.headcount = Math.max(1, headcount);
			return this;
		}

		public Builder dietaryRequirements(Set<String> requirements) {
			this.dietaryRequirements = requirements != null ? requirements : Set.of();
			return this;
		}

		public Builder allergenExclusions(Set<String> exclusions) {
			this.allergenExclusions = exclusions != null ? exclusions : Set.of();
			return this;
		}

		public Builder budgetLimit(BigDecimal limit) {
			this.budgetLimit = limit;
			return this;
		}

		public Builder occasion(Occasion occasion) {
			this.occasion = occasion != null ? occasion : Occasion.SNACKS;
			return this;
		}

		public Builder occasion(String occasionString) {
			this.occasion = Occasion.fromString(occasionString);
			return this;
		}

		public MissionRequest build() {
			return new MissionRequest(description, headcount, dietaryRequirements,
					allergenExclusions, budgetLimit, occasion);
		}
	}
}

