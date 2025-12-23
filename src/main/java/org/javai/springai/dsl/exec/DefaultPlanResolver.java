package org.javai.springai.dsl.exec;

import java.util.ArrayList;
import java.util.List;
import org.javai.springai.dsl.act.ActionBinding;
import org.javai.springai.dsl.act.ActionParameterDescriptor;
import org.javai.springai.dsl.act.ActionRegistry;
import org.javai.springai.dsl.plan.Plan;
import org.javai.springai.dsl.plan.PlanStep;

/**
 * Default resolver that maps Plan steps to ActionBindings and resolves arguments.
 */
public class DefaultPlanResolver implements PlanResolver {

	@Override
	public PlanResolutionResult resolve(Plan plan, ActionRegistry registry) {
		if (plan == null) {
			return PlanResolutionResult.failure(List.of(new PlanResolutionError(null, null, "Plan is null", null)));
		}
		if (registry == null) {
			return PlanResolutionResult.failure(List.of(new PlanResolutionError(null, null, "ActionRegistry is null", null)));
		}

		List<PlanResolutionError> errors = new ArrayList<>();
		List<ResolvedStep> resolved = new ArrayList<>();

		for (PlanStep step : plan.planSteps()) {
			switch (step) {
				case PlanStep.ErrorStep(String assistantMessage) -> resolved.add(new ResolvedStep.ErrorStep(assistantMessage));
				case PlanStep.PendingActionStep pendingStep -> {
					String actionId = pendingStep.actionId();
					PlanStep.PendingParam[] pendingParams = pendingStep.pendingParams();
					if (pendingParams != null && pendingParams.length > 0) {
						for (PlanStep.PendingParam pendingParam : pendingParams) {
							errors.add(new PlanResolutionError(
									actionId,
									pendingParam.name(),
									"Pending parameter: " + pendingParam.message(),
									null));
						}
					} else {
						errors.add(new PlanResolutionError(actionId, null,
								"Pending step cannot be resolved until required parameters are provided", null));
					}
				}
				case PlanStep.ActionStep actionStep -> {
					String actionId = actionStep.actionId();
					ActionBinding binding = registry.getActionBinding(actionId);
					if (binding == null) {
						errors.add(new PlanResolutionError(actionId, null, "Unknown action id", null));
						continue;
					}

					List<ActionParameterDescriptor> params = binding.parameters();
					Object[] args = actionStep.actionArguments();
					if (args == null) {
						args = new Object[0];
					}
					if (args.length != params.size()) {
						errors.add(new PlanResolutionError(actionId, null,
								"Argument count mismatch: expected " + params.size() + " got " + args.length, args));
						continue;
					}

					List<ResolvedArgument> resolvedArgs = new ArrayList<>();
					boolean stepFailed = false;
					for (int i = 0; i < params.size(); i++) {
						ActionParameterDescriptor param = params.get(i);
						Object raw = args[i];
						Object converted = convert(raw, param, errors, actionId);
						if (converted == ConversionFailure.INSTANCE) {
							stepFailed = true;
							break;
						}
						resolvedArgs.add(new ResolvedArgument(param.name(), converted, resolveType(param.typeName())));
					}
					if (!stepFailed) {
						resolved.add(new ResolvedStep.ActionStep(binding, resolvedArgs));
					}
				}
			}
		}

		if (!errors.isEmpty()) {
			return PlanResolutionResult.failure(errors);
		}
		return PlanResolutionResult.success(new ResolvedPlan(resolved));
	}

	private Object convert(Object raw, ActionParameterDescriptor param, List<PlanResolutionError> errors, String actionId) {
		Class<?> targetType = resolveType(param.typeName());
		if (targetType == null) {
			errors.add(new PlanResolutionError(actionId, param.name(), "Unknown parameter type: " + param.typeName(), raw));
			return ConversionFailure.INSTANCE;
		}
		if (raw == null) {
			return null;
		}
		if (targetType.isInstance(raw)) {
			return raw;
		}
		// Simple conversions
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
			errors.add(new PlanResolutionError(actionId, param.name(),
					"Failed to convert value to " + targetType.getSimpleName() + ": " + ex.getMessage(), raw));
			return ConversionFailure.INSTANCE;
		}

		if (targetType.isInstance(raw)) {
			return raw;
		}

		// Fallback: no conversion performed
		return raw;
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
		} catch (ClassNotFoundException e) {
			return null;
		}
	}

	private enum ConversionFailure {
		INSTANCE
	}
}

