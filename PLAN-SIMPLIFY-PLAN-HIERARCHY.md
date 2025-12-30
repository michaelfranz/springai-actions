# Refactoring Plan: Simplify Plan Hierarchy

**Branch:** `refactor/simplify-plan-hierarchy`  
**Created from:** `main`  
**Date:** 2024-12-30

## Objective

Simplify the framework's type hierarchy by:
1. Merging `ResolvedPlan` into `Plan` and `ResolvedStep` into `PlanStep`
2. Reorganizing packages to separate public API from internal implementation
3. Promoting developer-facing types to the top-level `actions` package
4. Moving internal machinery to an `internal` sub-package

## Pre-existing Issue

The `main` branch has compile errors: `Planner.java` references types from a deleted 
`org.javai.springai.actions.execution` package. This refactoring will resolve these 
as part of the broader cleanup.

---

## Current Package Structure

```
org.javai.springai.actions/
├── api/                    (6 files) - Annotations
├── bind/                   (10 files) - Action registration
├── conversation/           (6 files) - Multi-turn support
├── exec/                   (10 files) - Resolution & execution
├── instrument/             (12 files) - Telemetry
├── plan/                   (10 files) - Plan types & Planner
├── prompt/                 (8 files) - Prompt construction
└── sql/                    (3 files) - SQL support
```

### Key Problem: Triple-Layer Hierarchy

```
JsonPlan → Plan → ResolvedPlan
JsonPlanStep → PlanStep → ResolvedStep
```

This creates:
- Cognitive overhead for developers
- Duplication between parallel type hierarchies
- Confusion about which types to use

---

## Target Package Structure

```
org.javai.springai.actions/
│
├── api/                              ← Annotations (UNCHANGED)
│   ├── Action.java
│   ├── ActionParam.java
│   ├── ActionContext.java
│   ├── ContextKey.java
│   ├── FromContext.java
│   └── Mutability.java
│
├── Plan.java                         ← PROMOTED (merged with ResolvedPlan)
├── PlanStep.java                     ← PROMOTED (merged with ResolvedStep)
├── PlanStatus.java                   ← PROMOTED
├── Planner.java                      ← PROMOTED
├── PlanExecutor.java                 ← PROMOTED
├── DefaultPlanExecutor.java          ← PROMOTED
├── PlanExecutionResult.java          ← PROMOTED
├── PersonaSpec.java                  ← PROMOTED (from prompt/)
├── PromptContributor.java            ← PROMOTED (from prompt/)
│
├── conversation/                     ← Multi-turn support (PUBLIC)
│   ├── ConversationManager.java
│   ├── ConversationState.java
│   ├── ConversationStateStore.java
│   ├── ConversationTurnResult.java
│   └── InMemoryConversationStateStore.java
│
├── sql/                              ← SQL support (PUBLIC, optional)
│   ├── Query.java
│   ├── QueryFactory.java
│   └── QueryValidationException.java
│
└── internal/                         ← Implementation details (HIDDEN)
    │
    ├── parse/                        ← JSON parsing
    │   ├── RawPlan.java                   (renamed from JsonPlan)
    │   └── RawPlanStep.java               (renamed from JsonPlanStep)
    │
    ├── plan/                         ← Plan internals
    │   ├── PlanArgument.java              (renamed from ResolvedArgument)
    │   ├── PlanFormulationResult.java
    │   ├── PlannerOptions.java
    │   ├── PlanRunResult.java
    │   └── PromptPreview.java
    │
    ├── resolve/                      ← Resolution
    │   ├── PlanResolver.java
    │   └── DefaultPlanResolver.java
    │
    ├── bind/                         ← Action registration
    │   ├── ActionBinding.java
    │   ├── ActionDefinition.java
    │   ├── ActionDescriptor.java
    │   ├── ActionDescriptorFactory.java
    │   ├── ActionDescriptorFilter.java
    │   ├── ActionDescriptorJsonMapper.java
    │   ├── ActionEntry.java
    │   ├── ActionParameterDescriptor.java
    │   ├── ActionPromptContributor.java
    │   └── ActionRegistry.java
    │
    ├── exec/                         ← Execution details
    │   └── StepExecutionResult.java
    │
    ├── prompt/                       ← Prompt construction
    │   ├── ConversationPromptBuilder.java
    │   ├── PlanActionsContextContributor.java
    │   ├── SqlCatalog.java
    │   ├── SqlCatalogContextContributor.java
    │   ├── InMemorySqlCatalog.java
    │   └── SystemPromptBuilder.java
    │
    └── instrument/                   ← Telemetry (all files)
        └── ... (12 files)
```

