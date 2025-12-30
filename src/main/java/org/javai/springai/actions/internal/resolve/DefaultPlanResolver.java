package org.javai.springai.actions.internal.resolve;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.javai.springai.actions.Plan;
import org.javai.springai.actions.PlanStep;
import org.javai.springai.actions.internal.bind.ActionBinding;
import org.javai.springai.actions.internal.bind.ActionParameterDescriptor;
import org.javai.springai.actions.internal.bind.ActionRegistry;
import org.javai.springai.actions.internal.parse.RawPlan;
import org.javai.springai.actions.internal.parse.RawPlanStep;
import org.javai.springai.actions.internal.plan.PlanArgument;
import org.javai.springai.actions.sql.Query;
import org.javai.springai.actions.sql.QueryValidationException;
import org.javai.springai.actions.sql.SqlCatalog;

/**
 * Default resolver that converts a RawPlan to a bound Plan.
 * <p>
 * This resolver:
 * <ul>
 *   <li>Validates action IDs exist in the registry</li>
 *   <li>Checks parameter counts match</li>
 *   <li>Binds actions to their method implementations</li>
 *   <li>Converts parameter values to their target types</li>
 *   <li>Validates SQL queries against the schema catalog (if provided)</li>
 * </ul>
 * <p>
 * Resolution issues are captured as {@link PlanStep.ErrorStep} entries.
 */
public class DefaultPlanResolver implements PlanResolver {

	private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

	@Override
	public Plan resolve(RawPlan jsonPlan, ResolutionContext context) {
		if (jsonPlan == null || context == null || context.registry() == null) {
			return new Plan(null, List.of(new PlanStep.ErrorStep("RawPlan or resolution context is null")));
		}

		if (jsonPlan.steps() == null || jsonPlan.steps().isEmpty()) {
			return new Plan(jsonPlan.message(), List.of());
		}

		List<PlanStep> resolved = jsonPlan.steps().stream()
				.map(step -> resolveStep(step, context))
				.toList();
		return new Plan(jsonPlan.message(), resolved);
	}

	private PlanStep resolveStep(RawPlanStep step, ResolutionContext context) {
		String actionId = step.actionId();
		if (actionId == null || actionId.isBlank()) {
			return new PlanStep.ErrorStep("Step has no actionId");
		}

		ActionBinding binding = context.registry().getActionBinding(actionId);
		if (binding == null) {
			return new PlanStep.ErrorStep("Unknown action id: " + actionId);
		}

		List<ActionParameterDescriptor> params = binding.parameters();
		Map<String, Object> stepParams = step.parameters() != null ? step.parameters() : Map.of();

		// Check arity
		if (stepParams.size() != params.size()) {
			return new PlanStep.ErrorStep(
					"Argument count mismatch for action " + actionId + ": expected " + params.size() 
					+ " got " + stepParams.size());
		}

		// Convert each parameter
		List<PlanArgument> arguments = new ArrayList<>();
		for (ActionParameterDescriptor param : params) {
			Object raw = stepParams.get(param.name());
			ConversionOutcome outcome = convert(raw, param, actionId, context);
			if (!outcome.success()) {
				return new PlanStep.ErrorStep(outcome.errorMessage());
			}
			
			// Validate constraints (allowed values, regex)
			String constraintError = validateConstraint(param, outcome.value());
			if (constraintError != null) {
				return new PlanStep.ErrorStep(constraintError);
			}
			
			arguments.add(new PlanArgument(param.name(), outcome.value(), resolveType(param.typeName())));
		}

		return new PlanStep.ActionStep(binding, arguments);
	}

	/**
	 * Validate parameter constraints (allowed values, regex patterns).
	 * Returns null if valid, error message if invalid.
	 */
	private String validateConstraint(ActionParameterDescriptor param, Object value) {
		if (value == null) {
			return null; // Let downstream handle nulls
		}
		String stringValue = value.toString();
		
		if (param.allowedValues() != null && param.allowedValues().length > 0) {
			boolean match = false;
			for (String allowed : param.allowedValues()) {
				if (param.caseInsensitive()) {
					if (stringValue.equalsIgnoreCase(allowed)) {
						match = true;
						break;
					}
				} else if (stringValue.equals(allowed)) {
					match = true;
					break;
				}
			}
			if (!match) {
				return "Value for parameter '" + param.name() + "' must be one of: "
						+ String.join(", ", param.allowedValues());
			}
		}
		
		if (param.allowedRegex() != null && !param.allowedRegex().isBlank()) {
			String pattern = param.allowedRegex();
			boolean matches = param.caseInsensitive()
					? stringValue.toLowerCase().matches(pattern.toLowerCase())
					: stringValue.matches(pattern);
			if (!matches) {
				return "Value for parameter '" + param.name() + "' must match pattern: " + pattern;
			}
		}
		
		return null;
	}

	private ConversionOutcome convert(Object raw, ActionParameterDescriptor param, String actionId, ResolutionContext context) {
		Class<?> targetType = resolveType(param.typeName());
		if (targetType == null) {
			return ConversionOutcome.failure(
					"Unknown parameter type for action " + actionId + ": " + param.typeName());
		}
		if (raw == null) {
			return ConversionOutcome.success(null);
		}
		if (targetType.isInstance(raw)) {
			return ConversionOutcome.success(raw);
		}

		if (targetType.isArray()) {
			return convertArray(raw, targetType.componentType(), param.dslId(), actionId, param.name(), context);
		}

		if (Collection.class.isAssignableFrom(targetType)) {
			List<?> asList = toList(raw);
			if (asList == null) {
				return ConversionOutcome.failure(
						"Expected a collection-compatible value for parameter " + param.name());
			}
			return ConversionOutcome.success(asList);
		}

		return convertScalar(raw, targetType, param.dslId(), actionId, param.name(), context);
	}

