package org.javai.springai.actions;

/**
 * Configuration for a single model tier in the Planner's fallback chain.
 * 
 * <p>Each tier specifies a model name, maximum retry attempts, and optional
 * model-specific options like temperature.</p>
 *
 * @param modelName the model identifier (e.g., "gpt-4.1-mini", "gpt-4o")
 * @param maxAttempts maximum attempts before moving to next tier (≥1)
 * @param temperature optional temperature setting (0.0-2.0)
 * @param topP optional top-p setting (0.0-1.0)
 */
public record ModelTierConfig(
		String modelName,
		int maxAttempts,
		Double temperature,
		Double topP
) {
	public ModelTierConfig {
		if (modelName == null || modelName.isBlank()) {
			throw new IllegalArgumentException("modelName must not be blank");
		}
		if (maxAttempts < 1) {
			throw new IllegalArgumentException("maxAttempts must be at least 1");
		}
	}

	/**
	 * Builder for ModelTierConfig.
	 */
	public static class Builder {
		private final String modelName;
		private int maxAttempts = 1;
		private Double temperature;
		private Double topP;

		public Builder(String modelName) {
			this.modelName = modelName;
		}

		public Builder maxAttempts(int maxAttempts) {
			this.maxAttempts = maxAttempts;
			return this;
		}

		public Builder temperature(double temperature) {
			this.temperature = temperature;
			return this;
		}

		public Builder topP(double topP) {
			this.topP = topP;
			return this;
		}

		public ModelTierConfig build() {
			return new ModelTierConfig(modelName, maxAttempts, temperature, topP);
		}
	}

	public static Builder builder(String modelName) {
		return new Builder(modelName);
	}
}