---

## Type Transformations

### 1. Merge ResolvedPlan → Plan

**Before:**
- `Plan`: unbound, contains `List<PlanStep>` with `Object[] actionArguments`
- `ResolvedPlan`: bound, contains `List<ResolvedStep>` with `ActionBinding`

**After:**
- Single `Plan` with `List<PlanStep>` containing bound steps

### 2. Merge ResolvedStep → PlanStep

**Before:**
```java
// plan/PlanStep.java
public sealed interface PlanStep {
    record ActionStep(String assistantMessage, String actionId, Object[] actionArguments);
    record PendingActionStep(String assistantMessage, String actionId, PendingParam[] pendingParams, Map<String,Object> providedParams);
    record ErrorStep(String assistantMessage);
}

// exec/ResolvedStep.java
public sealed interface ResolvedStep {
    record ActionStep(ActionBinding binding, List<ResolvedArgument> arguments);
    record PendingActionStep(String assistantMessage, String actionId, PendingParam[] pendingParams, Map<String,Object> providedParams);
    record ErrorStep(String reason);
}
```

**After:**
```java
// actions/PlanStep.java
public sealed interface PlanStep {
    record ActionStep(ActionBinding binding, List<PlanArgument> arguments) {
        public String actionId() { return binding != null ? binding.id() : null; }
    }
    record PendingActionStep(String assistantMessage, String actionId, PendingParam[] pendingParams, Map<String,Object> providedParams);
    record ErrorStep(String reason);
    record PendingParam(String name, String message);
}
```

### 3. Rename JsonPlan/JsonPlanStep → RawPlan/RawPlanStep

Move to `internal.parse` package with new names that clarify purpose.

### 4. Rename ResolvedArgument → PlanArgument

Move to `internal.plan` package.

---

## Files to Delete

| File | Reason |
|------|--------|
| `exec/ResolvedPlan.java` | Merged into `Plan` |
| `exec/ResolvedStep.java` | Merged into `PlanStep` |
| `exec/ResolvedArgument.java` | Renamed to `PlanArgument`, moved |
| `exec/PlanVerifier.java` | Logic absorbed into `DefaultPlanResolver` |

---

## Files to Rename/Move

| From | To |
|------|-----|
| `plan/JsonPlan.java` | `internal/parse/RawPlan.java` |
| `plan/JsonPlanStep.java` | `internal/parse/RawPlanStep.java` |
| `plan/Plan.java` | `Plan.java` (top-level) |
| `plan/PlanStep.java` | `PlanStep.java` (top-level) |
| `plan/PlanStatus.java` | `PlanStatus.java` (top-level) |
| `plan/Planner.java` | `Planner.java` (top-level) |
| `plan/PlanFormulationResult.java` | `internal/plan/PlanFormulationResult.java` |
| `plan/PlannerOptions.java` | `internal/plan/PlannerOptions.java` |
| `plan/PlanRunResult.java` | `internal/plan/PlanRunResult.java` |
| `plan/PromptPreview.java` | `internal/plan/PromptPreview.java` |
| `exec/PlanExecutor.java` | `PlanExecutor.java` (top-level) |
| `exec/DefaultPlanExecutor.java` | `DefaultPlanExecutor.java` (top-level) |
| `exec/PlanExecutionResult.java` | `PlanExecutionResult.java` (top-level) |
| `exec/PlanResolver.java` | `internal/resolve/PlanResolver.java` |
| `exec/DefaultPlanResolver.java` | `internal/resolve/DefaultPlanResolver.java` |
| `exec/StepExecutionResult.java` | `internal/exec/StepExecutionResult.java` |
| `prompt/PersonaSpec.java` | `PersonaSpec.java` (top-level) |
| `prompt/PromptContributor.java` | `PromptContributor.java` (top-level) |
| `prompt/*` (remaining) | `internal/prompt/*` |
| `bind/*` | `internal/bind/*` |
| `instrument/*` | `internal/instrument/*` |

---

## Implementation Steps

### Phase 1: Fix Compile Errors ✅ COMPLETE
1. ~~Remove broken imports from `Planner.java` referencing deleted `execution` package~~
2. ~~Remove or stub the `toExecutablePlan()` and `toExecutableAction()` methods~~
3. ~~Verify build compiles~~
4. ~~Remove stale test files referencing deleted `execution` package~~

**Status:** `compileJava` ✅ | `compileTestJava` ✅