	private ConversionOutcome convertArray(Object raw, Class<?> componentType, String dslId, String actionId, String paramName, ResolutionContext context) {
		Object[] elements = toObjectArray(raw);
		if (elements == null) {
			return ConversionOutcome.failure("Expected an array or collection value for parameter " + paramName);
		}

		Object array = Array.newInstance(componentType, elements.length);
		for (int i = 0; i < elements.length; i++) {
			ConversionOutcome converted = convertScalar(elements[i], componentType, dslId, actionId, paramName, context);
			if (!converted.success()) {
				return converted;
			}
			Array.set(array, i, converted.value());
		}
		return ConversionOutcome.success(array);
	}

	private ConversionOutcome convertScalar(Object raw, Class<?> targetType, String dslId, String actionId, String paramName, ResolutionContext context) {
		// Handle SQL strings for Query targets - now with catalog for validation
		if (raw instanceof String s && Query.class.isAssignableFrom(targetType)) {
			return convertSqlString(s, paramName, context.sqlCatalog());
		}

		Object normalized = normalizeRaw(raw, targetType);
		try {
			if (targetType == String.class) {
				return ConversionOutcome.success(normalized.toString());
			}
			if (targetType.isEnum()) {
				return convertEnum(normalized, targetType, paramName);
			}
			if (targetType == Integer.class || targetType == int.class) {
				return ConversionOutcome.success(Integer.valueOf(normalized.toString()));
			}
			if (targetType == Long.class || targetType == long.class) {
				return ConversionOutcome.success(Long.valueOf(normalized.toString()));
			}
			if (targetType == Double.class || targetType == double.class) {
				return ConversionOutcome.success(Double.valueOf(normalized.toString()));
			}
			if (targetType == Float.class || targetType == float.class) {
				return ConversionOutcome.success(Float.valueOf(normalized.toString()));
			}
			if (targetType == Boolean.class || targetType == boolean.class) {
				return ConversionOutcome.success(Boolean.valueOf(normalized.toString()));
			}

			// Fallback to JSON mapping for complex types and records
			Object mapped = mapper.convertValue(normalized, targetType);
			return ConversionOutcome.success(mapped);
		}
		catch (Exception ex) {
			return ConversionOutcome.failure(
					"Failed to convert parameter " + paramName + " to " + targetType.getSimpleName() + ": "
							+ ex.getMessage());
		}
	}

	private ConversionOutcome convertSqlString(String sql, String paramName, SqlCatalog catalog) {
		try {
			// Pass catalog for schema validation (table/column references)
			Query query = Query.fromSql(sql, catalog);
			return ConversionOutcome.success(query);
		} catch (QueryValidationException e) {
			return ConversionOutcome.failure(
					"Failed to parse SQL for parameter " + paramName + ": " + e.getMessage());
		}
	}

	private Object normalizeRaw(Object raw, Class<?> targetType) {
		if (raw instanceof String s && looksLikeJson(s)) {
			try {
				return mapper.readValue(s, Object.class);
			}
			catch (Exception ignored) {
				// Fall through
			}
		}
		return raw;
	}

	private boolean looksLikeJson(String s) {
		String trimmed = s.trim();
		return (!trimmed.isEmpty()) && (trimmed.startsWith("{") || trimmed.startsWith("["));
	}

	private ConversionOutcome convertEnum(Object raw, Class<?> targetType, String paramName) {
		if (raw == null) {
			return ConversionOutcome.success(null);
		}
		String candidate = raw.toString();
		for (Object constant : targetType.getEnumConstants()) {
			if (constant.toString().equalsIgnoreCase(candidate) || ((Enum<?>) constant).name().equalsIgnoreCase(candidate)) {
				return ConversionOutcome.success(constant);
			}
		}
		String allowed = Arrays.stream(targetType.getEnumConstants())
				.map(Object::toString)
				.collect(Collectors.joining(", "));
		return ConversionOutcome.failure(
				"Failed to convert parameter " + paramName + " to " + targetType.getSimpleName() + ": allowed values are " + allowed);
	}

	private Object[] toObjectArray(Object raw) {
		if (raw == null) {
			return new Object[0];
		}
		if (raw instanceof Object[] objectArray) {
			return objectArray;
		}
		if (raw instanceof List<?> list) {
			return list.toArray();
		}
		if (raw.getClass().isArray()) {
			int length = Array.getLength(raw);
			Object[] copy = new Object[length];
			for (int i = 0; i < length; i++) {
				copy[i] = Array.get(raw, i);
			}
			return copy;
		}
		return new Object[] { raw };
	}

	private List<?> toList(Object raw) {
		if (raw instanceof List<?> list) {
			return list;
		}
		if (raw instanceof Object[] array) {
			return Arrays.asList(array);
		}
		if (raw != null && raw.getClass().isArray()) {
			return Arrays.asList(toObjectArray(raw));
		}
		return null;
	}

	private Class<?> resolveType(String typeName) {
		if (typeName == null || typeName.isBlank()) {
			return Object.class;
		}
		try {
			return switch (typeName) {
				case "int" -> int.class;
				case "long" -> long.class;
				case "double" -> double.class;
				case "float" -> float.class;
				case "boolean" -> boolean.class;
				default -> Class.forName(typeName);
			};
		}
		catch (ClassNotFoundException e) {
			return null;
		}
	}

	private record ConversionOutcome(boolean success, Object value, String errorMessage) {
		static ConversionOutcome success(Object value) {
			return new ConversionOutcome(true, value, null);
		}

		static ConversionOutcome failure(String errorMessage) {
			return new ConversionOutcome(false, null, errorMessage);
		}
	}
}
