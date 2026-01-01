# Spring AI Actions

A framework for building robust, side-effect-free agentic applications with Spring AI. The LLM returns declarative plans; the framework validates and executes them safely.

## Key Features

- **Plan/Execution Separation**: LLM produces a structured plan; your code decides when and how to execute it
- **Type-Safe Actions**: Define actions with annotations; the framework handles parameter binding and validation
- **Conversation Support**: Built-in multi-turn conversation management with state tracking
- **Structured Error Handling**: Missing parameters and errors are surfaced as structured data, not exceptions
- **Clean API**: Core types (`Plan`, `PlanStep`, `Planner`, `PlanExecutor`) in one top-level package

## Quick Start

### 1. Define Actions

```java
public class ShoppingActions {

    @Action(description = "Add a product and quantity to the basket")
    public void addItem(
            @ActionParam(description = "Product name") String product,
            @ActionParam(description = "Quantity") int quantity) {
        // Implementation
    }

    @Action(description = "Checkout the basket and end the session")
    public void checkout() {
        // Implementation
    }
}
```

### 2. Configure the Planner

```java
// Create a persona for your assistant
PersonaSpec persona = PersonaSpec.builder()
        .name("shopping-assistant")
        .role("Helpful shopping assistant")
        .principles(List.of("Confirm quantities before adding items"))
        .build();

// Build the planner with Spring AI ChatClient
Planner planner = Planner.builder()
        .withChatClient(chatClient)
        .persona(persona)
        .actions(new ShoppingActions())
        .build();
```

### 3. Formulate and Execute Plans

```java
// Create a conversation manager for multi-turn support
ConversationManager manager = new ConversationManager(
        planner, 
        new InMemoryConversationStateStore()
);

// Process user input
ConversationTurnResult turn = manager.converse(
        "add 6 bottles of Coke Zero", 
        "session-123"
);

Plan plan = turn.plan();

// Check plan status before execution
switch (plan.status()) {
    case READY -> {
        PlanExecutionResult result = new DefaultPlanExecutor().execute(plan);
        // Handle success
    }
    case PENDING -> {
        // Ask user for missing information
        List<PlanStep.PendingParam> missing = plan.pendingParams();
    }
    case ERROR -> {
        // Handle error gracefully
        PlanStep.ErrorStep error = (PlanStep.ErrorStep) plan.planSteps().getFirst();
    }
}
```

## Core Concepts

### Plans and Steps

A `Plan` contains a list of `PlanStep` instances. Each step can be:

| Step Type | Description |
|-----------|-------------|
| `ActionStep` | Fully bound action ready for execution |
| `PendingActionStep` | Action missing required parameters |
| `ErrorStep` | Error encountered during planning |

### Plan Status

| Status | Meaning |
|--------|---------|
| `READY` | All steps are bound and executable |
| `PENDING` | One or more steps need additional information |
| `ERROR` | Plan contains errors and cannot be executed |

### Actions and Parameters

Actions are annotated methods that the LLM can include in plans:

```java
@Action(
    description = "Send email to customer",
    contextKey = "emailResult"  // Store return value in context
)
public String sendEmail(
        @ActionParam(description = "Recipient email") String to,
        @ActionParam(description = "Email body") String body,
        ActionContext context) {  // Optional: access execution context
    // ...
}
```

### ActionContext

Share data between action executions within a plan:

```java
@Action(description = "Fetch user profile")
public UserProfile getProfile(String userId, ActionContext context) {
    UserProfile profile = userService.find(userId);
    context.put("profile", profile);  // Available to subsequent actions
    return profile;
}

@Action(description = "Send personalized greeting")
public void greet(@FromContext("profile") UserProfile profile) {
    // profile injected from context
}
```

## Package Structure

```
org.javai.springai.actions/
├── Plan.java              ← Immutable plan with steps
├── PlanStep.java          ← ActionStep, PendingActionStep, ErrorStep
├── PlanStatus.java        ← READY, PENDING, ERROR
├── Planner.java           ← Fluent builder for LLM planning
├── PlanExecutor.java      ← Interface for plan execution
├── DefaultPlanExecutor.java
├── PlanExecutionResult.java
├── PersonaSpec.java       ← Define assistant personality
├── PromptContributor.java ← Extend system prompts
│
├── api/                   ← Annotations
│   ├── @Action
│   ├── @ActionParam
│   ├── @ContextKey
│   ├── @FromContext
│   └── ActionContext
│
├── conversation/          ← Multi-turn support
│   ├── ConversationManager.java
│   ├── ConversationState.java
│   └── ConversationTurnResult.java
│
├── sql/                   ← SQL query support
│   └── Query.java
│
└── internal/              ← Implementation details (not public API)
```

## Conversation Management

The framework tracks conversation state across turns, enabling:

- **Context accumulation**: Previously provided information is preserved
- **Pending parameter resolution**: Users can supply missing info in follow-up messages
- **Session isolation**: Each session ID maintains independent state

```java
// Turn 1: Missing quantity
ConversationTurnResult turn1 = manager.converse("add coke zero", sessionId);
// turn1.plan().status() == PENDING
// turn1.pendingParams() contains "quantity"

// Turn 2: User provides missing info
ConversationTurnResult turn2 = manager.converse("make it 6 bottles", sessionId);
// turn2.plan().status() == READY
// Framework merged context from previous turn
```

## Extending the System Prompt

Add custom context to the system prompt:

```java
Planner planner = Planner.builder()
        .withChatClient(chatClient)
        .addPromptContributor(context -> 
            Optional.of("Current date: " + LocalDate.now()))
        .actions(myActions)
        .build();
```

## SQL Support

For data warehouse applications, the framework includes SQL query support:

```java
@Action(description = "Execute a SQL query against the warehouse")
public ResultSet executeQuery(Query query) {
    return jdbcTemplate.query(query.sql(), ...);
}
```

## Build & Test

```bash
# Build and run tests
./gradlew test

# Run with Spring Boot
./gradlew bootRun
```

### Integration Tests

LLM integration tests require environment variables:

```bash
export OPENAI_API_KEY=your-key
export RUN_LLM_TESTS=true
./gradlew test
```

## Example Scenarios

The `src/test/java/org/javai/springai/scenarios/` directory contains complete examples:

| Scenario | Description |
|----------|-------------|
| `shopping/` | Shopping cart with add/remove/checkout actions |
| `data_warehouse/` | SQL queries against a star schema |
| `stats_app/` | Statistical analysis workflows |
| `protocol/` | Protocol-driven notebook generation |

## Design Principles

1. **Side-Effect Free Planning**: LLM calls never modify state directly
2. **Structured Responses**: Errors and missing info are data, not exceptions
3. **Type Safety**: Actions and parameters are strongly typed
4. **Composability**: Mix actions, tools, and custom prompt contributors
5. **Testability**: Plans can be inspected before execution

## Contributing

- Use AssertJ `assertThat` in tests
- Keep LLM interactions side-effect free
- Follow existing code style and patterns

## Possible future enhancements

### Data Warehouse Scenarios
- The star schema assumption is powerful but not universal. Consider how to handle other warehouse patterns (snowflake, data vault) in future iterations.
- The adaptive hybrid approach could benefit from ML-based prediction, but start with simple frequency counting.
- Consider how this scenario relates to the MCP (Model Context Protocol) for external database introspection.

## License

Licensed under the Apache License, Version 2.0 (Apache-2.0). See `LICENSE`.

### Attribution / credits

If you redistribute a product that includes this framework (source or binary), you must retain the attribution notices in `NOTICE` as required by Apache-2.0.
Suggested credit line:
> Includes Spring AI Actions by Mike Mannion.