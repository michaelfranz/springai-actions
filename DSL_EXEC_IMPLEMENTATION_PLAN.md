# DSL Execution Framework Implementation Plan

## Overview

Implement a simple, focused execution engine in `org.javai.springai.dsl.exec` that:

1. **Takes as input:** A `Plan` (DSL-based) containing a sequence of `PlanStep` actions
2. **Uses:** `ActionRegistry` to resolve action definitions and invoke them
3. **Provides:** Sequential execution with optional fallback on error
4. **Returns:** Execution results with traceability

**Scope:** Simple sequence execution. Data flow between steps deferred to Phase 2.

---

## Part 1: Core Data Models

### 1.1 Plan Execution Context

```java
package org.javai.springai.dsl.exec;

/**
 * Holds state during plan execution.
 * Used to pass context between steps and accumulate results.
 */
public class PlanExecutionContext {
    
    // Execution state
    private final String planId;
    private final long startTime;
    private ExecutionState executionState = ExecutionState.PENDING;
    private Throwable executionError;
    
    // Results accumulation
    private final List<StepExecutionResult> stepResults = new ArrayList<>();
    
    // Optional: shared state for future data flow
    private final Map<String, Object> sharedData = new HashMap<>();
    
    public PlanExecutionContext(String planId) { ... }
    
    public void recordStepResult(StepExecutionResult result) { ... }
    public void setError(Throwable error) { ... }
    public List<StepExecutionResult> getStepResults() { ... }
    public ExecutionState getState() { ... }
    
    // For future data flow support
    public void put(String key, Object value) { ... }
    public <T> Optional<T> get(String key, Class<T> type) { ... }
}

public enum ExecutionState {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED_WITH_FALLBACK,
    FAILED
}
```

### 1.2 Step Execution Result

```java
package org.javai.springai.dsl.exec;

/**
 * Represents the outcome of executing a single plan step.
 */
public class StepExecutionResult {
    private final String stepId;
    private final String actionId;
    private final long startTime;
    private final long endTime;
    private final Object result;
    private final Throwable error;
    private final boolean wasSuccessful;
    private final boolean wasFallback; // true if this was a fallback action
    
    public StepExecutionResult(
        String stepId,
        String actionId,
        long startTime,
        long endTime,
        Object result,
        Throwable error,
        boolean wasFallback
    ) { ... }
    
    public long getDurationMillis() { ... }
    public boolean isSuccess() { ... }
    public boolean isFallback() { ... }
}
```

### 1.3 Plan Execution Result

```java
package org.javai.springai.dsl.exec;

/**
 * Final outcome of plan execution.
 */
public class PlanExecutionResult {
    private final PlanExecutionContext context;
    private final List<StepExecutionResult> allSteps;
    private final boolean succeeded;
    private final Throwable finalError;
    private final long totalDurationMillis;
    
    public PlanExecutionResult(PlanExecutionContext context) { ... }
    
    public boolean isSuccessful() { ... }
    public List<StepExecutionResult> getSteps() { ... }
    public List<StepExecutionResult> getFailedSteps() { ... }
    public List<StepExecutionResult> getFallbackSteps() { ... }
    public String getExecutionSummary() { ... }
}
```

---

## Part 2: Step Resolution and Invocation

### 2.1 Action Argument Resolver

Responsible for converting s-expression-based action arguments (from the Plan) into typed Java objects.

The Plan itself is an s-expression containing embedded DSL instances (also s-expressions) representing the parameters.
ActionArgumentResolver bridges the gap between these s-expressions and the typed Java parameters expected by action methods.

```java
package org.javai.springai.dsl.exec;

/**
 * Converts s-expression-based plan parameters into typed Java objects.
 * 
 * The Plan is an s-expression that contains embedded DSL instances (also s-expressions)
 * representing action parameters. This resolver deserializes them to match what the 
 * action method expects.
 * 
 * Handles:
 * - Embedded DSL s-expressions (via EmbeddedResolver) → DSL objects (Query, RuleSet, etc.)
 * - Primitive/standard types → Java objects (String, Integer, LocalDate, etc.)
 */
public class ActionArgumentResolver {
    
    private final ActionParameterSpec paramSpec;
    private final Object sxlArgument;  // s-expression from the Plan
    private final EmbeddedResolver embeddedResolver;
    
    /**
     * @param paramSpec The parameter specification (defines expected type)
     * @param sxlArgument The s-expression argument from the Plan
     * @param embeddedResolver Used to deserialize embedded DSL s-expressions
     */
    public ActionArgumentResolver(
        ActionParameterSpec paramSpec,
        Object sxlArgument,
        EmbeddedResolver embeddedResolver
    ) { ... }
    
    /**
     * Resolve the s-expression argument to the expected Java type.
     * 
     * @return Typed Java object ready to pass to the action method
     * @throws ActionArgumentResolutionException if conversion fails
     */
    public Object resolve() throws ActionArgumentResolutionException {
        // Case 1: Embedded DSL parameter
        // Example: (query (select [...]) (from table1)) → Query object
        if (paramSpec.dslId() != null && !paramSpec.dslId().isBlank()) {
            return resolveEmbeddedDsl();
        }
        
        // Case 2: Primitive/standard type parameter
        // Example: 100 → Integer, "name" → String, 2024-01-01 → LocalDate
        Class<?> expectedType = Class.forName(paramSpec.typeName());
        return resolvePrimitive(expectedType);
    }
    
    /**
     * Deserialize an embedded DSL s-expression to a typed object.
     * Example: (query (select [...]) (from table1)) → Query object
     */
    private Object resolveEmbeddedDsl() throws ActionArgumentResolutionException {
        return embeddedResolver.resolve(
            paramSpec.dslId(),
            sxlArgument,
            Class.forName(paramSpec.typeName())
        );
    }
    
    /**
     * Convert a primitive value to the target Java type.
     */
    private Object resolvePrimitive(Class<?> type) throws ActionArgumentResolutionException { ... }
}

public class ActionArgumentResolutionException extends Exception { ... }
```

