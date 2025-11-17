package org.javai.springai.actions.execution;

import org.javai.springai.actions.api.ActionContext;

public interface ExecutableAction {
	void perform(ActionContext ctx) throws Exception;
}