### Phase 2: Merge ResolvedPlan/ResolvedStep into Plan/PlanStep ✅ COMPLETE
1. ~~Update `PlanStep.ActionStep` to include `ActionBinding` and `List<PlanArgument>`~~
2. ~~Update `Plan` to incorporate `ResolvedPlan` functionality~~
3. ~~Update `DefaultPlanResolver` to return `Plan` instead of `ResolvedPlan`~~
4. ~~Update `DefaultPlanExecutor` to work with unified `Plan`/`PlanStep`~~
5. ~~Update `ConversationManager` and `ConversationTurnResult`~~
6. ~~Delete `ResolvedPlan.java`, `ResolvedStep.java`, `ResolvedArgument.java`, `PlanVerifier.java`~~
7. ~~Rename `ResolvedArgument` → `PlanArgument`~~
8. ~~Merge `PlanVerifier` logic into `DefaultPlanResolver`~~
9. ~~Update all tests to use new type structure~~
10. ~~Remove obsolete `JsonPlan.toPlan()` and `JsonPlanStep.toActionStep()` methods~~

**Status:** `compileJava` ✅ | `compileTestJava` ✅ | `test` ✅

### Phase 3: Create Package Structure ✅ COMPLETE
1. ~~Create `internal/` directory structure~~
2. ~~Create `internal/parse/`, `internal/plan/`, `internal/resolve/`, `internal/bind/`, `internal/exec/`, `internal/prompt/`, `internal/instrument/`~~
3. ~~Add `package-info.java` to each internal package with appropriate documentation~~

**Status:** `compileJava` ✅

### Phase 4: Move Internal Types
1. Move `bind/*` → `internal/bind/*`
2. Move `instrument/*` → `internal/instrument/*`
3. Move prompt internals → `internal/prompt/*`
4. Rename and move `JsonPlan` → `internal/parse/RawPlan`
5. Rename and move `JsonPlanStep` → `internal/parse/RawPlanStep`

### Phase 5: Promote Public Types
1. Move `Plan`, `PlanStep`, `PlanStatus` to top-level `actions/`
2. Move `Planner` to top-level `actions/`
3. Move `PlanExecutor`, `DefaultPlanExecutor`, `PlanExecutionResult` to top-level
4. Move `PersonaSpec`, `PromptContributor` to top-level
5. Move remaining `plan/` internals to `internal/plan/`

### Phase 6: Update Imports
1. Update all source files with new package locations
2. Update all test files with new package locations

### Phase 7: Verification
1. Run `./gradlew compileJava` - verify no errors
2. Run `./gradlew compileTestJava` - verify no errors
3. Run `./gradlew test` - verify all tests pass

---

## Developer Experience: Before vs After

### Before
```java
import org.javai.springai.actions.exec.DefaultPlanExecutor;
import org.javai.springai.actions.exec.DefaultPlanResolver;
import org.javai.springai.actions.exec.PlanExecutionResult;
import org.javai.springai.actions.exec.PlanResolver;
import org.javai.springai.actions.exec.ResolvedPlan;
import org.javai.springai.actions.exec.ResolvedStep;
import org.javai.springai.actions.plan.PlanStatus;
import org.javai.springai.actions.plan.PlanStep;
import org.javai.springai.actions.plan.Planner;
import org.javai.springai.actions.prompt.PersonaSpec;
```

### After
```java
import org.javai.springai.actions.Plan;
import org.javai.springai.actions.PlanStep;
import org.javai.springai.actions.PlanStatus;
import org.javai.springai.actions.Planner;
import org.javai.springai.actions.PlanExecutor;
import org.javai.springai.actions.DefaultPlanExecutor;
import org.javai.springai.actions.PlanExecutionResult;
import org.javai.springai.actions.PersonaSpec;
```

**Key improvements:**
- All core types in one package
- No `ResolvedPlan` vs `Plan` confusion
- Internal types clearly marked as `internal`
- `PlanResolver` hidden (used internally)

---

## Risk Assessment

| Risk | Mitigation |
|------|------------|
| Breaking existing code | Comprehensive test suite; phased approach |
| Missing import updates | IDE refactoring tools; grep verification |
| Circular dependencies | Careful package layering; interface extraction |
| Test failures | Run tests after each phase |

---

## Success Criteria

1. ✅ Build compiles without errors
2. ✅ All existing tests pass
3. ✅ No references to `ResolvedPlan` or `ResolvedStep` remain
4. ✅ Developer-facing types are in top-level `actions` package
5. ✅ Internal types are in `internal` sub-packages
6. ✅ Scenario tests demonstrate simplified import structure