### 2.2 Action Invoker

Responsible for invoking action methods and capturing results.

```java
package org.javai.springai.dsl.exec;

/**
 * Invokes an action method with resolved arguments.
 */
public class ActionInvoker {
    
    private final ActionDefinition actionDef;
    private final Object[] resolvedArguments;
    
    public ActionInvoker(
        ActionDefinition actionDef,
        Object[] resolvedArguments
    ) { ... }
    
    /**
     * Invoke the action and return the result.
     * @throws ActionInvocationException if invocation fails
     */
    public Object invoke() throws ActionInvocationException {
        try {
            return actionDef.method().invoke(
                actionDef.bean(),
                resolvedArguments
            );
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new ActionInvocationException(
                "Failed to invoke action: " + actionDef.id(),
                e
            );
        }
    }
}

public class ActionInvocationException extends Exception { ... }
```

---

## Part 3: Core Execution Engine

### 3.1 Step Executor

Executes a single plan step (main action + optional fallback).

```java
package org.javai.springai.dsl.exec;

/**
 * Executes a single plan step with optional fallback.
 */
public class StepExecutor {
    
    private final ActionRegistry actionRegistry;
    private final EmbeddedResolver embeddedResolver;
    private final PlanExecutionContext context;
    
    public StepExecutor(
        ActionRegistry actionRegistry,
        EmbeddedResolver embeddedResolver,
        PlanExecutionContext context
    ) { ... }
    
    /**
     * Execute a step with its optional fallback.
     * 
     * @param step The step to execute (either Action or Error)
     * @return The step execution result
     * @throws StepExecutionTerminationException if both primary and fallback fail
     */
    public StepExecutionResult execute(PlanStep step) {
        if (step instanceof PlanStep.Error error) {
            return executeErrorStep(error);
        }
        
        if (step instanceof PlanStep.Action action) {
            return executeActionStep(action);
        }
        
        throw new IllegalArgumentException("Unknown step type: " + step.getClass());
    }
    
    private StepExecutionResult executeActionStep(PlanStep.Action action) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. Resolve action definition
            ActionDefinition actionDef = actionRegistry.getActionDefinition(action.actionId());
            if (actionDef == null) {
                throw new ActionNotFoundException(
                    "Action not found: " + action.actionId()
                );
            }
            
            // 2. Resolve arguments
            Object[] resolvedArgs = resolveArguments(actionDef, action.actionArguments());
            
            // 3. Invoke action
            ActionInvoker invoker = new ActionInvoker(actionDef, resolvedArgs);
            Object result = invoker.invoke();
            
            // 4. Record success
            return new StepExecutionResult(
                generateStepId(),
                action.actionId(),
                startTime,
                System.currentTimeMillis(),
                result,
                null,
                false // not a fallback
            );
            
        } catch (Exception e) {
            // Action failed; try fallback
            return attemptFallback(action, startTime, e);
        }
    }
    
    private StepExecutionResult attemptFallback(
        PlanStep.Action action,
        long startTime,
        Exception primaryError
    ) {
        // For now: if primary fails and there's a fallback, execute the fallback
        // Fallback information would come from enhanced Plan model (Phase 2)
        
        // For this phase: just fail
        return new StepExecutionResult(
            generateStepId(),
            action.actionId(),
            startTime,
            System.currentTimeMillis(),
            null,
            primaryError,
            false
        );
    }
    
    private StepExecutionResult executeErrorStep(PlanStep.Error error) {
        // Error steps don't execute; they represent a state where the LLM
        // couldn't determine valid steps. Log and fail.
        return new StepExecutionResult(
            generateStepId(),
            "ERROR",
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            null,
            new PlanError(error.assistantMessage()),
            false
        );
    }
    
    private Object[] resolveArguments(
        ActionDefinition actionDef,
        Object[] sxlArguments
    ) throws ActionArgumentResolutionException {
        // Convert s-expression arguments from the Plan to typed Java objects
        if (sxlArguments == null || sxlArguments.length == 0) {
            return new Object[0];
        }
        
        List<ActionParameterSpec> paramSpecs = actionDef.actionParameterDefinitions();
        Object[] resolved = new Object[sxlArguments.length];
        
        // For each s-expression parameter from the Plan:
        for (int i = 0; i < sxlArguments.length; i++) {
            ActionParameterSpec paramSpec = paramSpecs.get(i);
            ActionArgumentResolver resolver = new ActionArgumentResolver(
                paramSpec,
                sxlArguments[i],  // s-expression from Plan
                embeddedResolver
            );
            // Resolves embedded DSLs to objects, primitives to their types
            resolved[i] = resolver.resolve();
        }
        
        return resolved;
    }
    
    private String generateStepId() {
        // Generate unique step ID (timestamp + index)
        return "step_" + System.currentTimeMillis() + "_" + context.getStepResults().size();
    }
}

public class ActionNotFoundException extends Exception { ... }
public class PlanError extends Exception { ... }
```

