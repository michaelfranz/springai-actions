package org.javai.springai.actions.internal.bind;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.api.ActionParam;
import org.javai.springai.actions.sql.Query;

public final class ActionRegistry {

	private final Map<String, ActionEntry> entries = new LinkedHashMap<>();

	public void registerActions(Object bean) {
		for (Method method : bean.getClass().getMethods()) {
			Action action = method.getAnnotation(Action.class);
			if (action == null) continue;

			String id = createActionId(bean, method);
			List<ActionParameterDescriptor> actionParameterDefinitions = createActionParameterDefinitions(bean, method);
			String description = createActionDescription(action, method.getName());

			if (entries.containsKey(id)) {
				throw new IllegalStateException("Duplicate action definition: " + id);
			}
			String contextKey = action.contextKey();
			entries.put(id, new ActionEntry(id, description, actionParameterDefinitions, null, bean, method, contextKey));
		}
	}

	private static List<ActionParameterDescriptor> createActionParameterDefinitions(Object bean, Method method) {
		List<ActionParameterDescriptor> actionParameterDefinitions = new ArrayList<>();
		for (Parameter parameter : method.getParameters()) {
			if (parameter.getType() == ActionContext.class) {
				// Injected at execution time; not exposed to LLM
				continue;
			}
			actionParameterDefinitions.add(createActionParameterDefinition(parameter));
		}
		return actionParameterDefinitions;
	}

	private static ActionParameterDescriptor createActionParameterDefinition(Parameter parameter) {
		ActionParam annotation = parameter.getAnnotation(ActionParam.class);
		String dslId = getDslIdForType(parameter.getType());
		String[] derivedAllowedValues = deriveAllowedValues(parameter, annotation);
		String allowedRegex = annotation != null ? annotation.allowedRegex() : "";
		boolean caseInsensitive = annotation != null && annotation.caseInsensitive();
		String[] examples = annotation != null ? annotation.examples() : new String[0];
		return new ActionParameterDescriptor(
				parameter.getName(),
				parameter.getType().getName(),
				deriveShortTypeId(parameter.getType(), dslId),
				createActionParameterDescription(annotation, parameter.getName()),
				dslId,
				derivedAllowedValues,
				allowedRegex,
				caseInsensitive,
				examples
		);
	}

	/**
	 * Get the DSL ID for a parameter type.
	 * Query types are specially recognized; other types return null.
	 */
	private static String getDslIdForType(Class<?> type) {
		if (Query.class.isAssignableFrom(type)) {
			return "sql-query";
		}
		return null;
	}

	private static String[] deriveAllowedValues(Parameter parameter, ActionParam annotation) {
		// Explicit allowedValues wins
		if (annotation != null && annotation.allowedValues().length > 0) {
			return annotation.allowedValues();
		}
		Class<?> type = parameter.getType();
		if (type.isEnum()) {
			Object[] constants = type.getEnumConstants();
			if (constants != null && constants.length > 0) {
				return Arrays.stream(constants).map(Object::toString).toArray(String[]::new);
			}
		}
		return new String[0];
	}

	private static String createActionParameterDescription(ActionParam actionParam, String name) {
		if (actionParam != null && !actionParam.description().isBlank()) {
			return actionParam.description();
		}
		return "Parameter to " + nameBreakdown(name);
	}

	private static String deriveShortTypeId(Class<?> type, String dslId) {
		String simple = type.getSimpleName();
		if (dslId != null && !dslId.isBlank()) {
			return dslId + ":" + simple;
		}
		return simple;
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
		return method.getName();
	}

	public ActionBinding getActionBinding(String actionId) {
		ActionEntry entry = entries.get(actionId);
		return entry != null ? entry.binding() : null;
	}

	public List<ActionBinding> getActionBindings() {
		return entries.values().stream().map(ActionEntry::binding).toList();
	}

	public List<ActionDescriptor> getActionDescriptors() {
		return entries.values().stream().map(ActionEntry::descriptor).toList();
	}

}