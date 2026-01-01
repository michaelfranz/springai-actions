package org.javai.springai.actions;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.internal.bind.ActionBinding;
import org.javai.springai.actions.internal.exec.StepExecutionResult;
import org.javai.springai.actions.internal.instrument.InvocationEmitter;
import org.javai.springai.actions.internal.instrument.InvocationEventType;
import org.javai.springai.actions.internal.instrument.InvocationKind;

/**
 * Default sequential executor for plans.
 *
 * <p>Executes {@link PlanStep.ActionStep} steps via reflection and fails fast
 * on the first error step or invocation failure.</p>
 *
 * <h2>Plan State Handling</h2>
 * <p>By default, attempting to execute a plan that is not in {@link PlanStatus#READY}
 * state throws an {@link IllegalStateException}. You can register handlers to
 * intercept these cases gracefully:</p>
 *
 * <pre>{@code
 * DefaultPlanExecutor executor = DefaultPlanExecutor.builder()
 *     .onPending((plan, context) -> {
 *         respondToUser("I need more info: " + plan.pendingParameterNames());
 *         return PlanExecutionResult.notExecuted(plan, context, "Awaiting input");
 *     })
 *     .onError((plan, context) -> {
 *         logger.warn("Plan error occurred");
 *         return PlanExecutionResult.notExecuted(plan, context, "Plan parsing failed");
 *     })
 *     .build();
 * }</pre>
 *
 * @see PendingPlanHandler
 * @see ErrorPlanHandler
 */
public class DefaultPlanExecutor implements PlanExecutor {

	private final InvocationEmitter emitter;
	private final PendingPlanHandler pendingHandler;
	private final ErrorPlanHandler errorHandler;

	/**
	 * Create an executor with default behavior (no handlers).
	 */
	public DefaultPlanExecutor() {
		this(null, null, null);
	}

	/**
	 * Create an executor with an invocation emitter for instrumentation.
	 *
	 * @param emitter the emitter for action invocation events
	 */
	public DefaultPlanExecutor(InvocationEmitter emitter) {
		this(emitter, null, null);
	}

	private DefaultPlanExecutor(InvocationEmitter emitter,
								PendingPlanHandler pendingHandler,
								ErrorPlanHandler errorHandler) {
		this.emitter = emitter;
		this.pendingHandler = pendingHandler;
		this.errorHandler = errorHandler;
	}

	private DefaultPlanExecutor(Builder builder) {
		this.emitter = builder.emitter;
		this.pendingHandler = builder.pendingHandler;
		this.errorHandler = builder.errorHandler;
	}

	/**
	 * Create a new builder for configuring the executor.
	 *
	 * @return a new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	@Override
	public PlanExecutionResult execute(Plan plan) {
		return execute(plan, new ActionContext());
	}

	public PlanExecutionResult execute(Plan plan, ActionContext context) {
		Objects.requireNonNull(plan);

		// Handle PENDING state
		if (plan.status() == PlanStatus.PENDING) {
			if (pendingHandler == null) {
				throw new IllegalStateException("Plan in PENDING state has no handler");
			}
			return pendingHandler.handle(plan, context);
		}

		// Handle ERROR state
		if (plan.status() == PlanStatus.ERROR) {
			if (errorHandler == null) {
				throw new IllegalStateException("Plan in ERROR state has no handler");
			}
			return errorHandler.handle(plan, context);
		}

		List<StepExecutionResult> results = new ArrayList<>();
		boolean success = true;

		for (PlanStep step : plan.planSteps()) {
			assert step instanceof PlanStep.ActionStep : "Unexpected step type: " + step;
			StepExecutionResult result = executeActionStep((PlanStep.ActionStep)step, context);
			results.add(result);
			if (!result.success()) {
				success = false;
				break; // fail fast on execution error
			}
		}

		return new PlanExecutionResult(success, results, context);
	}

	private StepExecutionResult executeActionStep(PlanStep.ActionStep step, ActionContext context) {
		ActionBinding binding = step.binding();
		if (binding == null) {
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
				} else {
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
			if (!binding.contextKey().isBlank()) {
				context.put(binding.contextKey(), returnValue);
			}
			if (emitter != null) {
				long durationMs = (System.nanoTime() - start) / 1_000_000;
				String contextKeyValue = binding.contextKey();
				emitter.emit(InvocationKind.ACTION, InvocationEventType.SUCCEEDED, actionId, invocationId, null, durationMs,
						Map.of("actionId", actionId, "contextKey", contextKeyValue));
			}
			return new StepExecutionResult(actionId, true, returnValue, null, null);
		} catch (Exception ex) {
			if (emitter != null) {
				long durationMs = (System.nanoTime() - start) / 1_000_000;
				String errorMessage = ex.getMessage() != null ? ex.getMessage() : "Unknown error";
				emitter.emit(InvocationKind.ACTION, InvocationEventType.FAILED, actionId, invocationId, null, durationMs,
						Map.of("actionId", actionId, "error", errorMessage));
			}
			return new StepExecutionResult(actionId, false, null, ex, "Execution failed: " + ex.getMessage());
		}
	}

	/**
	 * Builder for configuring a {@link DefaultPlanExecutor}.
	 *
	 * <p>Use the builder to register handlers for non-READY plan states:</p>
	 * <pre>{@code
	 * DefaultPlanExecutor executor = DefaultPlanExecutor.builder()
	 *     .withEmitter(myEmitter)
	 *     .onPending((plan, ctx) -> ...)
	 *     .onError((plan, ctx) -> ...)
	 *     .build();
	 * }</pre>
	 */
	public static final class Builder {
		private InvocationEmitter emitter;
		private PendingPlanHandler pendingHandler;
		private ErrorPlanHandler errorHandler;

		private Builder() {
		}

		/**
		 * Set the invocation emitter for instrumentation.
		 *
		 * @param emitter the emitter for action invocation events
		 * @return this builder
		 */
		public Builder withEmitter(InvocationEmitter emitter) {
			this.emitter = emitter;
			return this;
		}

		/**
		 * Register a handler for {@link PlanStatus#PENDING} plans.
		 *
		 * <p>When a plan with pending parameters is passed to {@code execute()},
		 * this handler will be invoked instead of throwing {@link IllegalStateException}.</p>
		 *
		 * @param handler the pending plan handler
		 * @return this builder
		 */
		public Builder onPending(PendingPlanHandler handler) {
			this.pendingHandler = handler;
			return this;
		}

		/**
		 * Register a handler for {@link PlanStatus#ERROR} plans.
		 *
		 * <p>When a plan with error steps is passed to {@code execute()},
		 * this handler will be invoked instead of throwing {@link IllegalStateException}.</p>
		 *
		 * @param handler the error plan handler
		 * @return this builder
		 */
		public Builder onError(ErrorPlanHandler handler) {
			this.errorHandler = handler;
			return this;
		}

		/**
		 * Build the configured executor.
		 *
		 * @return a new {@link DefaultPlanExecutor} instance
		 */
		public DefaultPlanExecutor build() {
			return new DefaultPlanExecutor(this);
		}
	}
}