### 3.2 Plan Executor (Main Orchestrator)

```java
package org.javai.springai.dsl.exec;

/**
 * Main orchestrator for plan execution.
 * Executes a plan's steps sequentially and publishes progress events.
 */
public class PlanExecutor {
    
    private final ActionRegistry actionRegistry;
    private final EmbeddedResolver embeddedResolver;
    private final PlanExecutionEventPublisher eventPublisher;
    private static final Logger logger = LoggerFactory.getLogger(PlanExecutor.class);
    
    public PlanExecutor(
        ActionRegistry actionRegistry,
        EmbeddedResolver embeddedResolver,
        PlanExecutionEventPublisher eventPublisher
    ) {
        this.actionRegistry = actionRegistry;
        this.embeddedResolver = embeddedResolver;
        this.eventPublisher = eventPublisher;
    }
    
    /**
     * Register a listener for execution events.
     */
    public void addEventListener(PlanExecutionListener listener) {
        eventPublisher.subscribe(listener);
    }
    
    /**
     * Unregister a listener.
     */
    public void removeEventListener(PlanExecutionListener listener) {
        eventPublisher.unsubscribe(listener);
    }
    
    /**
     * Execute a plan and return the result.
     * 
     * Publishes events at each stage:
     * - PlanExecutionStartedEvent when execution begins
     * - StepExecutionStartedEvent before each step
     * - StepExecutionCompletedEvent or StepExecutionFailedEvent for each step
     * - PlanExecutionCompletedEvent when all steps complete
     * 
     * @param plan The plan to execute
     * @return Execution result with all step outcomes
     */
    public PlanExecutionResult execute(Plan plan) {
        String planId = generatePlanId();
        long planStartTime = System.currentTimeMillis();
        logger.info("Starting plan execution: {}", planId);
        
        PlanExecutionContext context = new PlanExecutionContext(planId);
        
        // Publish plan started event
        eventPublisher.publishPlanStarted(planId, plan.planSteps().size());
        
        try {
            context.executionState = ExecutionState.IN_PROGRESS;
            
            StepExecutor stepExecutor = new StepExecutor(
                actionRegistry,
                embeddedResolver,
                context,
                eventPublisher
            );
            
            // Execute each step in sequence
            for (int i = 0; i < plan.planSteps().size(); i++) {
                PlanStep step = plan.planSteps().get(i);
                
                StepExecutionResult stepResult = stepExecutor.execute(step, i);
                context.recordStepResult(stepResult);
                
                logger.debug("Step completed: {} -> {}", 
                    stepResult.getStepId(), 
                    stepResult.isSuccess() ? "SUCCESS" : "FAILED"
                );
                
                // If a step fails and has no fallback, stop execution
                if (!stepResult.isSuccess() && !stepResult.isFallback()) {
                    context.executionState = ExecutionState.FAILED;
                    context.setError(stepResult.error);
                    break;
                }
            }
            
            if (context.executionState == ExecutionState.IN_PROGRESS) {
                context.executionState = ExecutionState.COMPLETED;
            }
            
            logger.info("Plan execution completed: {} ({})", 
                planId, 
                context.executionState
            );
            
        } catch (Exception e) {
            logger.error("Plan execution failed unexpectedly", e);
            context.executionState = ExecutionState.FAILED;
            context.setError(e);
        }
        
        long planEndTime = System.currentTimeMillis();
        long totalDuration = planEndTime - planStartTime;
        
        PlanExecutionResult result = new PlanExecutionResult(context);
        
        // Publish plan completed event
        int successfulSteps = (int) context.getStepResults().stream()
            .filter(StepExecutionResult::isSuccess)
            .count();
        int failedSteps = context.getStepResults().size() - successfulSteps;
        
        eventPublisher.publishPlanCompleted(
            planId,
            context.executionState,
            successfulSteps,
            failedSteps,
            totalDuration,
            context.getError()
        );
        
        return result;
    }
    
    private String generatePlanId() {
        return "plan_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
```

---

## Part 3B: Progress Tracking & Callbacks

### 3B.1 Execution Events

