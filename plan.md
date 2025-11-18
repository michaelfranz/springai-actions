# Incremental Plan for Scalable Action Execution

Each milestone delivers a usable framework plus an updated `DadJokeForTodayGeneratorTest`. Checkbox lists track the concrete tasks required to reach that milestone.

## Milestone 1 — Metadata Foundation (framework remains single-threaded)
- [ ] Define a serialisable `ActionMetadata` record (or builder-backed class) with identity, affinity IDs, mutability, resource scopes, context contracts, and execution policy slots (even if some stay empty initially).
- [ ] Add light-weight `ContextKey<T>` helpers so actions can declare the keys they read/write without string literals.
- [ ] Allow `@Action` to specify optional defaults (description, mutability hint, context key names) but keep affinity prefix optional.
- [ ] Update `ExecutableActionFactory` to instantiate `ActionMetadata` placeholders for current actions (auto-filling from annotation defaults) so existing behaviour continues unchanged.
- [ ] Adjust `DadJokeForTodayGeneratorTest` actions to compile against the new helpers (no behaviour change yet).

## Milestone 2 — Actions Return Metadata
- [ ] Change the contract so `@Action` methods must return `ActionMetadata`; introduce compile-time/static analysis guards to flag legacy signatures.
- [ ] Extend `ExecutableActionFactory` to capture the returned metadata, persist it inside `ExecutableAction`, and remove the old “store arbitrary return value in context” path.
- [ ] Provide convenience builders (`ActionMetadata.readOnly(affinityId)`, `.create(...)`, `.mutate(...)`) to keep declarations terse.
- [ ] Update all sample actions (including DadJoke scenario) to write business data via `ActionContext` and return metadata describing their scheduling needs.
- [ ] Backfill unit tests that ensure missing metadata (e.g., null affinity on mutating action) is rejected at build time.

## Milestone 3 — Context Contracts & Dependency Graph
- [ ] Teach `ActionMetadata` to carry `requiresContext` and `producesContext`, plus automatic registration when actions call `ctx.put(ContextKey, value)`.
- [ ] Enhance the planning phase to derive implicit dependencies from these contracts and persist them alongside plan steps.
- [ ] Validate that every required key has a producer (or is marked as pre-seeded) and fail fast on contradictions.
- [ ] Implement cycle detection (topological sort) within plan compilation; bubble clear diagnostics back to callers.
- [ ] Extend DadJoke scenario so the email action declares it consumes the joke text written by the generator action; verify the planner enforces the ordering even if executables are re-ordered manually.

## Milestone 4 — Affinity-Aware Parallel Executor
- [ ] Introduce a new `ConcurrentPlanExecutor` that reads affinity IDs and mutability to route actions into affinity queues while still honouring dependencies computed in Milestone 3.
- [ ] Add scheduling policies: read-only actions on the same affinity can co-exist, create actions run concurrently unless they share a parent affinity, mutating actions serialize per affinity.
- [ ] Provide configuration for thread pools and queue capacities; include sensible defaults so the feature works out of the box.
- [ ] Add metrics/logging hooks so developers can trace queueing, execution order, and contention.
- [ ] Upgrade DadJoke scenario to tag its actions with realistic affinity IDs (e.g., `email:recipient:michaelmannion@me.com`) and demonstrate concurrent execution when multiple jokes are planned.

## Milestone 5 — Resilience & Policy Enforcement
- [ ] Implement timeout and retry handling using the policy data already present in `ActionMetadata`.
- [ ] Honour `failureImpact` by introducing plan-level compensation/abort strategies.
- [ ] Surface actionable error reports when metadata contracts are violated at runtime (e.g., required context missing because an upstream action failed).
- [ ] Allow administrators to plug in rate-limit or quota policies that consume affinity IDs/resource scopes without changing action declarations.
- [ ] Expand DadJoke scenario (or add a new scenario) to showcase retries, idempotent actions, and failure handling under the concurrent executor.

Delivering each milestone keeps the framework operational, grows the scheduling sophistication incrementally, and ensures example scenarios remain runnable after every step.

