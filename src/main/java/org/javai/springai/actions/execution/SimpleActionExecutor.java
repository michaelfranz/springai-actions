package org.javai.springai.actions.execution;

import java.util.List;
import org.javai.springai.actions.api.ActionContext;

public class SimpleActionExecutor implements ActionExecutor {
		public void execute(List<ExecutableAction> actions) throws Exception {
			ActionContext ctx = new ActionContext();
			for (ExecutableAction action : actions) {
				action.perform(ctx);
			}
		}
	}