```java
package org.javai.springai.dsl.exec.event;

/**
 * Base class for all plan execution events.
 */
public abstract sealed class PlanExecutionEvent permits
    PlanExecutionStartedEvent,
    StepExecutionStartedEvent,
    StepExecutionCompletedEvent,
    StepExecutionFailedEvent,
    PlanExecutionCompletedEvent {
    
    protected final String planId;
    protected final long timestamp;
    protected final int stepIndex;
    
    protected PlanExecutionEvent(String planId, int stepIndex) {
        this.planId = planId;
        this.timestamp = System.currentTimeMillis();
        this.stepIndex = stepIndex;
    }
    
    public String getPlanId() { return planId; }
    public long getTimestamp() { return timestamp; }
    public int getStepIndex() { return stepIndex; }
}

/**
 * Fired when plan execution begins.
 */
public final class PlanExecutionStartedEvent extends PlanExecutionEvent {
    private final int totalSteps;
    
    public PlanExecutionStartedEvent(String planId, int totalSteps) {
        super(planId, -1);
        this.totalSteps = totalSteps;
    }
    
    public int getTotalSteps() { return totalSteps; }
}

/**
 * Fired when a step begins execution.
 */
public final class StepExecutionStartedEvent extends PlanExecutionEvent {
    private final String stepId;
    private final String actionId;
    private final String actionDescription;
    
    public StepExecutionStartedEvent(
        String planId,
        int stepIndex,
        String stepId,
        String actionId,
        String actionDescription
    ) {
        super(planId, stepIndex);
        this.stepId = stepId;
        this.actionId = actionId;
        this.actionDescription = actionDescription;
    }
    
    public String getStepId() { return stepId; }
    public String getActionId() { return actionId; }
    public String getActionDescription() { return actionDescription; }
}

/**
 * Fired when a step completes successfully.
 */
public final class StepExecutionCompletedEvent extends PlanExecutionEvent {
    private final String stepId;
    private final String actionId;
    private final Object result;
    private final long durationMillis;
    
    public StepExecutionCompletedEvent(
        String planId,
        int stepIndex,
        String stepId,
        String actionId,
        Object result,
        long durationMillis
    ) {
        super(planId, stepIndex);
        this.stepId = stepId;
        this.actionId = actionId;
        this.result = result;
        this.durationMillis = durationMillis;
    }
    
    public String getStepId() { return stepId; }
    public String getActionId() { return actionId; }
    public Object getResult() { return result; }
    public long getDurationMillis() { return durationMillis; }
}

/**
 * Fired when a step fails.
 */
public final class StepExecutionFailedEvent extends PlanExecutionEvent {
    private final String stepId;
    private final String actionId;
    private final Throwable error;
    private final long durationMillis;
    private final boolean hasFallback;
    
    public StepExecutionFailedEvent(
        String planId,
        int stepIndex,
        String stepId,
        String actionId,
        Throwable error,
        long durationMillis,
        boolean hasFallback
    ) {
        super(planId, stepIndex);
        this.stepId = stepId;
        this.actionId = actionId;
        this.error = error;
        this.durationMillis = durationMillis;
        this.hasFallback = hasFallback;
    }
    
    public String getStepId() { return stepId; }
    public String getActionId() { return actionId; }
    public Throwable getError() { return error; }
    public long getDurationMillis() { return durationMillis; }
    public boolean hasFallback() { return hasFallback; }
}

/**
 * Fired when plan execution completes (success or failure).
 */
public final class PlanExecutionCompletedEvent extends PlanExecutionEvent {
    private final ExecutionState finalState;
    private final int successfulSteps;
    private final int failedSteps;
    private final long totalDurationMillis;
    private final Throwable error;
    
    public PlanExecutionCompletedEvent(
        String planId,
        ExecutionState finalState,
        int successfulSteps,
        int failedSteps,
        long totalDurationMillis,
        Throwable error
    ) {
        super(planId, -1);
        this.finalState = finalState;
        this.successfulSteps = successfulSteps;
        this.failedSteps = failedSteps;
        this.totalDurationMillis = totalDurationMillis;
        this.error = error;
    }
    
    public ExecutionState getFinalState() { return finalState; }
    public int getSuccessfulSteps() { return successfulSteps; }
    public int getFailedSteps() { return failedSteps; }
    public long getTotalDurationMillis() { return totalDurationMillis; }
    public Throwable getError() { return error; }
    public int getTotalSteps() { return successfulSteps + failedSteps; }
}
```

### 3B.2 Execution Listener Interface

```java
package org.javai.springai.dsl.exec.event;

/**
 * Listener for plan execution events.
 * Implementations can track progress, log, emit metrics, update UI, etc.
 */
public interface PlanExecutionListener {
    
    /**
     * Called when plan execution starts.
     */
    void onPlanExecutionStarted(PlanExecutionStartedEvent event);
    
    /**
     * Called when a step begins execution.
     */
    void onStepExecutionStarted(StepExecutionStartedEvent event);
    
    /**
     * Called when a step completes successfully.
     */
    void onStepExecutionCompleted(StepExecutionCompletedEvent event);
    
    /**
     * Called when a step fails.
     */
    void onStepExecutionFailed(StepExecutionFailedEvent event);
    
    /**
     * Called when plan execution completes.
     */
    void onPlanExecutionCompleted(PlanExecutionCompletedEvent event);
}

/**
 * Adapter providing default (no-op) implementations.
 * Extend this to listen to only events you care about.
 */
public class PlanExecutionAdapter implements PlanExecutionListener {
    @Override
    public void onPlanExecutionStarted(PlanExecutionStartedEvent event) {}
    
    @Override
    public void onStepExecutionStarted(StepExecutionStartedEvent event) {}
    
    @Override
    public void onStepExecutionCompleted(StepExecutionCompletedEvent event) {}
    
    @Override
    public void onStepExecutionFailed(StepExecutionFailedEvent event) {}
    
    @Override
    public void onPlanExecutionCompleted(PlanExecutionCompletedEvent event) {}
}
```

