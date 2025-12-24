package org.javai.springai.dsl.exec;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.javai.springai.dsl.act.ActionBinding;
import org.javai.springai.dsl.act.ActionParameterDescriptor;
import org.javai.springai.dsl.act.ActionRegistry;
import org.javai.springai.dsl.plan.Plan;
import org.javai.springai.dsl.plan.PlanStep;

/**
 * Default resolver that maps Plan steps to ActionBindings and resolves arguments.
 * Resolution issues are captured as ResolvedStep.ErrorStep entries.
 */
public class DefaultPlanResolver implements PlanResolver {

	@Override
	public ResolvedPlan resolve(Plan plan, ActionRegistry registry) {
		if (plan == null || registry == null) {
			return new ResolvedPlan(List.of(new ResolvedStep.ErrorStep("Plan or registry is null")));
		}

		List<ResolvedStep> resolved = new ArrayList<>();
		for (PlanStep step : plan.planSteps()) {
			if (step instanceof PlanStep.ActionStep actionStep) {
				resolveActionStep(actionStep, registry, resolved);
			}
			else if (step instanceof PlanStep.ErrorStep(String assistantMessage)) {
				resolved.add(new ResolvedStep.ErrorStep(assistantMessage));
			}
			else if (step instanceof PlanStep.PendingActionStep pendingStep) {
				String actionId = pendingStep.actionId();
				PlanStep.PendingParam[] pendingParams = pendingStep.pendingParams();
				String message = "Pending parameter(s) prevent resolution";
				if (pendingParams != null && pendingParams.length > 0) {
					message += ": " + pendingParams[0].name();
				}
				resolved.add(new ResolvedStep.ErrorStep(message + " for action " + actionId));
			}
		}
		return new ResolvedPlan(resolved);
	}

	private void resolveActionStep(PlanStep.ActionStep actionStep, ActionRegistry registry,
			List<ResolvedStep> resolved) {
		String actionId = actionStep.actionId();
		ActionBinding binding = registry.getActionBinding(actionId);
		if (binding == null) {
			resolved.add(new ResolvedStep.ErrorStep("Unknown action id: " + actionId));
			return;
		}

		List<ActionParameterDescriptor> params = binding.parameters();
		Object[] args = actionStep.actionArguments();
		if (args == null) {
			args = new Object[0];
		}
		if (args.length != params.size()) {
			resolved.add(new ResolvedStep.ErrorStep(
					"Argument count mismatch for action " + actionId + ": expected " + params.size() + " got "
							+ args.length));
			return;
		}

		List<ResolvedArgument> resolvedArgs = new ArrayList<>();
		for (int i = 0; i < params.size(); i++) {
			ActionParameterDescriptor param = params.get(i);
			Object raw = args[i];
			Object converted = convert(raw, param, actionId, resolved);
			if (converted == ConversionFailure.INSTANCE) {
				return;
			}
			resolvedArgs.add(new ResolvedArgument(param.name(), converted, resolveType(param.typeName())));
		}
		resolved.add(new ResolvedStep.ActionStep(binding, resolvedArgs));
	}

	private Object convert(Object raw, ActionParameterDescriptor param, String actionId, List<ResolvedStep> resolved) {
		Class<?> targetType = resolveType(param.typeName());
		if (targetType == null) {
			resolved.add(new ResolvedStep.ErrorStep(
					"Unknown parameter type for action " + actionId + ": " + param.typeName()));
			return ConversionFailure.INSTANCE;
		}
		if (raw == null) {
			return null;
		}
		if (targetType.isInstance(raw)) {
			return raw;
		}

		if (targetType.isArray()) {
			return convertArray(raw, targetType.componentType(), actionId, param.name(), resolved);
		}

		if (Collection.class.isAssignableFrom(targetType)) {
			List<?> asList = toList(raw);
			if (asList == null) {
				resolved.add(new ResolvedStep.ErrorStep(
						"Expected a collection-compatible value for parameter " + param.name()));
				return ConversionFailure.INSTANCE;
			}
			return asList;
		}

		return convertScalar(raw, targetType, actionId, param.name(), resolved);
	}

	private Object convertArray(Object raw, Class<?> componentType, String actionId, String paramName,
			List<ResolvedStep> resolved) {
		Object[] elements = toObjectArray(raw);
		if (elements == null) {
			resolved.add(new ResolvedStep.ErrorStep(
					"Expected an array or collection value for parameter " + paramName));
			return ConversionFailure.INSTANCE;
		}

		Object array = Array.newInstance(componentType, elements.length);
		for (int i = 0; i < elements.length; i++) {
			Object converted = convertScalar(elements[i], componentType, actionId, paramName, resolved);
			if (converted == ConversionFailure.INSTANCE) {
				return ConversionFailure.INSTANCE;
			}
			Array.set(array, i, converted);
		}
		return array;
	}

	private Object convertScalar(Object raw, Class<?> targetType, String actionId, String paramName,
			List<ResolvedStep> resolved) {
		try {
			if (targetType == String.class) {
				return raw.toString();
			}
			if (targetType == Integer.class || targetType == int.class) {
				return Integer.valueOf(raw.toString());
			}
			if (targetType == Long.class || targetType == long.class) {
				return Long.valueOf(raw.toString());
			}
			if (targetType == Double.class || targetType == double.class) {
				return Double.valueOf(raw.toString());
			}
			if (targetType == Float.class || targetType == float.class) {
				return Float.valueOf(raw.toString());
			}
			if (targetType == Boolean.class || targetType == boolean.class) {
				return Boolean.valueOf(raw.toString());
			}
		}
		catch (Exception ex) {
			resolved.add(new ResolvedStep.ErrorStep(
					"Failed to convert parameter " + paramName + " to " + targetType.getSimpleName() + ": "
							+ ex.getMessage()));
			return ConversionFailure.INSTANCE;
		}

		if (targetType.isInstance(raw)) {
			return raw;
		}

		// Fallback: no conversion performed
		return raw;
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

	private enum ConversionFailure {
		INSTANCE
	}
}

