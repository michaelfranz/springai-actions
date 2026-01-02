package org.javai.springai.actions.conversation;

import java.util.Optional;

/**
 * Formats working context for inclusion in the user message.
 * 
 * <p>LLMs allocate more attention to user messages than system prompt content.
 * For multi-turn refinement scenarios, including the current state in the
 * user message significantly improves the model's ability to understand and
 * modify it.</p>
 * 
 * <h2>Design Rationale</h2>
 * <ul>
 *   <li><b>System prompt</b>: What you are (persona), what you can do (actions), 
 *       how to respond (format) - stable across turns</li>
 *   <li><b>User message</b>: What you're working with (current state), 
 *       what the user wants (request) - changes each turn</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * <pre>{@code
 * ConversationManager manager = new ConversationManager(planner, serializer, typeRegistry, config)
 *     .registerAugmenter(new SqlUserMessageAugmenter());
 * 
 * // Turn 2+ messages automatically include working context
 * ConversationTurnResult turn2 = manager.converse("add a filter for region", turn1.blob());
 * // Planner receives: "Current query: SELECT ... \n\nUser request: add a filter for region"
 * }</pre>
 * 
 * @see ConversationManager#registerAugmenter(UserMessageAugmenter)
 * @see WorkingContext
 */
public interface UserMessageAugmenter {

	/**
	 * The context type this augmenter handles.
	 * Must match {@link WorkingContext#contextType()}.
	 * 
	 * @return the context type identifier (e.g., "sql.query")
	 */
	String contextType();

	/**
	 * Formats the working context for inclusion in the user message.
	 * 
	 * <p>The returned string will be prepended to the user's message with
	 * a separator. For example:</p>
	 * <pre>
	 * Current query: SELECT order_value FROM fct_orders
	 * 
	 * User request: add a filter for region = 'East'
	 * </pre>
	 * 
	 * <p>The implementation can use its own prefix (e.g., "Current query:")
	 * or use the config's {@link ConversationStateConfig#contextPrefix()}.</p>
	 * 
	 * @param workingContext the current working context (never null)
	 * @return formatted string to prepend, or empty if context should not be included
	 */
	Optional<String> formatForUserMessage(WorkingContext<?> workingContext);

	/**
	 * Formats the working context using the config's context prefix.
	 * 
	 * <p>Default implementation delegates to {@link #formatForUserMessage(WorkingContext)}
	 * ignoring the config prefix. Override to use config-driven prefixes.</p>
	 * 
	 * @param workingContext the current working context
	 * @param config the conversation config with prefix settings
	 * @return formatted string to prepend, or empty if context should not be included
	 */
	default Optional<String> formatForUserMessage(WorkingContext<?> workingContext, 
			ConversationStateConfig config) {
		return formatForUserMessage(workingContext);
	}

	/**
	 * Whether to augment the user message for this context instance.
	 * 
	 * <p>Override to conditionally skip augmentation, for example when
	 * the context is empty or stale.</p>
	 * 
	 * @param context the working context
	 * @return true to augment (default), false to skip
	 */
	default boolean shouldAugment(WorkingContext<?> context) {
		return true;
	}
}

