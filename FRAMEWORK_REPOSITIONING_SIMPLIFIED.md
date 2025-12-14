# Framework Repositioning: Core Value = Compact, Safe S-Expression Specs

## The Cleaner Vision

Your framework's real USP is **not planning orchestration** (that's what Embabel does).

Your framework's USP is:

> **Safe, compact, hallucination-resistant object specs via s-expressions**
> 
> With built-in deserialization from LLM-generated s-expressions to typed Java objects.

---

## Simplified Value Proposition

### What Your Framework Provides

1. **Compact S-Expression Grammars** (vs verbose JSON Schema)
   - 5-6x smaller
   - Fewer hallucinations
   - Lower token usage
   - Explicit constraints (no invalid operations expressible)

2. **Safe Deserialization** from s-expressions to Java objects
   - Type-safe
   - Constraint validation
   - Error handling

3. **Two Usage Modes:**
   - **Standalone:** Execute sequential steps (Plan executor)
   - **Embedded:** Plug into any framework (Embabel, LangChain, etc.)

---

## Integration with Embabel (Simplified)

### No Plans Within Plans

Instead: Embabel calls a single action that internally uses your framework.

```java
@Service
public class ComplexDataAnalysis {
    
    @org.embabel.agent.api.Action(description = "Analyze data with complex query")
    public AnalysisResult analyze(
        Query query,        // ← Embabel receives this as s-expression
        RuleSet rules       // ← Embabel receives this as s-expression
    ) {
        // Embabel's s-expression deserialization hook:
        // 1. Recognizes Query has SXL definition
        // 2. Calls your EmbeddedResolver
        // 3. Passes typed Query object to this method
        
        QueryResult data = executeQuery(query);
        return applyRules(data, rules);
    }
}
```

### The Integration Points

**What Embabel does:**
1. Generates spec for `Query` parameter
2. LLM generates s-expression `(query ...)`
3. Deserializes to `Query` object
4. Invokes method

**What your framework provides:**
- Step 1: Your SXL grammar instead of JSON Schema (compact)
- Step 3: Your EmbeddedResolver (safe, type-correct)

### Code

```java
// Your framework provides this hook
@Bean
public SXLTypeSerializationStrategy queryStrategy() {
    return new SXLTypeSerializationStrategy(Query.class, sqlGrammar);
}

// Embabel's converter discovers it and uses it
// (via your custom Spring configuration)
@Bean
@Primary
public StructuredOutputConverter<?> sxlAwareConverter(
    SXLTypeRegistry registry,
    FilteringJacksonOutputConverter delegate
) {
    return new SXLTypeSerializationConverter(registry, delegate);
}
```

---

## Embabel Developer Experience

```java
@Service
public class CustomerAnalysis {
    
    @Autowired
    private PlanExecutor planExecutor;  // Your framework (optional)
    
    // Simple action with complex parameters
    @Action(description = "Analyze customer query")
    public QueryResults analyzeCustomer(
        Query customerQuery,      // ← s-expression param, typed via SXL
        Integer resultLimit       // ← primitive param
    ) {
        // Method receives properly typed objects
        // No manual deserialization needed
        
        // Option 1: Use your plan executor if needed
        Plan plan = generateSubPlan(customerQuery);
        PlanExecutionResult planResult = planExecutor.execute(plan);
        
        // Option 2: Execute inline
        QueryResults results = queryDatabase(customerQuery, resultLimit);
        return results;
    }
}
```

**Key point:** Embabel developer doesn't think about your framework's execution model. They just get better parameter specs.

---

## How to Position It

### Old Framing (confusing)
- "Planning DSL for LLM-generated plans"
- "Sequential step execution"
- "DAG-based ordering"

### New Framing (clear)
- "Compact, safe s-expression specs for complex types"
- "Built-in deserialization for LLM-generated s-expressions"
- "Works standalone or as plugin for orchestration frameworks"

### The Messaging

**For your framework alone:**
> Execute sequences of actions (steps) using compact, safe s-expression specifications. Get 5-6x smaller prompts and better hallucination resistance than JSON Schema.

**For Embabel integration:**
> Use our s-expression framework as the spec generator and deserializer for your Embabel actions. Reduce complexity and improve accuracy on intricate parameter types.

**For other frameworks:**
> Could work with LangChain, Spring Agent, or custom orchestration. Provides safe, compact specs for any framework that supports structured output.

---

## Updated File Structure

### What Changes

**DSL_EXEC_IMPLEMENTATION_PLAN.md** 
- Rename "Plan" terminology to "Action Step Sequence" or "Step Group"
- Emphasize: "For sequences of steps that belong together"
- De-emphasize: "This is NOT replacing orchestration frameworks"

**Create new document: SXL_FRAMEWORK_VALUE_PROPOSITION.md**
- Core value: compact, safe specs
- Two usage modes: standalone vs plugin
- Embabel integration as an example

### What Stays the Same

- Execution engine
- Progress tracking
- Event model
- ActionArgumentResolver
- All the good technical work

### What to Clarify

- Plans execute steps in sequence (not conditionally)
- Plans are "units of work" not "intelligent orchestration"
- The real value is in the S-expression specs, not the execution model
- Standalone use is valid; Embabel integration is optional

---

## Concrete Example: The Value

### JSON Schema Approach (Embabel's default)

```json
{
  "Query": {
    "type": "object",
    "properties": {
      "select": {
        "type": "array",
        "items": {"type": "string"},
        "description": "Columns to select"
      },
      "from": {
        "type": "string",
        "description": "Table name"
      },
      "where": {
        "type": "object",
        "properties": {
          "field": {"type": "string"},
          "operator": {"enum": ["=", "!=", ">", ">=", "<", "<="]},
          "value": {}
        },
        "description": "Filter condition"
      }
    }
  }
}
```

Tokens: 150-200+

### Your S-Expression Approach

```
Query spec:
(query
  (select [...])
  (from table-name)
  (where
    (field operator value)))

Optional grammar:
- select: List<String>, required
- from: String, required
- where: (field String) (operator keyword) (value Any), optional
```

Tokens: 30-50

**Result:** Better hallucination resistance, lower cost, same accuracy (or better)

---

## Implementation Note

The plan executor code you've already designed still makes perfect sense. It's not wasted work.

**Just reposition it:**
- **Old:** "Planning DSL execution model"
- **New:** "Sequential step execution for cohesive action units"

The code is the same. The framing changes.

---

## Three Tiers of Integration

### Tier 1: Standalone (Today)
Execute your own step sequences with your own execution model.

### Tier 2: Plugin to Framework (Soon)
Provide SXL specs and deserialization to any orchestration framework.

### Tier 3: Deep Integration (Future)
Embabel (or others) could make SXL the preferred format for complex types.

---

## Summary

**Stop trying to reconcile:**
- Your Plan model with Embabel's goal model
- Sequential execution with autonomous agents
- Planning with orchestration

**Start highlighting:**
- Compact, safe specs via s-expressions
- 5-6x reduction in prompt size
- Works with or without orchestration frameworks
- Embabel developers get better parameter handling

This is cleaner, more focused, and aligns your actual technical innovation (the DSL/s-expression approach) with what frameworks actually need.

Does this repositioning feel more authentic to what you've built?


