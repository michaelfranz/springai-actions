package org.javai.springai.actions.execution;

import java.lang.reflect.Method;
import java.util.List;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.definition.ActionDefinition;
import org.javai.springai.actions.planning.ActionStep;

public class ExecutableActionFactory {

	private final List<ActionDefinition> actions;
	private final ActionArgumentBinder binder = new ActionArgumentBinder();

	public ExecutableActionFactory(List<ActionDefinition> actions) {
		this.actions = actions;
	}

	public ExecutableAction from(ActionStep step) {
		ActionDefinition def = findDefinition(step.action());
		Method method = def.method();
		Object bean = def.bean();

		return ctx -> {
			Object[] args = binder.bindArguments(method, step.arguments(), ctx);
			Object result = method.invoke(bean, args);

			// Store return value if contextKey is provided
			Action actionAnno = method.getAnnotation(Action.class);
			if (actionAnno != null && !actionAnno.contextKey().isEmpty() && result != null) {
				ctx.put(actionAnno.contextKey(), result);
			}
		};
	}

	private ActionDefinition findDefinition(String actionName) {
		return actions.stream()
				.filter(d -> d.name().equals(actionName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"Unknown action: " + actionName));
	}
}