### 3B.3 Event Publisher

```java
package org.javai.springai.dsl.exec.event;

/**
 * Broadcasts execution events to registered listeners.
 * Handles multiple listeners and ensures non-blocking delivery.
 */
public class PlanExecutionEventPublisher {
    
    private final List<PlanExecutionListener> listeners = new CopyOnWriteArrayList<>();
    private static final Logger logger = LoggerFactory.getLogger(PlanExecutionEventPublisher.class);
    
    /**
     * Register a listener for execution events.
     */
    public void subscribe(PlanExecutionListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }
    
    /**
     * Unregister a listener.
     */
    public void unsubscribe(PlanExecutionListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Get count of registered listeners.
     */
    public int getListenerCount() {
        return listeners.size();
    }
    
    /**
     * Publish a plan execution started event.
     */
    public void publishPlanStarted(String planId, int totalSteps) {
        PlanExecutionStartedEvent event = new PlanExecutionStartedEvent(planId, totalSteps);
        notifyListeners(listener -> listener.onPlanExecutionStarted(event));
    }
    
    /**
     * Publish a step started event.
     */
    public void publishStepStarted(
        String planId,
        int stepIndex,
        String stepId,
        String actionId,
        String actionDescription
    ) {
        StepExecutionStartedEvent event = new StepExecutionStartedEvent(
            planId, stepIndex, stepId, actionId, actionDescription
        );
        notifyListeners(listener -> listener.onStepExecutionStarted(event));
    }
    
    /**
     * Publish a step completed event.
     */
    public void publishStepCompleted(
        String planId,
        int stepIndex,
        String stepId,
        String actionId,
        Object result,
        long durationMillis
    ) {
        StepExecutionCompletedEvent event = new StepExecutionCompletedEvent(
            planId, stepIndex, stepId, actionId, result, durationMillis
        );
        notifyListeners(listener -> listener.onStepExecutionCompleted(event));
    }
    
    /**
     * Publish a step failed event.
     */
    public void publishStepFailed(
        String planId,
        int stepIndex,
        String stepId,
        String actionId,
        Throwable error,
        long durationMillis,
        boolean hasFallback
    ) {
        StepExecutionFailedEvent event = new StepExecutionFailedEvent(
            planId, stepIndex, stepId, actionId, error, durationMillis, hasFallback
        );
        notifyListeners(listener -> listener.onStepExecutionFailed(event));
    }
    
    /**
     * Publish a plan completed event.
     */
    public void publishPlanCompleted(
        String planId,
        ExecutionState finalState,
        int successfulSteps,
        int failedSteps,
        long totalDurationMillis,
        Throwable error
    ) {
        PlanExecutionCompletedEvent event = new PlanExecutionCompletedEvent(
            planId, finalState, successfulSteps, failedSteps, totalDurationMillis, error
        );
        notifyListeners(listener -> listener.onPlanExecutionCompleted(event));
    }
    
    /**
     * Notify all listeners, catching and logging any exceptions.
     */
    private void notifyListeners(Consumer<PlanExecutionListener> notification) {
        for (PlanExecutionListener listener : listeners) {
            try {
                notification.accept(listener);
            } catch (Exception e) {
                logger.error("Error notifying listener: {}", listener.getClass().getName(), e);
                // Continue notifying other listeners
            }
        }
    }
}
```

### 3B.4 Example Listeners

