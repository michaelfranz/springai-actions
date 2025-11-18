package org.javai.springai.actions.execution;

import org.javai.springai.actions.api.ActionContext;

public interface PlanExecutor {

	ActionContext execute(ExecutablePlan plan) throws PlanExecutionException;

	default void beforeExecute(ActionContext context) {
	}

	default void afterExecute(ActionContext context) {
	}

}
