package org.javai.springai.actions.sql;

import java.util.Optional;
import org.javai.springai.actions.PlanExecutionResult;
import org.javai.springai.actions.PlanStep;
import org.javai.springai.actions.conversation.ConversationTurnResult;
import org.javai.springai.actions.conversation.WorkingContext;
import org.javai.springai.actions.conversation.WorkingContextExtractor;
import org.javai.springai.actions.internal.plan.PlanArgument;

/**
 * Extracts SQL query context from executed conversation turns.
 * 
 * <p>When a plan step produces a {@link Query} result (directly or via parameter),
 * this extractor creates a {@link SqlQueryPayload} working context for the next turn.</p>
 * 
 * <h2>Extraction Strategy</h2>
 * <p>The extractor looks for Query objects in:</p>
 * <ol>
 *   <li>Action step arguments (the query that was shown/run)</li>
 *   <li>Step execution results (if actions return Query objects)</li>
 * </ol>
 * 
 * <h2>Model SQL Capture</h2>
 * <p>Ideally, the model SQL should be captured before resolution. This extractor
 * uses {@link Query#modelSql()} as a best-effort approximation. For full
 * fidelity, the application should capture the raw LLM output before resolution.</p>
 */
public class SqlWorkingContextExtractor implements WorkingContextExtractor<SqlQueryPayload> {

	@Override
	public String contextType() {
		return SqlQueryPayload.CONTEXT_TYPE;
	}

	@Override
	public boolean canHandle(ConversationTurnResult turn, PlanExecutionResult executionResult) {
		if (turn.plan() == null || turn.plan().planSteps().isEmpty()) {
			return false;
		}

		// Check if any step has a Query argument
		for (PlanStep step : turn.plan().planSteps()) {
			if (step instanceof PlanStep.ActionStep actionStep) {
				if (hasQueryArgument(actionStep)) {
					return true;
				}
			}
		}

		return false;
	}

	@Override
	public Optional<WorkingContext<SqlQueryPayload>> extract(
			ConversationTurnResult turn,
			PlanExecutionResult executionResult,
			WorkingContext<?> priorWorkingContext) {

		// Find the first Query in the plan arguments
		Query query = findQuery(turn);
		if (query == null) {
			return Optional.empty();
		}

		// Create payload from the query
		SqlQueryPayload payload = SqlQueryPayload.fromQuery(query);

		return Optional.of(WorkingContext.of(SqlQueryPayload.CONTEXT_TYPE, payload));
	}

	/**
	 * Checks if an action step has a Query argument.
	 */
	private boolean hasQueryArgument(PlanStep.ActionStep actionStep) {
		if (actionStep.arguments() == null) {
			return false;
		}
		return actionStep.arguments().stream()
				.map(PlanArgument::value)
				.anyMatch(v -> v instanceof Query);
	}

	/**
	 * Finds the first Query in the plan's action step arguments.
	 */
	private Query findQuery(ConversationTurnResult turn) {
		if (turn.plan() == null) {
			return null;
		}

		for (PlanStep step : turn.plan().planSteps()) {
			if (step instanceof PlanStep.ActionStep actionStep) {
				if (actionStep.arguments() != null) {
					for (PlanArgument arg : actionStep.arguments()) {
						if (arg.value() instanceof Query query) {
							return query;
						}
					}
				}
			}
		}

		return null;
	}
}

