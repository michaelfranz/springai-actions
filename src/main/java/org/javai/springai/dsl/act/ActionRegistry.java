package org.javai.springai.dsl.act;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionParam;
import org.javai.springai.dsl.bind.TypeFactoryRegistry;
import org.springframework.ai.tool.annotation.Tool;

public final class ActionRegistry {

	private final Map<String, ActionEntry> entries = new LinkedHashMap<>();

	public void registerActions(Object bean) {
		for (Method method : bean.getClass().getMethods()) {
			Action action = method.getAnnotation(Action.class);
			Tool tool = method.getAnnotation(Tool.class);
			if (action == null && tool == null) continue;

			String id = createActionId(method, tool);
			List<ActionParameterDescriptor> actionParameterDefinitions = createActionParameterDefinitions(bean, method);
			String description = action != null
					? createActionDescription(action, method.getName())
					: createToolDescription(tool, method.getName());

			if (entries.containsKey(id)) {
				throw new IllegalStateException("Duplicate action definition: " + id);
			}
			entries.put(id, new ActionEntry(id, description, actionParameterDefinitions, null, bean, method));
		}
	}

	private static List<ActionParameterDescriptor> createActionParameterDefinitions(Object bean, Method method) {
		List<ActionParameterDescriptor> actionParameterDefinitions = new ArrayList<>();
		for (Parameter parameter : method.getParameters()) {
			actionParameterDefinitions.add(createActionParameterDefinition(parameter));
		}
		return actionParameterDefinitions;
	}

	private static ActionParameterDescriptor createActionParameterDefinition(Parameter parameter) {
		ActionParam annotation = parameter.getAnnotation(ActionParam.class);
		String dslId = TypeFactoryRegistry.getDslIdForType(parameter.getType()).orElse(null);
		String[] derivedAllowedValues = deriveAllowedValues(parameter, annotation);
		String allowedRegex = annotation != null ? annotation.allowedRegex() : "";
		boolean caseInsensitive = annotation != null && annotation.caseInsensitive();
		return new ActionParameterDescriptor(
				parameter.getName(),
				parameter.getType().getName(),
				deriveShortTypeId(parameter.getType(), dslId),
				createActionParameterDescription(annotation, parameter.getName()),
				dslId,
				derivedAllowedValues,
				allowedRegex,
				caseInsensitive
		);
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

	private static String createToolDescription(Tool tool, String name) {
		if (tool != null && tool.description() != null && !tool.description().isBlank()) {
			return tool.description();
		}
		return "Tool to " + nameBreakdown(name);
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

	private static String createActionId(Method method, Tool tool) {
		if (tool != null && tool.name() != null && !tool.name().isBlank()) {
			return tool.name();
		}
		// Some Spring AI versions also offer value(); prefer it if present
		if (tool != null) {
			try {
				Method valueMethod = tool.annotationType().getMethod("value");
				Object value = valueMethod.invoke(tool);
				if (value instanceof String s && !s.isBlank()) {
					return s;
				}
			}
			catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
				// fall back to method name
			}
		}
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