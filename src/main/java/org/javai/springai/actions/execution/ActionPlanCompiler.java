package org.javai.springai.actions.execution;

import java.util.List;
import org.javai.springai.actions.planning.ActionPlan;

public class ActionPlanCompiler {

	private final ExecutableActionFactory factory;

	public ActionPlanCompiler(ExecutableActionFactory factory) {
		this.factory = factory;
	}

	public List<ExecutableAction> compile(ActionPlan plan) {
		return plan.steps().stream()
				.map(factory::from)
				.toList();
	}
}