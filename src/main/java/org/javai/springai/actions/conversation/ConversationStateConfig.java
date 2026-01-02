package org.javai.springai.actions.conversation;

/**
 * Configuration for conversation state management.
 * 
 * <p>Controls how conversation state is maintained across turns,
 * including history retention limits and user message augmentation.</p>
 * 
 * <h2>User Message Augmentation</h2>
 * <p>When enabled, the framework automatically prepends working context
 * to user messages. This improves LLM comprehension for multi-turn
 * refinement scenarios. For example:</p>
 * <pre>
 * Current query: SELECT order_value FROM fct_orders
 * 
 * User request: add a filter for region = 'East'
 * </pre>
 * 
 * <h2>Usage</h2>
 * <pre>{@code
 * // Use defaults
 * ConversationStateConfig config = ConversationStateConfig.defaults();
 * 
 * // Custom configuration
 * ConversationStateConfig config = ConversationStateConfig.builder()
 *         .maxHistorySize(20)
 *         .augmentUserMessage(true)
 *         .contextPrefix("Current state:")
 *         .requestPrefix("User request:")
 *         .build();
 * }</pre>
 * 
 * @param maxHistorySize maximum number of prior working contexts to retain in history
 * @param augmentUserMessage whether to prepend working context to user messages
 * @param contextPrefix prefix before the context (e.g., "Current query:")
 * @param requestPrefix prefix before the user's request (e.g., "User request:")
 */
public record ConversationStateConfig(
		int maxHistorySize,
		boolean augmentUserMessage,
		String contextPrefix,
		String requestPrefix
) {

	/**
	 * Default maximum history size.
	 */
	public static final int DEFAULT_MAX_HISTORY_SIZE = 10;

	/**
	 * Default context prefix.
	 */
	public static final String DEFAULT_CONTEXT_PREFIX = "Current state:";

	/**
	 * Default request prefix.
	 */
	public static final String DEFAULT_REQUEST_PREFIX = "User request:";

	public ConversationStateConfig {
		if (maxHistorySize < 0) {
			throw new IllegalArgumentException("maxHistorySize must be non-negative");
		}
		if (contextPrefix == null) {
			contextPrefix = DEFAULT_CONTEXT_PREFIX;
		}
		if (requestPrefix == null) {
			requestPrefix = DEFAULT_REQUEST_PREFIX;
		}
	}

	/**
	 * Creates a configuration with default values.
	 * 
	 * @return default configuration
	 */
	public static ConversationStateConfig defaults() {
		return new ConversationStateConfig(
				DEFAULT_MAX_HISTORY_SIZE,
				true,
				DEFAULT_CONTEXT_PREFIX,
				DEFAULT_REQUEST_PREFIX);
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
		private boolean augmentUserMessage = true;
		private String contextPrefix = DEFAULT_CONTEXT_PREFIX;
		private String requestPrefix = DEFAULT_REQUEST_PREFIX;

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
		 * Sets whether to augment user messages with working context.
		 * 
		 * <p>When enabled (default), the framework prepends working context
		 * to user messages before planning. This improves LLM comprehension
		 * for multi-turn refinement scenarios.</p>
		 * 
		 * @param augment true to enable augmentation (default), false to disable
		 * @return this builder
		 */
		public Builder augmentUserMessage(boolean augment) {
			this.augmentUserMessage = augment;
			return this;
		}

		/**
		 * Sets the prefix for the context portion of augmented messages.
		 * 
		 * <p>Default is "Current state:". For SQL scenarios, you might
		 * use "Current query:" instead.</p>
		 * 
		 * @param prefix the context prefix
		 * @return this builder
		 */
		public Builder contextPrefix(String prefix) {
			this.contextPrefix = prefix;
			return this;
		}

		/**
		 * Sets the prefix for the user request portion of augmented messages.
		 * 
		 * <p>Default is "User request:".</p>
		 * 
		 * @param prefix the request prefix
		 * @return this builder
		 */
		public Builder requestPrefix(String prefix) {
			this.requestPrefix = prefix;
			return this;
		}

		/**
		 * Builds the configuration.
		 * 
		 * @return the configuration
		 */
		public ConversationStateConfig build() {
			return new ConversationStateConfig(
					maxHistorySize,
					augmentUserMessage,
					contextPrefix,
					requestPrefix);
		}
	}
}