```java
package org.javai.springai.dsl.exec.event;

/**
 * Example: Progress tracker for UI/console output.
 */
public class ProgressTrackingListener extends PlanExecutionAdapter {
    
    private final Logger logger = LoggerFactory.getLogger(ProgressTrackingListener.class);
    private int completedSteps = 0;
    private int totalSteps = 0;
    
    @Override
    public void onPlanExecutionStarted(PlanExecutionStartedEvent event) {
        this.totalSteps = event.getTotalSteps();
        this.completedSteps = 0;
        logger.info("[Plan {}] Starting execution with {} steps", 
            event.getPlanId(), totalSteps);
    }
    
    @Override
    public void onStepExecutionStarted(StepExecutionStartedEvent event) {
        logger.info("[Plan {} - Step {}/{}] Starting: {} ({})", 
            event.getPlanId(),
            event.getStepIndex() + 1,
            totalSteps,
            event.getActionId(),
            event.getActionDescription());
    }
    
    @Override
    public void onStepExecutionCompleted(StepExecutionCompletedEvent event) {
        completedSteps++;
        logger.info("[Plan {} - Step {}/{}] Completed in {}ms", 
            event.getPlanId(),
            event.getStepIndex() + 1,
            totalSteps,
            event.getDurationMillis());
        logProgress();
    }
    
    @Override
    public void onStepExecutionFailed(StepExecutionFailedEvent event) {
        logger.warn("[Plan {} - Step {}/{}] Failed: {}", 
            event.getPlanId(),
            event.getStepIndex() + 1,
            totalSteps,
            event.getError().getMessage(),
            event.getError());
        if (event.hasFallback()) {
            logger.info("  → Fallback available");
        }
    }
    
    @Override
    public void onPlanExecutionCompleted(PlanExecutionCompletedEvent event) {
        logger.info("[Plan {}] Completed in {}ms: {} successful, {} failed (state: {})", 
            event.getPlanId(),
            event.getTotalDurationMillis(),
            event.getSuccessfulSteps(),
            event.getFailedSteps(),
            event.getFinalState());
    }
    
    private void logProgress() {
        int percentage = (int) ((completedSteps * 100.0) / totalSteps);
        logger.debug("Progress: {}/{} ({}%)", completedSteps, totalSteps, percentage);
    }
}

/**
 * Example: Metrics collection.
 */
public class MetricsCollectingListener extends PlanExecutionAdapter {
    
    private final MeterRegistry meterRegistry;
    private long planStartTime;
    
    public MetricsCollectingListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    @Override
    public void onPlanExecutionStarted(PlanExecutionStartedEvent event) {
        this.planStartTime = event.getTimestamp();
    }
    
    @Override
    public void onStepExecutionCompleted(StepExecutionCompletedEvent event) {
        meterRegistry.timer(
            "plan.step.duration",
            "action", event.getActionId(),
            "status", "success"
        ).record(event.getDurationMillis(), TimeUnit.MILLISECONDS);
    }
    
    @Override
    public void onStepExecutionFailed(StepExecutionFailedEvent event) {
        meterRegistry.timer(
            "plan.step.duration",
            "action", event.getActionId(),
            "status", "failed"
        ).record(event.getDurationMillis(), TimeUnit.MILLISECONDS);
        
        meterRegistry.counter(
            "plan.step.failures",
            "action", event.getActionId()
        ).increment();
    }
    
    @Override
    public void onPlanExecutionCompleted(PlanExecutionCompletedEvent event) {
        meterRegistry.timer(
            "plan.execution.duration",
            "status", event.getFinalState().toString()
        ).record(event.getTotalDurationMillis(), TimeUnit.MILLISECONDS);
        
        meterRegistry.gauge("plan.completion.rate",
            (double) event.getSuccessfulSteps() / event.getTotalSteps());
    }
}

/**
 * Example: Callback-style listener.
 */
public class CallbackListener extends PlanExecutionAdapter {
    
    private final Consumer<String> onProgress;
    private final Consumer<String> onError;
    private final Consumer<String> onComplete;
    
    public CallbackListener(
        Consumer<String> onProgress,
        Consumer<String> onError,
        Consumer<String> onComplete
    ) {
        this.onProgress = onProgress;
        this.onError = onError;
        this.onComplete = onComplete;
    }
    
    @Override
    public void onStepExecutionCompleted(StepExecutionCompletedEvent event) {
        onProgress.accept(String.format(
            "Step %d completed: %s",
            event.getStepIndex() + 1,
            event.getActionId()
        ));
    }
    
    @Override
    public void onStepExecutionFailed(StepExecutionFailedEvent event) {
        onError.accept(String.format(
            "Step %d failed: %s - %s",
            event.getStepIndex() + 1,
            event.getActionId(),
            event.getError().getMessage()
        ));
    }
    
    @Override
    public void onPlanExecutionCompleted(PlanExecutionCompletedEvent event) {
        onComplete.accept(String.format(
            "Plan completed: %d successful, %d failed",
            event.getSuccessfulSteps(),
            event.getFailedSteps()
        ));
    }
}
```

---

## Part 4: Integration & Configuration

### 4.1 Spring Configuration

```java
package org.javai.springai.dsl.exec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.javai.springai.dsl.act.ActionRegistry;
import org.javai.springai.dsl.bind.EmbeddedResolver;
import org.javai.springai.dsl.exec.event.PlanExecutionEventPublisher;
import org.javai.springai.dsl.exec.event.ProgressTrackingListener;
import org.javai.springai.dsl.exec.event.MetricsCollectingListener;
import io.micrometer.core.instrument.MeterRegistry;

@Configuration
public class DslExecConfiguration {
    
    @Bean
    public PlanExecutionEventPublisher planExecutionEventPublisher() {
        return new PlanExecutionEventPublisher();
    }
    
    @Bean
    public PlanExecutor planExecutor(
        ActionRegistry actionRegistry,
        EmbeddedResolver embeddedResolver,
        PlanExecutionEventPublisher eventPublisher
    ) {
        PlanExecutor executor = new PlanExecutor(actionRegistry, embeddedResolver, eventPublisher);
        
        // Register default listeners
        executor.addEventListener(new ProgressTrackingListener());
        
        return executor;
    }
    
    @Bean
    public MetricsCollectingListener metricsListener(MeterRegistry meterRegistry) {
        return new MetricsCollectingListener(meterRegistry);
    }
}
```

