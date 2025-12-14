# Embabel Integration Architecture - Design Exploration

## The Core Issue

**Annotation Conflict:**
- Your framework uses `@Action` (from `org.javai.springai.actions.api`)
- Embabel uses its own `@Action` annotation
- Both are used to mark methods for invocation, but with different semantics

**Semantic Difference:**
- Your `@Action`: "This method is a step in a DSL-based Plan"
- Embabel `@Action`: "This method is a tool/action Embabel can invoke autonomously"

---

## Option 1: Separate Annotations (Cleanest)

Use `@PlanStep` for your framework to avoid conflicts.

### Definition

```java
package org.javai.springai.dsl.act;

/**
 * Marks a method as an executable step in a DSL-based Plan.
 * 
 * Used by the DSL execution framework to:
 * 1. Discover action implementations
 * 2. Build the action registry
 * 3. Generate s-expression specs for LLM
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PlanStep {
    
    /**
     * Human-readable description of this step
     */
    String description() default "";
    
    /**
     * Optional execution cost hint
     */
    int cost() default 1;
}
```

### Usage

```java
@Service
public class DataAnalysisActions {
    
    // Your framework's actions
    @PlanStep(description = "Execute a database query")
    public QueryResult executeQuery(Query query, Integer limit) {
        // ...
    }
    
    @PlanStep(description = "Process results with business logic")
    public ProcessedData process(QueryResult data, RuleSet rules) {
        // ...
    }
}

// Embabel's actions (if used)
@Service
public class AgentActions {
    
    @org.embabel.agent.api.Action(description = "Ask user a question")
    public String askUser(String question) {
        // ...
    }
}
```

### Benefits
- ✅ No annotation conflicts
- ✅ Clear semantic distinction
- ✅ Each framework owns its annotation
- ✅ Can use both in same application

---

## Option 2: Unified via Adapter (More Complex)

Adapt `@Action` to work with both frameworks through metadata.

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Action {
    
    String description() default "";
    
    // Enum to declare execution context
    ExecutionContext context() default ExecutionContext.PLAN_BASED;
}

public enum ExecutionContext {
    PLAN_BASED,     // Your framework's DSL Plan execution
    GOAL_ORIENTED   // Embabel's autonomous goal pursuit
}
```

**Pros:** Single annotation
**Cons:** Mixes concerns, harder to evolve independently

**Recommendation:** Avoid this. Separate annotations are cleaner.

---

## Plausible Integration Architectures

### Architecture A: Sequential Plans Within Goal Pursuit

```
┌─────────────────────────────────────────┐
│ Embabel (Goal-Oriented)                 │
│ Goal: "Analyze customer data"           │
└──────────────────┬──────────────────────┘
                   │ "I need to execute a plan"
                   ↓
┌──────────────────────────────────────────┐
│ Your DSL Execution Framework             │
│                                          │
│ Plan (s-expression from LLM):           │
│ (plan                                    │
│   (step :action "fetchData" ...)        │
│   (step :action "analyze" ...)          │
│   (step :action "generateReport" ...))  │
│                                          │
│ Execution:                               │
│ 1. PlanExecutor parses Plan             │
│ 2. For each step:                       │
│    - ActionArgumentResolver converts    │
│      s-expressions to objects           │
│    - Invoke @PlanStep method            │
│ 3. Return results to Embabel            │
└──────────────────┬──────────────────────┘
                   │ Results back to Embabel
                   ↓
        Embabel checks: "Did this achieve goal?"
```

**How Integration Works:**
1. Embabel is primary orchestrator (goal-based)
2. Embabel decides: "Execute a plan for this sub-goal"
3. Embabel calls `planExecutor.execute(plan)` 
4. Your framework executes the plan using @PlanStep methods
5. Results returned to Embabel for goal evaluation
6. Embabel either: satisfied → done, or unsatisfied → replan

**Code Example:**

```java
@Service
public class EmbabelPlanExecutionAction {
    
    @org.embabel.agent.api.Action(description = "Execute a DSL plan")
    public PlanExecutionResult executePlan(
        String planJson,  // LLM-generated plan as JSON
        LlmOperations llmOps
    ) {
        // Parse plan JSON to Plan object
        Plan plan = Plan.fromJson(planJson);
        
        // Execute using your framework
        return planExecutor.execute(plan);
    }
}

@Service
public class QueryActions {
    
    @PlanStep(description = "Execute query")
    public QueryResult query(Query query) { ... }
    
    @PlanStep(description = "Process results")
    public ProcessedData process(QueryResult data, RuleSet rules) { ... }
}
```

---

### Architecture B: Embabel Doesn't Use Your Plan Executor

Your framework and Embabel are completely separate layers.

```
┌──────────────────────────────────┐
│ Embabel Agent                    │
│ (Orchestrates via own @Action)   │
└──────────────────┬───────────────┘
                   │
         ┌─────────┴──────────┐
         │                    │
         ↓                    ↓
    @Action          Your SXL Serialization
    methods          Plugin for Embabel
    (handled by      ├─ Custom schema generator
    Embabel)         └─ Custom deserializer
                     
                     Used by Embabel when:
                     - Generating specs for complex types
                     - Deserializing LLM output
