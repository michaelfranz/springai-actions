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
		if (jsonPlan.steps().isEmpty()) {
			return new Plan(jsonPlan.message(), List.of());
		}

		List<PlanStep> resolved = jsonPlan.steps().stream()
				.map(step -> resolveStep(step, context))
				.toList();
		return new Plan(jsonPlan.message(), resolved);
	}

	private static final String NO_ACTION_ID = "noAction";

	private PlanStep resolveStep(RawPlanStep step, ResolutionContext context) {
		// Handle error step from LLM
		if (step.isError()) {
			return new PlanStep.ErrorStep(step.reason());
		}
		
		// Handle no-action step from LLM
		if (step.isNoAction()) {
			return new PlanStep.NoActionStep(step.reason());
		}
		
		// Handle pending step from LLM
		if (step.isPending()) {
			return resolvePendingStep(step);
		}
		
		String actionId = step.actionId();
		if (actionId.isBlank()) {
			return new PlanStep.ErrorStep("Step has no actionId");
		}

		// Handle special noAction step type (action-based fallback)
		if (NO_ACTION_ID.equals(actionId)) {
			String message = extractNoActionMessage(step);
			return new PlanStep.NoActionStep(message);
		}

		ActionBinding binding = context.actionRegistry().getActionBinding(actionId);
		if (binding == null) {
			return new PlanStep.ErrorStep("Unknown action: " + actionId);
		}

		List<ActionParameterDescriptor> params = binding.parameters();
		Map<String, Object> stepParams = step.parameters();

		// Check arity
		if (stepParams.size() != params.size()) {
			return new PlanStep.ErrorStep(
					"Argument count mismatch for action " + actionId + ": expected " + params.size() 
					+ " got " + stepParams.size());
		}

		// Convert each parameter - with fallback for when LLM uses wrong parameter names
		List<PlanArgument> arguments = new ArrayList<>();
		List<String> unusedProvidedParams = new ArrayList<>(stepParams.keySet());
		
		for (int i = 0; i < params.size(); i++) {
			ActionParameterDescriptor param = params.get(i);
			Object raw = stepParams.get(param.name());
			
			// If parameter not found by exact name, try to find a match by position (if arity matches)
			if (raw == null && stepParams.size() == params.size() && i < unusedProvidedParams.size()) {
				// LLM provided the right number of params but with wrong names
				// Try to match by position - use the corresponding unused provided param
				String fallbackKey = unusedProvidedParams.get(i);
				raw = stepParams.get(fallbackKey);
			}
			
			// If still null or wrong structure, consider converting to PENDING
			if (raw == null) {
				// LLM didn't provide this parameter at all - return PENDING
				return createPendingForMissingParam(actionId, param, stepParams, step.description());
			}
			
			ConversionOutcome outcome = convert(raw, param, actionId, context);
			if (!outcome.success()) {
				// Conversion failed - might be due to incomplete/wrong data structure
				// Check if this looks like partial data that should trigger PENDING
				if (looksLikePartialData(raw, param)) {
					return createPendingForIncompleteParam(actionId, param, raw, step.description());
				}
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
		if (typeName.isBlank()) {
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
	 * Resolve a pending action step from the LLM response.
	 * Converts the raw pending params to PlanStep.PendingParam objects.
	 */
	private PlanStep resolvePendingStep(RawPlanStep step) {
		String actionId = step.actionId();
		String description = step.description();
		
		// Convert pending params
		PlanStep.PendingParam[] pendingParams;
		if (!step.pendingParams().isEmpty()) {
			pendingParams = step.pendingParams().stream()
					.map(p -> new PlanStep.PendingParam(
							p.name(),
							p.prompt()))
					.toArray(PlanStep.PendingParam[]::new);
		} else {
			pendingParams = new PlanStep.PendingParam[0];
		}
		
		// Get provided params
		Map<String, Object> providedParams = step.providedParams();
		
		return new PlanStep.PendingActionStep(description, actionId, pendingParams, providedParams);
	}
	
	/**
	 * Create a PENDING step when an expected parameter is completely missing.
	 */
	private PlanStep createPendingForMissingParam(String actionId, ActionParameterDescriptor param,
			Map<String, Object> providedByLlm, String description) {
		PlanStep.PendingParam[] pendingParams = new PlanStep.PendingParam[] {
			new PlanStep.PendingParam(param.name(),
					"Please provide " + param.name() + " (" + param.description() + ")")
		};
		
		// Include what the LLM did provide (even if with wrong names)
		return new PlanStep.PendingActionStep(
				description,
				actionId, pendingParams, providedByLlm);
	}
	
	/**
	 * Create a PENDING step when a parameter has incomplete data (e.g., missing required nested fields).
	 */
	private PlanStep createPendingForIncompleteParam(String actionId, ActionParameterDescriptor param, 
			Object partialData, String description) {
		PlanStep.PendingParam[] pendingParams = new PlanStep.PendingParam[] {
			new PlanStep.PendingParam(param.name(),
					"Please provide complete " + param.name() + " - some required fields are missing")
		};
		
		// Include the partial data as providedParams
		Map<String, Object> provided = new java.util.HashMap<>();
		provided.put(param.name() + "_partial", partialData);
		
		return new PlanStep.PendingActionStep(
				description,
				actionId, pendingParams, provided);
	}
	
	/**
	 * Check if the raw data looks like partial/incomplete data that should trigger PENDING.
	 * This catches cases where the LLM provided some data but not in the expected structure.
	 */
	private boolean looksLikePartialData(Object raw, ActionParameterDescriptor param) {
		// If raw is a simple value but param expects a complex type, it might be partial data

		// If the param description mentions required nested fields, and raw is a simple string,
		// it's likely the LLM provided partial data
		if (raw instanceof String && param.description().toLowerCase().contains("period")) {
			return true;
		}
		
		// If param expects a complex type and raw is a primitive, it's incomplete
		String typeName = param.typeName();
		if (!typeName.startsWith("java.lang.") && (raw instanceof String || raw instanceof Number || raw instanceof Boolean)) {
			return true;
		}
		
		return false;
	}
	
	/**
	 * Extract the message from a noAction step.
	 * Looks for a "message" or "reason" parameter in the step.
	 */
	private String extractNoActionMessage(RawPlanStep step) {
		Map<String, Object> params = step.parameters();
		if (params.isEmpty()) {
			// Fall back to description if no parameters
			return step.description();
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
		return step.description();
	}
}
