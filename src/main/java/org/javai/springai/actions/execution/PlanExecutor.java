package org.javai.springai.actions.execution;

import org.javai.springai.actions.api.ActionContext;

public interface PlanExecutor {

	ActionContext execute(ExecutablePlan plan) throws PlanExecutionException;

	default ActionContext execute(ExecutablePlan plan, ActionContext context) throws PlanExecutionException {
		// Legacy adapters can override; default delegates to existing execute
		return execute(plan);
	}

	default void beforeExecute(ActionContext context) {
	}

	default void afterExecute(ActionContext context) {
	}

}
