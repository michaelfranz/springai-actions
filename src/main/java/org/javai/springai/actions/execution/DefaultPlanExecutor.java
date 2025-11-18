package org.javai.springai.actions.execution;

import org.javai.springai.actions.api.ActionContext;

public class DefaultPlanExecutor implements PlanExecutor {

	@Override
	public ActionContext execute(ExecutablePlan executablePlan) throws PlanExecutionException {
		try {
			ActionContext context = createContext();
			beforeExecute(context);
			for (ExecutableAction action : executablePlan.executables()) {
				action.perform(context);
			}
			afterExecute(context);
			return context;
		} catch (Exception e) {
			throw new PlanExecutionException("Plan execution failed", e);
		}
	}

	protected ActionContext createContext() {
		return new ActionContext();
	}

}
