package org.javai.springai.actions.definition;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.javai.springai.actions.api.Action;


public class ActionRegistry {

	private final Map<String, RegisteredAction> registry = new HashMap<>();

	public void register(Object bean) {
		for (Method method : bean.getClass().getMethods()) {
			if (method.isAnnotationPresent(Action.class)) {
				String name = method.getName();
				registry.put(name, new RegisteredAction(bean, method));
			}
		}
	}

	public RegisteredAction find(String actionName) {
		RegisteredAction ra = registry.get(actionName);
		if (ra == null) {
			throw new IllegalArgumentException("Unknown steps: " + actionName);
		}
		return ra;
	}
}

