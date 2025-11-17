package org.javai.springai.actions.execution;

import java.util.List;

public interface ActionExecutor {

	void execute(List<ExecutableAction> actions) throws Exception;

}
