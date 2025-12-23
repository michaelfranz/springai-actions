package org.javai.springai.dsl.exec;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.javai.springai.dsl.act.ActionBinding;

/**
 * Default sequential executor for resolved plans. Executes {@link ResolvedStep.ActionStep}
 * via reflection and fails fast on the first error step or invocation failure.
 */
public class DefaultPlanExecutor implements PlanExecutor {

	@Override
	public PlanExecutionResult execute(ResolvedPlan plan) {
		if (plan == null) {
			return new PlanExecutionResult(false, List.of(
					new StepExecutionResult(null, false, null, null, "Plan is null")));
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
				StepExecutionResult result = executeActionStep(actionStep);
				results.add(result);
				if (!result.success()) {
					success = false;
					break; // fail fast on execution error
				}
			}
		}

		return new PlanExecutionResult(success, results);
	}

	private StepExecutionResult executeActionStep(ResolvedStep.ActionStep step) {
		ActionBinding binding = step.binding();
		if (binding == null || binding.method() == null || binding.bean() == null) {
			return new StepExecutionResult(null, false, null, null, "Action binding is incomplete");
		}
		String actionId = binding.id();
		Method method = binding.method();
		Object target = binding.bean();
		try {
			method.setAccessible(true);
			Object returnValue = method.invoke(target, step.arguments().stream().map(ResolvedArgument::value).toArray());
			return new StepExecutionResult(actionId, true, returnValue, null, null);
		}
		catch (Exception ex) {
			return new StepExecutionResult(actionId, false, null, ex, "Execution failed: " + ex.getMessage());
		}
	}
}

