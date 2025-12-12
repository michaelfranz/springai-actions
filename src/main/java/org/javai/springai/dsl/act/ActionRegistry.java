package org.javai.springai.dsl.act;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionParam;

public final class ActionRegistry {

	// References to the actual bean methods which embody the actions' functionality
	private final Map<String, ActionDefinition> actionDefinitions = new HashMap<>();

	// Set of all action specs... these are what are communicated to the LLM
	private final List<ActionSpec> actionSpecs = new ArrayList<>();

	public void registerActions(Object bean) {
		for (Method method : bean.getClass().getMethods()) {
			Action action = method.getAnnotation(Action.class);
			if (action == null) continue;

			String id = createActionId(bean, method);
			List<ActionParameterSpec> actionParameterDefinitions = createActionParameterDefinitions(bean, method);

			var previous = actionDefinitions.put(
					id,
					new ActionDefinition(
							id,
							bean,
							method,
							actionParameterDefinitions
					));
			assert previous == null : "Duplicate action definition: " + id;

			String description = createActionDescription(action, method.getName());
			List<ActionParameterSpec> parameterSpecs = createActionParameterSpecs(bean, method);
			actionSpecs.add(new ActionSpec(id, description, parameterSpecs));
		}
	}

	private static List<ActionParameterSpec> createActionParameterDefinitions(Object bean, Method method) {
		List<ActionParameterSpec> actionParameterDefinitions = new ArrayList<>();
		for (Parameter parameter : method.getParameters()) {
			actionParameterDefinitions.add(createActionParameterDefinition(parameter));
		}
		return actionParameterDefinitions;
	}

	private static ActionParameterSpec createActionParameterDefinition(Parameter parameter) {
		ActionParam annotation = parameter.getAnnotation(ActionParam.class);
		return new ActionParameterSpec(
				parameter.getClass().getName(),
				createActionParameterDescription(annotation, parameter.getName())
		);
	}

	private static String createActionParameterDescription(ActionParam actionParam, String name) {
		return !actionParam.description().isBlank()
				? actionParam.description()
				: "Parameter to " + nameBreakdown(name);
	}


	private static String createActionDescription(Action action, String name) {
		return !action.description().isBlank()
				? action.description()
				: "Action to " + nameBreakdown(name);
	}

	private static String nameBreakdown(String name) {
		// Split method name based on camelCase / PascalCase / acronym boundaries
		// e.g. "getTableName" -> "get table name", "HTTPServerURL" -> "http server url"
		if (name == null || name.isBlank()) return "";

		String withAcronymSplit = name
				// Split between an acronym and a normal word: HTTPServer -> HTTP Server
				.replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
				// Split between lower/digit followed by upper: getTable -> get Table, v2Name -> v2 Name
				.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
				// Replace underscores and hyphens with spaces if present
				.replace('_', ' ')
				.replace('-', ' ')
				.trim();

		return withAcronymSplit.toLowerCase();
	}

	private static String createActionId(Object bean, Method method) {
		return bean.getClass().getName() + "." + method.getName();
	}

	public ActionDefinition getActionDefinition(String actionId) {
		return actionDefinitions.get(actionId);
	}

	public List<ActionSpec> getActionSpecs() {
		return Collections.unmodifiableList(actionSpecs);
	}

	private List<ActionParameterSpec> createActionParameterSpecs(Object bean, Method method) {
		return null;
	}
}