package org.javai.springai.actions.conversation;

/**
 * Configuration for conversation state management.
 * 
 * <p>Controls how conversation state is maintained across turns,
 * including history retention limits.</p>
 * 
 * <h2>Usage</h2>
 * <pre>{@code
 * // Use defaults
 * ConversationStateConfig config = ConversationStateConfig.defaults();
 * 
 * // Custom configuration
 * ConversationStateConfig config = ConversationStateConfig.builder()
 *         .maxHistorySize(20)
 *         .build();
 * }</pre>
 * 
 * @param maxHistorySize maximum number of prior working contexts to retain in history
 */
public record ConversationStateConfig(
		int maxHistorySize
) {

	/**
	 * Default maximum history size.
	 */
	public static final int DEFAULT_MAX_HISTORY_SIZE = 10;

	public ConversationStateConfig {
		if (maxHistorySize < 0) {
			throw new IllegalArgumentException("maxHistorySize must be non-negative");
		}
	}

	/**
	 * Creates a configuration with default values.
	 * 
	 * @return default configuration
	 */
	public static ConversationStateConfig defaults() {
		return new ConversationStateConfig(DEFAULT_MAX_HISTORY_SIZE);
	}

	/**
	 * Creates a new builder for custom configuration.
	 * 
	 * @return a new builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for {@link ConversationStateConfig}.
	 */
	public static class Builder {
		private int maxHistorySize = DEFAULT_MAX_HISTORY_SIZE;

		private Builder() {}

		/**
		 * Sets the maximum number of prior working contexts to retain.
		 * 
		 * <p>When the history exceeds this limit, the oldest entries are
		 * removed to make room for new ones.</p>
		 * 
		 * @param maxHistorySize the maximum history size (must be non-negative)
		 * @return this builder
		 */
		public Builder maxHistorySize(int maxHistorySize) {
			this.maxHistorySize = maxHistorySize;
			return this;
		}

		/**
		 * Builds the configuration.
		 * 
		 * @return the configuration
		 */
		public ConversationStateConfig build() {
			return new ConversationStateConfig(maxHistorySize);
		}
	}
}