### 4.2 Public API

```java
package org.javai.springai.dsl.exec;

/**
 * Public interface for DSL execution.
 * This is what applications use to execute plans.
 */
public class DslExecution {
    
    private final PlanExecutor planExecutor;
    
    public DslExecution(PlanExecutor planExecutor) {
        this.planExecutor = planExecutor;
    }
    
    /**
     * Execute a plan.
     */
    public PlanExecutionResult execute(Plan plan) {
        return planExecutor.execute(plan);
    }
}
```

---

## Part 5: Error Handling Strategy

### 5.1 Exception Hierarchy

```java
package org.javai.springai.dsl.exec;

public abstract class PlanExecutionException extends Exception {
    public PlanExecutionException(String message) { super(message); }
    public PlanExecutionException(String message, Throwable cause) { super(message, cause); }
}

public class ActionNotFoundException extends PlanExecutionException { ... }
public class ActionArgumentResolutionException extends PlanExecutionException { ... }
public class ActionInvocationException extends PlanExecutionException { ... }
public class StepExecutionTerminationException extends PlanExecutionException { ... }
public class InvalidPlanException extends PlanExecutionException { ... }
```

### 5.2 Error Recording

- Each step's error is captured in `StepExecutionResult`
- Context accumulates errors but continues (for Phase 2: data flow analysis)
- Final `PlanExecutionResult` includes all failures

---

## Part 6: Testing Strategy

### 6.1 Test Classes

```
src/test/java/org/javai/springai/dsl/exec/

├── PlanExecutorTest.java
│   ├── testSimpleSequentialExecution()
│   ├── testActionNotFound()
│   ├── testArgumentResolution()
│   ├── testMultipleSteps()
│   └── testErrorStep()
│
├── StepExecutorTest.java
│   ├── testActionStepExecution()
│   ├── testErrorStepExecution()
│   └── testArgumentPassing()
│
├── ActionArgumentResolverTest.java
│   ├── testPrimitiveTypeResolution()
│   ├── testEmbeddedDslResolution()
│   └── testTypeConversion()
│
└── IntegrationTest.java
    └── testEndToEndPlanExecution()
```

---

## Part 7: Implementation Phases

### Phase 1: Core (This Sprint)

**Goal:** Implement simple sequential execution

- [ ] Create `PlanExecutionContext`
- [ ] Create `StepExecutionResult` and `PlanExecutionResult`
- [ ] Create `ActionArgumentResolver`
- [ ] Create `ActionInvoker`
- [ ] Create `StepExecutor` (without fallback logic)
- [ ] Create `PlanExecutor`
- [ ] Create Spring configuration
- [ ] Write unit tests

**Estimated effort:** 3-4 days

**Dependencies:** 
- `ActionRegistry` ✓ (exists)
- `EmbeddedResolver` ✓ (exists)
- `Plan` / `PlanStep` ✓ (exist)

### Phase 2: Enhanced Model (Future)

**Goal:** Support data flow between steps

- [ ] Enhance `Plan` model to include data flow expressions
- [ ] Enhance `PlanStep` to reference input/output
- [ ] Implement context-aware argument resolution
- [ ] Add data flow validation

### Phase 3: Fallback Support (Future)

**Goal:** Support fallback actions on failure

- [ ] Enhance `Plan` model to include fallback specifications
- [ ] Implement fallback execution logic
- [ ] Add retry policies

---

## Part 8: API Usage Examples

### 8.1 Basic Execution

```java
// In application code
@Autowired
private PlanExecutor planExecutor;

public void executePlan() {
    // 1. Get plan from LLM (returns DSL Plan - an s-expression with embedded DSLs)
    // LLM generates something like:
    //   (plan
    //     :steps [
    //       (step :action "analyzeQuery"
    //         (query (select [col1]) (from table1))
    //         100)
    //       (step :action "saveResults"
    //         (rule-set (rule :id "r1" :expr (...))))
    //     ])
    Plan plan = llmClient.generatePlan(query);
    
    // 2. Execute plan (PlanExecutor converts s-expressions to typed Java objects)
    PlanExecutionResult result = planExecutor.execute(plan);
    
    // 3. Inspect results
    if (result.isSuccessful()) {
        List<StepExecutionResult> steps = result.getSteps();
        steps.forEach(step -> {
            System.out.println("Step: " + step.getStepId());
            System.out.println("Result: " + step.result);
            System.out.println("Duration: " + step.getDurationMillis() + "ms");
        });
    } else {
        System.out.println("Plan failed: " + result.getFinalError());
        result.getFailedSteps().forEach(step -> {
            System.out.println("Failed step: " + step.getStepId());
            System.out.println("Error: " + step.error.getMessage());
        });
    }
}
```

### 8.2 With Progress Tracking

