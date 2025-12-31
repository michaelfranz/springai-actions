package org.javai.springai.actions.conversation;

import java.util.Optional;
import org.javai.springai.actions.PlanExecutionResult;

/**
 * Extracts or updates a {@link WorkingContext} from an executed plan.
 * 
 * <p>After a plan executes, extractors examine the results and create
 * or update the working context for the next turn. This allows multi-turn
 * conversations to maintain state.</p>
 * 
 * <h2>Example: SQL Query Extractor</h2>
 * <pre>{@code
 * public class SqlWorkingContextExtractor 
 *         implements WorkingContextExtractor<Query> {
 *     
 *     @Override
 *     public String contextType() { return "sql.query"; }
 *     
 *     @Override
 *     public boolean canHandle(ConversationTurnResult turn, PlanExecutionResult result) {
 *         return result.executedSteps().stream()
 *                 .anyMatch(s -> s.resolvedParams().values().stream()
 *                         .anyMatch(v -> v instanceof Query));
 *     }
 *     
 *     @Override
 *     public Optional<WorkingContext<Query>> extract(
 *             ConversationTurnResult turn, 
 *             PlanExecutionResult result,
 *             WorkingContext<?> prior) {
 *         // Extract Query from executed step and wrap in WorkingContext
 *         Query query = findQueryInResults(result);
 *         return Optional.of(WorkingContext.of("sql.query", query));
 *     }
 * }
 * }</pre>
 * 
 * @param <T> the payload type this extractor produces
 */
public interface WorkingContextExtractor<T> {

	/**
	 * The context type this extractor produces.
	 * 
	 * @return the context type identifier (e.g., "sql.query", "shopping.basket")
	 */
	String contextType();

	/**
	 * Determines if this extractor can handle the given turn result.
	 * 
	 * @param turn the conversation turn result
	 * @param executionResult the plan execution result
	 * @return true if this extractor can extract context from these results
	 */
	boolean canHandle(ConversationTurnResult turn, PlanExecutionResult executionResult);

	/**
	 * Extracts a working context from the turn results.
	 * 
	 * @param turn the conversation turn result
	 * @param executionResult the plan execution result
	 * @param prior the prior working context (may be null or different type)
	 * @return the extracted working context, or empty if extraction fails
	 */
	Optional<WorkingContext<T>> extract(
			ConversationTurnResult turn,
			PlanExecutionResult executionResult,
			WorkingContext<?> prior);
}

