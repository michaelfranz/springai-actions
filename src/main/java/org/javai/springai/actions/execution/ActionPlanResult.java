package org.javai.springai.actions.execution;

import java.util.List;
import org.javai.springai.actions.planning.ActionPlan;

public record ActionPlanResult(ActionPlan plan, List<ExecutableAction> executableActions) {

	public void execute(ActionExecutor executor) throws Exception {
		executor.execute(executableActions);
	}
}