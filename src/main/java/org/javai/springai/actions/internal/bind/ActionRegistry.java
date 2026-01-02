package org.javai.springai.actions.internal.bind;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.api.ActionParam;
import org.javai.springai.actions.api.FromContext;
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
			if (parameter.getType() == ActionContext.class || parameter.isAnnotationPresent(FromContext.class)) {
				// Injected at execution time; not exposed to LLM
				continue;
			}
			actionParameterDefinitions.add(createActionParameterDefinition(parameter));
		}
		return actionParameterDefinitions;
	}

	private static ActionParameterDescriptor createActionParameterDefinition(Parameter parameter) {
		ActionParam annotation = parameter.getAnnotation(ActionParam.class);
		Optional<String> dslId = getDslIdForType(parameter.getType());
		String[] derivedAllowedValues = deriveAllowedValues(parameter, annotation);
		
		// Handle optional @ActionParam annotation - use defaults when not present
		String allowedRegex = annotation != null ? annotation.allowedRegex() : "";
		boolean caseInsensitive = annotation != null && annotation.caseInsensitive();
		String[] examples = annotation != null ? annotation.examples() : new String[0];
		
		return new ActionParameterDescriptor(
				parameter.getName(),
				parameter.getType().getName(),
				deriveShortTypeId(parameter.getType(), dslId),
				createActionParameterDescription(annotation, parameter.getName(), parameter.getType()),
				derivedAllowedValues,
				allowedRegex,
				caseInsensitive,
				examples
		);
	}

	/**
	 * Get the DSL ID for a parameter type.
	 * Query types are specially recognized; other types return empty.
	 */
	private static Optional<String> getDslIdForType(Class<?> type) {
		if (Query.class.isAssignableFrom(type)) {
			return Optional.of("sql-query");
		}
		return Optional.empty();
	}

	private static String[] deriveAllowedValues(Parameter parameter, ActionParam annotation) {
		// Explicit allowedValues wins (if annotation is present)
		if (annotation != null && annotation.allowedValues().length > 0) {
			return annotation.allowedValues();
		}
		// Fall back to enum constants if the type is an enum
		Class<?> type = parameter.getType();
		if (type.isEnum()) {
			Object[] constants = type.getEnumConstants();
			if (constants != null && constants.length > 0) {
				return Arrays.stream(constants).map(Object::toString).toArray(String[]::new);
			}
		}
		return new String[0];
	}

	/**
	 * Create a human-readable description for a parameter.
	 * 
	 * <p>If {@code @ActionParam} is present with a description, that is used.
	 * Otherwise, a default description is derived from the parameter name and type,
	 * converting camelCase to readable text.</p>
	 * 
	 * @param actionParam the annotation (may be null)
	 * @param name the parameter name
	 * @param type the parameter type
	 * @return a human-readable description
	 */
	private static String createActionParameterDescription(ActionParam actionParam, String name, Class<?> type) {
		// Use explicit description if provided
		if (actionParam != null && !actionParam.description().isBlank()) {
			return actionParam.description();
		}
		// Generate default description from name and type
		return deriveDefaultDescription(name, type);
	}

	/**
	 * Derive a default description from parameter name and type.
	 * 
	 * <p>Examples:
	 * <ul>
	 *   <li>{@code productName} (String) → "The product name"</li>
	 *   <li>{@code quantity} (int) → "The quantity (integer)"</li>
	 *   <li>{@code isEnabled} (boolean) → "Whether is enabled"</li>
	 *   <li>{@code orderStatus} (OrderStatus enum) → "The order status"</li>
	 * </ul>
	 */
	private static String deriveDefaultDescription(String name, Class<?> type) {
		String readableName = nameBreakdown(name);
		
		// Handle boolean parameters specially - they often represent flags
		if (type == boolean.class || type == Boolean.class) {
			if (readableName.startsWith("is ")) {
				return "Whether " + readableName.substring(3);
			}
			if (readableName.startsWith("has ") || readableName.startsWith("can ") || readableName.startsWith("should ")) {
				return "Whether " + readableName;
			}
			return "Whether " + readableName + " is true";
		}
		
		// Add type hint for non-obvious types
		String typeHint = deriveTypeHint(type);
		if (!typeHint.isEmpty()) {
			return "The " + readableName + " (" + typeHint + ")";
		}
		
		return "The " + readableName;
	}

	/**
	 * Derive a human-readable type hint for common types.
	 */
	private static String deriveTypeHint(Class<?> type) {
		if (type == int.class || type == Integer.class) {
			return "integer";
		}
		if (type == long.class || type == Long.class) {
			return "integer";
		}
		if (type == double.class || type == Double.class || type == float.class || type == Float.class) {
			return "number";
		}
		if (type == java.math.BigDecimal.class) {
			return "decimal number";
		}
		if (type.isEnum()) {
			return "one of: " + String.join(", ", 
					Arrays.stream(type.getEnumConstants()).map(Object::toString).toArray(String[]::new));
		}
		if (List.class.isAssignableFrom(type)) {
			return "list";
		}
		if (Map.class.isAssignableFrom(type)) {
			return "map";
		}
		// String and other types don't need a hint
		return "";
	}

	private static String deriveShortTypeId(Class<?> type, Optional<String> dslId) {
		String simple = type.getSimpleName();
		return dslId
				.filter(id -> !id.isBlank())
				.map(id -> id + ":" + simple)
				.orElse(simple);
	}


	private static String createActionDescription(Action action, String name) {
		return !action.description().isBlank()
				? action.description()
				: "Action to " + nameBreakdown(name);
	}

	private static String nameBreakdown(String name) {
		// Split method name based on camelCase / PascalCase / acronym boundaries
		// e.g. "getTableName" -> "get table name", "HTTPServerURL" -> "http server url"
		if (name.isBlank()) return "";

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