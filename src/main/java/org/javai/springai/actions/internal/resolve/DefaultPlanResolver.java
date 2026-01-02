package org.javai.springai.actions.internal.resolve;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.javai.springai.actions.Plan;
import org.javai.springai.actions.PlanStep;
import org.javai.springai.actions.api.TypeResolver;
import org.javai.springai.actions.internal.bind.ActionBinding;
import org.javai.springai.actions.internal.bind.ActionParameterDescriptor;
import org.javai.springai.actions.internal.parse.RawPlan;
import org.javai.springai.actions.internal.parse.RawPlanStep;
import org.javai.springai.actions.internal.plan.PlanArgument;

/**
 * Default resolver that converts a RawPlan to a bound Plan.
 * <p>
 * This resolver:
 * <ul>
 *   <li>Validates action IDs exist in the registry</li>
 *   <li>Checks parameter counts match</li>
 *   <li>Binds actions to their method implementations</li>
 *   <li>Converts parameter values to their target types</li>
 *   <li>Uses registered TypeResolvers for special types (e.g., Query)</li>
 * </ul>
 * <p>
 * Resolution issues are captured as {@link PlanStep.ErrorStep} entries.
 */
public class DefaultPlanResolver implements PlanResolver {

	private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

	@Override
	public Plan resolve(RawPlan jsonPlan, ResolutionContext context) {
		// Handle null or empty steps - this triggers the noAction handler
		if (jsonPlan.steps() == null || jsonPlan.steps().isEmpty()) {
			return new Plan(jsonPlan.message(), List.of());
		}

		List<PlanStep> resolved = jsonPlan.steps().stream()
				.map(step -> resolveStep(step, context))
				.toList();
		return new Plan(jsonPlan.message(), resolved);
	}

	private static final String NO_ACTION_ID = "noAction";

	private PlanStep resolveStep(RawPlanStep step, ResolutionContext context) {
		String actionId = step.actionId();
		if (actionId == null || actionId.isBlank()) {
			return new PlanStep.ErrorStep("Step has no actionId");
		}

		// Handle special noAction step type
		if (NO_ACTION_ID.equals(actionId)) {
			String message = extractNoActionMessage(step);
			return new PlanStep.NoActionStep(message);
		}

		ActionBinding binding = context.actionRegistry().getActionBinding(actionId);
		if (binding == null) {
			return new PlanStep.ErrorStep("Unknown action: " + actionId);
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
			Optional<String> constraintValidation = validateConstraint(param, outcome.value());
			if (constraintValidation.isPresent()) {
				return new PlanStep.ErrorStep(constraintValidation.get());
			}
			
			Class<?> targetType = resolveType(param.typeName()).orElse(Object.class);
			arguments.add(new PlanArgument(param.name(), outcome.value(), targetType));
		}

		return new PlanStep.ActionStep(binding, arguments);
	}

	/**
	 * Validate parameter constraints (allowed values, regex patterns).
	 * Returns Optional.empty() if valid, error message if invalid.
	 */
	private Optional<String> validateConstraint(ActionParameterDescriptor param, Object value) {
		//noinspection ConstantValue
		if (value == null) {
			return Optional.empty();
		}
		String stringValue = value.toString();
		
		if (param.allowedValues().length > 0) {
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
				return Optional.of("Value for parameter '" + param.name() + "' must be one of: "
						+ String.join(", ", param.allowedValues()));
			}
		}
		
		if (!param.allowedRegex().isBlank()) {
			String pattern = param.allowedRegex();
			boolean matches = param.caseInsensitive()
					? stringValue.toLowerCase().matches(pattern.toLowerCase())
					: stringValue.matches(pattern);
			if (!matches) {
				return Optional.of("Value for parameter '" + param.name() + "' must match pattern: " + pattern);
			}
		}

		return Optional.empty();
	}

	private ConversionOutcome convert(Object raw, ActionParameterDescriptor param, String actionId, ResolutionContext context) {
		Optional<Class<?>> maybeTargetType = resolveType(param.typeName());
		if (maybeTargetType.isEmpty()) {
			return ConversionOutcome.failure("Unknown type: " + param.typeName());
		}
		Class<?> targetType = maybeTargetType.get();
		if (targetType.isInstance(raw)) {
			return ConversionOutcome.success(raw);
		}

		if (targetType.isArray()) {
			return convertArray(raw, targetType.componentType(), param.name(), context);
		}

		if (Collection.class.isAssignableFrom(targetType)) {
			return toList(raw)
					.map(ConversionOutcome::success)
					.orElseGet(() -> ConversionOutcome.failure("Cannot convert to list: " + param.name()));
		}

		return convertScalar(raw, targetType, param.name(), context);
	}

	private ConversionOutcome convertArray(Object raw, Class<?> componentType, String paramName, ResolutionContext context) {
		Object[] elements = toObjectArray(raw);

		Object array = Array.newInstance(componentType, elements.length);
		for (int i = 0; i < elements.length; i++) {
			ConversionOutcome converted = convertScalar(elements[i], componentType, paramName, context);
			if (!converted.success()) {
				return converted;
			}
			Array.set(array, i, converted.value());
		}
		return ConversionOutcome.success(array);
	}

	private ConversionOutcome convertScalar(Object raw, Class<?> targetType, String paramName, ResolutionContext context) {
		// Check for custom TypeResolver first
		Optional<TypeResolver> customResolver = context.typeRegistry()
				.flatMap(registry -> registry.resolver(targetType));
		
		if (customResolver.isPresent()) {
			TypeResolver.ResolveResult result = customResolver.get().resolve(raw, context.context());
			if (result.isSuccess()) {
				return ConversionOutcome.success(result.value().orElse(null));
			} else {
				return ConversionOutcome.failure(
						"Failed to resolve parameter " + paramName + ": " + result.failureReason().orElse("unknown error"));
			}
		}

		// Standard type conversion
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

	private Optional<List<?>> toList(Object raw) {
		if (raw instanceof List<?> list) {
			return Optional.of(list);
		}
		if (raw instanceof Object[] array) {
			return Optional.of(Arrays.asList(array));
		}
		if (raw.getClass().isArray()) {
			return Optional.of(Arrays.asList(toObjectArray(raw)));
		}
		return Optional.empty();
	}

	private Optional<Class<?>> resolveType(String typeName) {
		if (typeName == null || typeName.isBlank()) {
			return Optional.of(Object.class);
		}
		try {
			return Optional.of(switch (typeName) {
				case "int" -> int.class;
				case "long" -> long.class;
				case "double" -> double.class;
				case "float" -> float.class;
				case "boolean" -> boolean.class;
				default -> Class.forName(typeName);
			});
		}
		catch (ClassNotFoundException e) {
			return Optional.empty();
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

	/**
	 * Extract the message from a noAction step.
	 * Looks for a "message" or "reason" parameter in the step.
	 */
	private String extractNoActionMessage(RawPlanStep step) {
		Map<String, Object> params = step.parameters();
		if (params == null || params.isEmpty()) {
			// Fall back to description if no parameters
			return step.description() != null ? step.description() 
					: "No appropriate action could be identified for this request.";
		}
		
		// Try "message" first, then "reason"
		Object message = params.get("message");
		if (message != null) {
			return message.toString();
		}
		
		Object reason = params.get("reason");
		if (reason != null) {
			return reason.toString();
		}
		
		// Fall back to description
		return step.description() != null ? step.description() 
				: "No appropriate action could be identified for this request.";
	}
}
