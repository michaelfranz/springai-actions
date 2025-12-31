package org.javai.springai.actions.conversation;

import java.util.Optional;

/**
 * Renders a {@link WorkingContext} for inclusion in the LLM prompt.
 * 
 * <p>Each domain implements its own contributor that knows how to present
 * the working context in a way the LLM can understand and act upon.</p>
 * 
 * <h2>Example: SQL Query Context</h2>
 * <pre>{@code
 * public class SqlWorkingContextContributor 
 *         implements WorkingContextContributor<Query> {
 *     
 *     @Override
 *     public String contextType() { return "sql.query"; }
 *     
 *     @Override
 *     public Optional<String> renderForPrompt(WorkingContext<Query> ctx) {
 *         Query query = ctx.payload();
 *         return Optional.of("""
 *             PREVIOUS QUERY:
 *             SQL: %s
 *             Tables: %s
 *             """.formatted(query.sqlString(), query.referencedTables()));
 *     }
 * }
 * }</pre>
 * 
 * <h2>Example: Shopping Basket Context</h2>
 * <pre>{@code
 * public class BasketWorkingContextContributor 
 *         implements WorkingContextContributor<BasketSummary> {
 *     
 *     @Override
 *     public String contextType() { return "shopping.basket"; }
 *     
 *     @Override
 *     public Optional<String> renderForPrompt(WorkingContext<BasketSummary> ctx) {
 *         BasketSummary basket = ctx.payload();
 *         return Optional.of("""
 *             CURRENT BASKET:
 *             Items: %d | Total: £%s
 *             Last added: %s
 *             """.formatted(basket.itemCount(), basket.total(), basket.lastAdded()));
 *     }
 * }
 * }</pre>
 * 
 * @param <T> the payload type this contributor handles
 */
public interface WorkingContextContributor<T> {

	/**
	 * The context type this contributor handles.
	 * Must match the {@link WorkingContext#contextType()} it renders.
	 * 
	 * @return the context type identifier (e.g., "sql.query", "shopping.basket")
	 */
	String contextType();

	/**
	 * Renders the working context as a string for the LLM prompt.
	 * 
	 * @param context the working context to render
	 * @return the rendered string, or empty if nothing to contribute
	 */
	Optional<String> renderForPrompt(WorkingContext<T> context);
}

