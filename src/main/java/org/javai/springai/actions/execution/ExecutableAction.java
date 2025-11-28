package org.javai.springai.actions.execution;

import java.util.function.Function;
import org.javai.springai.actions.api.ActionContext;

public interface ExecutableAction extends Function<ActionContext, ActionResult> {
	default ActionMetadata metadata() {
		return ActionMetadata.empty();
	}

	default String describe() {
		return metadata().describe();
	}
}
