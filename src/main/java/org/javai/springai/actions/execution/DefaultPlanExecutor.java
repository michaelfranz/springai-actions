package org.javai.springai.actions.execution;

import java.util.Optional;
import org.javai.springai.actions.api.ActionContext;

public class DefaultPlanExecutor implements PlanExecutor {

	private ActionContext preexistingContext;

	public DefaultPlanExecutor() {
		this(null);
	}

	public DefaultPlanExecutor(ActionContext preexistingContext) {
		this.preexistingContext = preexistingContext;
	}

	@Override
	public ActionContext execute(ExecutablePlan executablePlan) throws PlanExecutionException {
		try {
			ActionContext context = Optional.ofNullable(preexistingContext).orElse(createContext());
			beforeExecute(context);
			for (ExecutableAction action : executablePlan.executables()) {
				action.apply(context);
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
