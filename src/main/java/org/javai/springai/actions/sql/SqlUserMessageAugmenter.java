package org.javai.springai.actions.sql;

import java.util.Optional;
import org.javai.springai.actions.conversation.ConversationStateConfig;
import org.javai.springai.actions.conversation.UserMessageAugmenter;
import org.javai.springai.actions.conversation.WorkingContext;

/**
 * Augments user messages with the current SQL query for multi-turn refinement.
 * 
 * <p>When a conversation has a prior SQL query, this augmenter prepends it to
 * the user's message so the LLM can see and modify it. This is more effective
 * than including the query only in the system prompt.</p>
 * 
 * <h2>Example</h2>
 * <p>Given working context with SQL {@code SELECT order_value FROM fct_orders}
 * and user message "add a filter for region = 'East'", the augmented message becomes:</p>
 * <pre>
 * Current query: SELECT order_value FROM fct_orders
 * 
 * User request: add a filter for region = 'East'
 * </pre>
 * 
 * <h2>Usage</h2>
 * <pre>{@code
 * ConversationManager manager = new ConversationManager(planner, serializer, typeRegistry, config)
 *     .registerAugmenter(new SqlUserMessageAugmenter());
 * }</pre>
 * 
 * @see SqlQueryPayload
 * @see UserMessageAugmenter
 */
public class SqlUserMessageAugmenter implements UserMessageAugmenter {

	private static final String DEFAULT_PREFIX = "Current query:";

	private final String prefix;

	/**
	 * Creates an augmenter with the default prefix "Current query:".
	 */
	public SqlUserMessageAugmenter() {
		this(DEFAULT_PREFIX);
	}

	/**
	 * Creates an augmenter with a custom prefix.
	 * 
	 * @param prefix the prefix before the SQL (e.g., "Existing SQL:", "Base query:")
	 */
	public SqlUserMessageAugmenter(String prefix) {
		this.prefix = prefix != null ? prefix : DEFAULT_PREFIX;
	}

	@Override
	public String contextType() {
		return SqlQueryPayload.CONTEXT_TYPE;
	}

	@Override
	public Optional<String> formatForUserMessage(WorkingContext<?> workingContext) {
		return formatWithPrefix(workingContext, prefix);
	}

	@Override
	public Optional<String> formatForUserMessage(WorkingContext<?> workingContext,
			ConversationStateConfig config) {
		// Use config's contextPrefix if available, otherwise fall back to instance prefix
		String effectivePrefix = (config != null && config.contextPrefix() != null) 
				? config.contextPrefix() 
				: prefix;
		return formatWithPrefix(workingContext, effectivePrefix);
	}

	private Optional<String> formatWithPrefix(WorkingContext<?> workingContext, String prefixToUse) {
		if (workingContext == null || workingContext.payload() == null) {
			return Optional.empty();
		}

		if (workingContext.payload() instanceof SqlQueryPayload payload) {
			String sql = payload.modelSql();
			if (sql != null && !sql.isBlank()) {
				return Optional.of(prefixToUse + " " + sql);
			}
		}

		return Optional.empty();
	}

	@Override
	public boolean shouldAugment(WorkingContext<?> context) {
		if (context == null || context.payload() == null) {
			return false;
		}

		if (context.payload() instanceof SqlQueryPayload payload) {
			String sql = payload.modelSql();
			return sql != null && !sql.isBlank();
		}

		return false;
	}
}

