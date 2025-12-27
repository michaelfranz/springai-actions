package org.javai.springai.dsl.exec;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.dsl.act.ActionBinding;
import org.javai.springai.dsl.plan.PlanStatus;

/**
 * Default sequential executor for resolved plans. Executes {@link ResolvedStep.ActionStep}
 * via reflection and fails fast on the first error step or invocation failure.
 */
public class DefaultPlanExecutor implements PlanExecutor {

	public PlanExecutionResult execute(ResolvedPlan plan) {
		return execute(plan, new ActionContext());
	}

	public PlanExecutionResult execute(ResolvedPlan plan, ActionContext context) {
		if (plan == null) {
			return new PlanExecutionResult(false, List.of(
					new StepExecutionResult(null, false, null, null, "Plan is null")), context);
		}
		if (plan.status() != PlanStatus.READY) {
			throw new IllegalStateException("ResolvedPlan is not READY; status=" + plan.status());
		}

		List<StepExecutionResult> results = new ArrayList<>();
		boolean success = true;

		for (ResolvedStep step : plan.steps()) {
			if (step instanceof ResolvedStep.ErrorStep(String reason)) {
				success = false;
				results.add(new StepExecutionResult(null, false, null, null,
						"Plan contains error step: " + reason));
				break; // stop execution on error
			}

			if (step instanceof ResolvedStep.ActionStep actionStep) {
				StepExecutionResult result = executeActionStep(actionStep, context);
				results.add(result);
				if (!result.success()) {
					success = false;
					break; // fail fast on execution error
				}
			}
		}

		return new PlanExecutionResult(success, results, context);
	}

	private StepExecutionResult executeActionStep(ResolvedStep.ActionStep step, ActionContext context) {
		ActionBinding binding = step.binding();
		if (binding == null || binding.method() == null || binding.bean() == null) {
			return new StepExecutionResult(null, false, null, null, "Action binding is incomplete");
		}
		String actionId = binding.id();
		Method method = binding.method();
		Object target = binding.bean();
		try {
			method.setAccessible(true);
			var params = method.getParameters();
			Object[] invokeArgs = new Object[params.length];
			int argIdx = 0;
			for (int i = 0; i < params.length; i++) {
				if (params[i].getType() == ActionContext.class) {
					invokeArgs[i] = context;
				}
				else {
					if (argIdx >= step.arguments().size()) {
						return new StepExecutionResult(actionId, false, null, null,
								"Argument count mismatch for action " + actionId);
					}
					invokeArgs[i] = step.arguments().get(argIdx++).value();
				}
			}
			if (argIdx != step.arguments().size()) {
				return new StepExecutionResult(actionId, false, null, null,
						"Argument count mismatch for action " + actionId);
			}
			Object returnValue = method.invoke(target, invokeArgs);
			if (binding.contextKey() != null && !binding.contextKey().isBlank()) {
				context.put(binding.contextKey(), returnValue);
			}
			return new StepExecutionResult(actionId, true, returnValue, null, null);
		}
		catch (Exception ex) {
			return new StepExecutionResult(actionId, false, null, ex, "Execution failed: " + ex.getMessage());
		}
	}
}