```java
@Autowired
private PlanExecutor planExecutor;

public void executePlanWithProgress() {
    Plan plan = llmClient.generatePlan(query);
    
    // Register progress listener
    ProgressTrackingListener progressListener = new ProgressTrackingListener();
    planExecutor.addEventListener(progressListener);
    
    try {
        PlanExecutionResult result = planExecutor.execute(plan);
        // Progress is logged throughout execution
    } finally {
        planExecutor.removeEventListener(progressListener);
    }
}
```

### 8.3 With Callback-Style Listener

```java
public void executePlanWithCallbacks() {
    Plan plan = llmClient.generatePlan(query);
    
    // Create callback listener
    CallbackListener callbackListener = new CallbackListener(
        progress -> notifyUI("Progress: " + progress),
        error -> notifyUI("Error: " + error),
        complete -> {
            notifyUI("Completed: " + complete);
            updateResultsPanel();
        }
    );
    
    planExecutor.addEventListener(callbackListener);
    try {
        PlanExecutionResult result = planExecutor.execute(plan);
    } finally {
        planExecutor.removeEventListener(callbackListener);
    }
}
```

### 8.4 With Metrics Collection

```java
@Autowired
private PlanExecutor planExecutor;
@Autowired
private MeterRegistry meterRegistry;

@PostConstruct
public void setupMetrics() {
    MetricsCollectingListener metricsListener = 
        new MetricsCollectingListener(meterRegistry);
    planExecutor.addEventListener(metricsListener);
    // Metrics will be collected on every plan execution
}

public void executePlan() {
    Plan plan = llmClient.generatePlan(query);
    PlanExecutionResult result = planExecutor.execute(plan);
    // Metrics are automatically collected
}
```

### 8.5 Custom Progress Listener Example

```java
public class WebSocketProgressListener extends PlanExecutionAdapter {
    
    private final WebSocketService wsService;
    private final String sessionId;
    
    public WebSocketProgressListener(WebSocketService wsService, String sessionId) {
        this.wsService = wsService;
        this.sessionId = sessionId;
    }
    
    @Override
    public void onPlanExecutionStarted(PlanExecutionStartedEvent event) {
        wsService.sendMessage(sessionId, new Message(
            "PLAN_STARTED",
            event.getTotalSteps()
        ));
    }
    
    @Override
    public void onStepExecutionStarted(StepExecutionStartedEvent event) {
        wsService.sendMessage(sessionId, new Message(
            "STEP_STARTED",
            event.getActionId() + ": " + event.getActionDescription()
        ));
    }
    
    @Override
    public void onStepExecutionCompleted(StepExecutionCompletedEvent event) {
        wsService.sendMessage(sessionId, new Message(
            "STEP_COMPLETED",
            event.getActionId() + " (" + event.getDurationMillis() + "ms)"
        ));
    }
    
    @Override
    public void onStepExecutionFailed(StepExecutionFailedEvent event) {
        wsService.sendMessage(sessionId, new Message(
            "STEP_FAILED",
            event.getActionId() + ": " + event.getError().getMessage()
        ));
    }
    
    @Override
    public void onPlanExecutionCompleted(PlanExecutionCompletedEvent event) {
        wsService.sendMessage(sessionId, new Message(
            "PLAN_COMPLETED",
            "Success: " + event.getSuccessfulSteps() + 
            ", Failed: " + event.getFailedSteps() +
            " (Total: " + event.getTotalDurationMillis() + "ms)"
        ));
    }
}
```

---

## Summary of Classes to Implement

| Class | Purpose | Complexity |
|-------|---------|-----------|
| `PlanExecutionContext` | Holds execution state | Low |
| `StepExecutionResult` | Single step outcome | Low |
| `PlanExecutionResult` | Final plan outcome | Low |
| `ActionArgumentResolver` | Resolve SXL args to Java | Medium |
| `ActionInvoker` | Invoke action via reflection | Low |
| `StepExecutor` | Execute single step with events | Medium |
| `PlanExecutor` | Orchestrate plan steps with events | Medium |
| `DslExecution` | Public API | Low |
| `PlanExecutionEventPublisher` | Broadcast execution events | Medium |
| `PlanExecutionListener` | Event listener interface | Low |
| `PlanExecutionAdapter` | Default listener implementation | Low |
| Example listeners (3x) | Progress, metrics, callbacks | Medium |
| Exception classes | Error handling | Low |

**Total estimated effort:** 4-5 days

---

## Key Design Decisions

1. **Sequential execution only** - Data flow deferred to Phase 2
2. **No fallback in Phase 1** - Will add in Phase 3
3. **Accumulate results, don't abort early** - Allows diagnostics even after failure
4. **SXL argument objects passed directly** - No parsing, just resolution
5. **Spring-managed** - Integrates cleanly with action registry and resolvers
6. **Observable** - Logging and detailed result tracking for debugging
7. **Event-driven progress** - Supports real-time progress tracking via listener interface
8. **Non-blocking event delivery** - Listener exceptions don't affect execution
9. **Multiple listener support** - Can attach progress, metrics, logging, UI updates simultaneously
10. **Rich event model** - Each event contains relevant context for decision-making


