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
 *     .onNoAction((plan, context, message) -> {
 *         respondToUser(message);
 *         return PlanExecutionResult.notExecuted(plan, context, "No action identified");
 *     })
 *     .build();
 * }</pre>
 *
 * @see PendingPlanHandler
 * @see ErrorPlanHandler
 * @see NoActionPlanHandler
 */
public class DefaultPlanExecutor implements PlanExecutor {

	private static final String DEFAULT_NO_ACTION_MESSAGE = 
			"I couldn't identify an appropriate action for your request.";

	private final InvocationEmitter emitter;
	private final PendingPlanHandler pendingHandler;
	private final ErrorPlanHandler errorHandler;
	private final NoActionPlanHandler noActionHandler;

	/**
	 * Create an executor with default behavior (no handlers).
	 */
	public DefaultPlanExecutor() {
		this(null, null, null, null);
	}

	/**
	 * Create an executor with an invocation emitter for instrumentation.
	 *
	 * @param emitter the emitter for action invocation events
	 */
	public DefaultPlanExecutor(InvocationEmitter emitter) {
		this(emitter, null, null, null);
	}

	private DefaultPlanExecutor(InvocationEmitter emitter,
								PendingPlanHandler pendingHandler,
								ErrorPlanHandler errorHandler,
								NoActionPlanHandler noActionHandler) {
		this.emitter = emitter;
		this.pendingHandler = pendingHandler;
		this.errorHandler = errorHandler;
		this.noActionHandler = noActionHandler;
	}

	private DefaultPlanExecutor(Builder builder) {
		this.emitter = builder.emitter;
		this.pendingHandler = builder.pendingHandler;
		this.errorHandler = builder.errorHandler;
		this.noActionHandler = builder.noActionHandler;
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

		// Handle NO_ACTION state FIRST (empty steps or NoActionStep)
		// This must be checked before ERROR because Plan.status() returns ERROR for empty steps
		if (isNoActionPlan(plan)) {
			if (noActionHandler == null) {
				throw new IllegalStateException("Plan has no actions and no handler is registered");
			}
			String message = extractNoActionMessage(plan);
			return noActionHandler.handle(plan, context, message);
		}

		// Handle PENDING state
		if (plan.status() == PlanStatus.PENDING) {
			if (pendingHandler == null) {
				throw new IllegalStateException("Plan in PENDING state has no handler");
			}
			return pendingHandler.handle(plan, context);
		}

		// Handle ERROR state (contains ErrorStep, not just empty)
		if (plan.status() == PlanStatus.ERROR) {
			if (errorHandler == null) {
				throw new IllegalStateException("Plan in ERROR state has no handler");
			}
			return errorHandler.handle(plan, context);
		}

		List<StepExecutionResult> results = new ArrayList<>();
		boolean success = true;

		for (PlanStep step : plan.planSteps()) {
			if (step instanceof PlanStep.ActionStep actionStep) {
				StepExecutionResult result = executeActionStep(actionStep, context);
				results.add(result);
				if (!result.success()) {
					success = false;
					break; // fail fast on execution error
				}
			} else {
				// Unexpected step type - should have been caught by isNoActionPlan
				throw new IllegalStateException("Unexpected step type during execution: " + step.getClass().getSimpleName());
			}
		}

		return new PlanExecutionResult(success, results, context);
	}

	/**
	 * Check if this plan represents a "no action" situation.
	 * This is true when:
	 * - The plan has no steps at all (empty list)
	 * - The plan contains only a NoActionStep
	 */
	private boolean isNoActionPlan(Plan plan) {
		List<PlanStep> steps = plan.planSteps();
		if (steps == null || steps.isEmpty()) {
			return true;
		}
		// Single NoActionStep is also a no-action plan
		if (steps.size() == 1 && steps.getFirst() instanceof PlanStep.NoActionStep) {
			return true;
		}
		return false;
	}

	/**
	 * Extract the no-action message from the plan.
	 * Returns the message from NoActionStep if present, otherwise a default message.
	 */
	private String extractNoActionMessage(Plan plan) {
		if (plan.planSteps() != null) {
			for (PlanStep step : plan.planSteps()) {
				if (step instanceof PlanStep.NoActionStep noActionStep) {
					return noActionStep.message();
				}
			}
		}
		return DEFAULT_NO_ACTION_MESSAGE;
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
	 *     .onNoAction((plan, ctx, msg) -> ...)
	 *     .build();
	 * }</pre>
	 */
	public static final class Builder {
		private InvocationEmitter emitter;
		private PendingPlanHandler pendingHandler;
		private ErrorPlanHandler errorHandler;
		private NoActionPlanHandler noActionHandler;

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
		 * Register a handler for plans with no executable actions.
		 *
		 * <p>When a plan has no steps or contains only a {@link PlanStep.NoActionStep},
		 * this handler will be invoked instead of throwing {@link IllegalStateException}.</p>
		 *
		 * <p>This occurs when the assistant could not identify an appropriate action
		 * for the user's request and provides an explanation message.</p>
		 *
		 * @param handler the no-action plan handler
		 * @return this builder
		 */
		public Builder onNoAction(NoActionPlanHandler handler) {
			this.noActionHandler = handler;
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
