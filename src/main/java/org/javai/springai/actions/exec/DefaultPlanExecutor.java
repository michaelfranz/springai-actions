package org.javai.springai.actions.exec;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.internal.bind.ActionBinding;
import org.javai.springai.actions.internal.exec.StepExecutionResult;
import org.javai.springai.actions.internal.instrument.InvocationEmitter;
import org.javai.springai.actions.internal.instrument.InvocationEventType;
import org.javai.springai.actions.internal.instrument.InvocationKind;
import org.javai.springai.actions.plan.Plan;
import org.javai.springai.actions.plan.PlanStatus;
import org.javai.springai.actions.plan.PlanStep;

/**
 * Default sequential executor for plans.
 * <p>
 * Executes {@link PlanStep.ActionStep} steps via reflection and fails fast
 * on the first error step or invocation failure.
 */
public class DefaultPlanExecutor implements PlanExecutor {

	private final InvocationEmitter emitter;

	public DefaultPlanExecutor() {
		this(null);
	}

	public DefaultPlanExecutor(InvocationEmitter emitter) {
		this.emitter = emitter;
	}

	@Override
	public PlanExecutionResult execute(Plan plan) {
		return execute(plan, new ActionContext());
	}

	public PlanExecutionResult execute(Plan plan, ActionContext context) {
		if (plan == null) {
			return new PlanExecutionResult(false, List.of(
					new StepExecutionResult(null, false, null, null, "Plan is null")), context);
		}
		if (plan.status() != PlanStatus.READY) {
			throw new IllegalStateException("Plan is not READY; status=" + plan.status());
		}

		List<StepExecutionResult> results = new ArrayList<>();
		boolean success = true;

		for (PlanStep step : plan.planSteps()) {
			if (step instanceof PlanStep.ErrorStep errorStep) {
				success = false;
				results.add(new StepExecutionResult(null, false, null, null,
						"Plan contains error step: " + errorStep.reason()));
				break; // stop execution on error
			}

			if (step instanceof PlanStep.ActionStep actionStep) {
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

	private StepExecutionResult executeActionStep(PlanStep.ActionStep step, ActionContext context) {
		ActionBinding binding = step.binding();
		if (binding == null || binding.method() == null || binding.bean() == null) {
			return new StepExecutionResult(null, false, null, null, "Action binding is incomplete");
		}
		String actionId = binding.id();
		Method method = binding.method();
		Object target = binding.bean();
		String invocationId = emitter != null ? emitter.nextInvocationId() : null;
		long start = System.nanoTime();
		try {
			if (emitter != null) {
				emitter.emit(InvocationKind.ACTION, InvocationEventType.REQUESTED, actionId, invocationId, null, null,
						Map.of("actionId", actionId));
				emitter.emit(InvocationKind.ACTION, InvocationEventType.STARTED, actionId, invocationId, null, null,
						Map.of("actionId", actionId));
			}
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
			if (emitter != null) {
				long durationMs = (System.nanoTime() - start) / 1_000_000;
				String contextKeyValue = binding.contextKey() != null ? binding.contextKey() : "";
				emitter.emit(InvocationKind.ACTION, InvocationEventType.SUCCEEDED, actionId, invocationId, null, durationMs,
						Map.of("actionId", actionId, "contextKey", contextKeyValue));
			}
			return new StepExecutionResult(actionId, true, returnValue, null, null);
		}
		catch (Exception ex) {
			if (emitter != null) {
				long durationMs = (System.nanoTime() - start) / 1_000_000;
				String errorMessage = ex.getMessage() != null ? ex.getMessage() : "Unknown error";
				emitter.emit(InvocationKind.ACTION, InvocationEventType.FAILED, actionId, invocationId, null, durationMs,
						Map.of("actionId", actionId, "error", errorMessage));
			}
			return new StepExecutionResult(actionId, false, null, ex, "Execution failed: " + ex.getMessage());
		}
	}
}