```

**How It Works:**
1. Your framework never invokes @PlanStep methods
2. Instead, you provide Embabel with hooks:
   - `SXLTypeSerializationStrategy` for complex types
   - `SXLObjectMapperProvider` for deserialization
3. Embabel uses these when it encounters types with DSL definitions
4. Your framework is available but optional

**Code Example:**

```java
@Configuration
public class SXLEmbabelIntegration {
    
    @Bean
    public SXLTypeRegistry sxlRegistry() {
        SXLTypeRegistry registry = new SXLTypeRegistry();
        registry.register(Query.class, sqlGrammar);
        registry.register(RuleSet.class, rulesGrammar);
        return registry;
    }
    
    @Bean
    public SXLTypeSerializationStrategy sxlStrategy(SXLTypeRegistry registry) {
        return new SXLTypeSerializationStrategy(registry);
    }
    
    // This plugs into Embabel's converter chain
    @Bean
    @Primary
    public ObjectMapperProvider embabelCustomMapper(SXLTypeRegistry registry) {
        return new HybridObjectMapperProvider(registry);
    }
}

// Embabel does its own orchestration
@Service
public class EmbabelActions {
    
    @org.embabel.agent.api.Action(description = "Execute query")
    public QueryResult executeQuery(Query query) {
        // Embabel calls this directly
        // Query parameter was deserialized using your SXL strategy
    }
}
```

---

### Architecture C: Hybrid (Both Patterns)

Some actions use your Plan execution, some use Embabel directly.

```
Embabel Agent
│
├─ Goal: "Analyze data" 
│  ├─ Calls @Action method (Embabel-direct)
│  │  └─ Your code executes immediately
│  │
│  └─ Calls `planExecutor.execute(plan)` (Your framework)
│     └─ Executes @PlanStep methods sequentially
│
└─ Evaluates results against goal
```

**Pros:** Maximum flexibility
**Cons:** More complex, two execution models to understand

---

## My Recommendation: Architecture A + Option 1

**Why this combination:**

1. **Semantic Clarity** - `@PlanStep` vs `@Action` is immediately clear
2. **No Conflicts** - Each framework owns its annotation
3. **Natural Layering** - Embabel orchestrates, your framework executes plans
4. **Extensible** - Either can evolve independently
5. **Testable** - Your framework works standalone

### Implementation Steps

**Step 1: Create @PlanStep annotation**
```java
package org.javai.springai.dsl.act;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PlanStep {
    String description() default "";
    int cost() default 1;
}
```

**Step 2: Update ActionRegistry** to scan for `@PlanStep`
```java
public class ActionRegistry {
    public void registerActions(Object bean) {
        for (Method method : bean.getClass().getMethods()) {
            PlanStep planStep = method.getAnnotation(PlanStep.class);  // ← Changed
            if (planStep == null) continue;
            // ... rest of registration
        }
    }
}
```

**Step 3: Create Embabel integration point**
```java
@Service
public class PlanExecutionBridge {
    
    @Autowired
    private PlanExecutor planExecutor;
    
    // This can be called by Embabel when needed
    public PlanExecutionResult executePlan(Plan plan) {
        return planExecutor.execute(plan);
    }
}
```

**Step 4: Document clearly** when each annotation is used

---

## Action Parameter Resolution in Embabel Context

When Embabel encounters a parameter like `Query`, here's how s-expression handling fits:

```
Embabel asking LLM to generate:
Query parameter

├─ Embabel's schema generator calls your SXL plugin
│  └─ Returns: SXL grammar (compact, safe)
│  
└─ LLM generates:
   (query (select [...]) (from table1))

Embabel receiving LLM output:
├─ Embabel's deserializer calls your SXL plugin
│  ├─ Recognizes: Query type has SXL handler
│  ├─ Calls: SXLDeserializer.deserialize()
│  └─ Returns: Query object
│
└─ Invokes: @Action method(Query query)
```

**Key Point:** The deserialization hook works for both:
- Embabel's @Action methods (when Embabel calls them)
- Your @PlanStep methods (when your framework calls them)

---

## Summary: Recommended Path Forward

| Component | Your Framework | Embabel | Integration |
|-----------|---|---|---|
| Annotation | `@PlanStep` | `@Action` | No conflict ✓ |
| Orchestration | Sequential plan execution | Goal-oriented | Embabel is primary |
| S-Expression Handling | Core to execution | Optional plugin | Your plugin hooks into Embabel |
| Use Cases | "Execute this plan" | "Achieve this goal" | "Achieve goal by executing plan" |

**For implementation:**
1. Rename your @Action to @PlanStep
2. Keep your execution framework standalone
3. Plan for Embabel integration as Phase 4 (after Phase 1-3 complete)
4. Document both use cases clearly

Does this architecture make sense? Would you like me to detail any specific aspect further?


