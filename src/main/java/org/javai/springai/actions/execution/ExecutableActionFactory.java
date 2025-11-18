package org.javai.springai.actions.execution;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.definition.ActionDefinition;
import org.javai.springai.actions.planning.PlanStep;

public class ExecutableActionFactory {

	private final List<ActionDefinition> actions;
	private final ActionArgumentBinder binder = new ActionArgumentBinder();

	public ExecutableActionFactory(List<ActionDefinition> actions) {
		this.actions = actions;
	}

	public ExecutableAction from(PlanStep step) {
		ActionDefinition def = findDefinition(step.action());
		Method method = def.method();
		Object bean = def.bean();

		return new AbstractExecutableAction(step) {
			public void perform(ActionContext ctx) throws PlanExecutionException {
				Object[] args = binder.bindArguments(method, step.arguments(), ctx);
				try {
					Object result = method.invoke(bean, args);
					Action actionAnno = method.getAnnotation(Action.class);
					// Store return value if contextKey is provided
					if (actionAnno != null && !actionAnno.contextKey().isEmpty() && result != null) {
						ctx.put(actionAnno.contextKey(), result);
					}
				} catch (IllegalAccessException | InvocationTargetException e) {
					throw new PlanExecutionException("Exception invoking action: " + def.name(), e);
				}
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

	public boolean actionExists(String action) {
		return actions.stream().anyMatch(d -> d.name().equals(action));
	}
}