package org.javai.springai.actions.exec;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.stream.Streams;
import org.javai.springai.actions.bind.ActionBinding;
import org.javai.springai.actions.bind.ActionParameterDescriptor;
import org.javai.springai.actions.bind.ActionRegistry;
import org.javai.springai.actions.plan.Plan;
import org.javai.springai.actions.plan.PlanStep;
import org.javai.springai.actions.sql.Query;
import org.javai.springai.actions.sql.QueryValidationException;

/**
 * Default resolver that maps Plan steps to ActionBindings and resolves arguments.
 * Resolution issues are captured as ResolvedStep.ErrorStep entries.
 */
public class DefaultPlanResolver implements PlanResolver {

	private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

	@Override
	public ResolvedPlan resolve(Plan plan, ActionRegistry registry) {
		if (plan == null || registry == null) {
			return new ResolvedPlan(List.of(new ResolvedStep.ErrorStep("Plan or registry is null")));
		}

		List<ResolvedStep> resolved = plan.planSteps()
				.stream()
				.map(step -> resolveStep(step, registry))
				.toList();
		return new ResolvedPlan(resolved);
	}

	private ResolvedStep resolveStep(PlanStep step, ActionRegistry registry) {
		return switch (step) {
			case PlanStep.ErrorStep errorStep -> resolveErrorStep(errorStep.assistantMessage());
			case PlanStep.PendingActionStep pendingStep -> resolvePendingActionStep(pendingStep);
			case PlanStep.ActionStep actionStep -> resolveActionStep(actionStep, registry);
		};
	}

	private static ResolvedStep.ErrorStep resolveErrorStep(String errorStep) {
		return new ResolvedStep.ErrorStep(errorStep);
	}

	private ResolvedStep.PendingActionStep resolvePendingActionStep(PlanStep.PendingActionStep pendingStep) {
		return new ResolvedStep.PendingActionStep(
				pendingStep.assistantMessage(),
				pendingStep.actionId(),
				Streams.of(pendingStep.pendingParams())
						.map(ps -> new ResolvedStep.PendingParam(ps.name(), ps.message()))
						.toArray(ResolvedStep.PendingParam[]::new),
				pendingStep.providedParams());
	}

	private ResolvedStep resolveActionStep(PlanStep.ActionStep actionStep, ActionRegistry registry) {
		String actionId = actionStep.actionId();
		ActionBinding binding = registry.getActionBinding(actionId);
		if (binding == null) {
			return new ResolvedStep.ErrorStep("Unknown action id: " + actionId);
		}

		List<ActionParameterDescriptor> params = binding.parameters();
		Object[] args = actionStep.actionArguments();
		if (args == null) {
			args = new Object[0];
		}
		if (args.length != params.size()) {
			return new ResolvedStep.ErrorStep(
					"Argument count mismatch for action " + actionId + ": expected " + params.size() + " got "
							+ args.length);
		}

		List<ResolvedArgument> resolvedArgs = new ArrayList<>();
		for (int i = 0; i < params.size(); i++) {
			ActionParameterDescriptor param = params.get(i);
			Object raw = args[i];
			ConversionOutcome outcome = convert(raw, param, actionId);
			if (!outcome.success()) {
				return new ResolvedStep.ErrorStep(outcome.errorMessage());
			}
			resolvedArgs.add(new ResolvedArgument(param.name(), outcome.value(), resolveType(param.typeName())));
		}
		return new ResolvedStep.ActionStep(binding, resolvedArgs);
	}

	private ConversionOutcome convert(Object raw, ActionParameterDescriptor param, String actionId) {
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
			return convertArray(raw, targetType.componentType(), param.dslId(), actionId, param.name());
		}

		if (Collection.class.isAssignableFrom(targetType)) {
			List<?> asList = toList(raw);
			if (asList == null) {
				return ConversionOutcome.failure(
						"Expected a collection-compatible value for parameter " + param.name());
			}
			return ConversionOutcome.success(asList);
		}

		return convertScalar(raw, targetType, param.dslId(), actionId, param.name());
	}

	private ConversionOutcome convertArray(Object raw, Class<?> componentType, String dslId, String actionId, String paramName) {
		Object[] elements = toObjectArray(raw);
		if (elements == null) {
			return ConversionOutcome.failure("Expected an array or collection value for parameter " + paramName);
		}

		Object array = Array.newInstance(componentType, elements.length);
		for (int i = 0; i < elements.length; i++) {
			ConversionOutcome converted = convertScalar(elements[i], componentType, dslId, actionId, paramName);
			if (!converted.success()) {
				return converted;
			}
			Array.set(array, i, converted.value());
		}
		return ConversionOutcome.success(array);
	}

	private ConversionOutcome convertScalar(Object raw, Class<?> targetType, String dslId, String actionId, String paramName) {
		// Handle SQL strings for Query targets
		if (raw instanceof String s && Query.class.isAssignableFrom(targetType)) {
			return convertSqlString(s, paramName);
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

	/**
	 * Convert a SQL string to a Query object.
	 * This handles the case where plans contain SQL strings for Query parameters.
	 */
	private ConversionOutcome convertSqlString(String sql, String paramName) {
		try {
			Query query = Query.fromSql(sql);
			return ConversionOutcome.success(query);
		} catch (QueryValidationException e) {
			return ConversionOutcome.failure(
					"Failed to parse SQL for parameter " + paramName + ": " + e.getMessage());
		}
	}

	/**
	 * Normalize raw values before conversion.
	 * Handles JSON strings → parsed to Map/List.
	 */
	private Object normalizeRaw(Object raw, Class<?> targetType) {
		// Handle JSON strings
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

