# Incremental Plan for Scalable Action Execution

Each milestone delivers a usable framework plus an updated `DadJokeForTodayGeneratorTest`. Checkbox lists track the concrete tasks required to reach that milestone.

## Milestone 1 — Metadata Foundation (framework remains single-threaded)
- [x] Define a serialisable `ActionMetadata` record (or builder-backed class) with identity, affinity IDs, mutability, resource scopes, context contracts, and execution policy slots (even if some stay empty initially).
- [x] Add light-weight `ContextKey<T>` helpers so actions can declare the keys they read/write without string literals.
- [x] Allow `@Action` to specify optional defaults (description, mutability hint, context key names) but keep affinity prefix optional.
- [x] Update `ExecutableActionFactory` to instantiate `ActionMetadata` placeholders for current actions (auto-filling from annotation defaults) so existing behaviour continues unchanged.
- [x] Adjust `DadJokeForTodayGeneratorTest` actions to compile against the new helpers (no behaviour change yet).

## Milestone 2 — Annotation-Derived Metadata Templates
- [x] Keep action methods returning domain results; metadata moves entirely into annotations plus helper annotations (e.g., `@RequiresContext`, `@ProducesContext`).
- [x] Extend `@Action` to support template attributes (e.g., `affinity = "customer:{customerId}"`, `produces = "emailText"`) with safe defaults to avoid annotation bloat.
- [x] Build a template expansion utility that validates referenced parameters/context keys at plan-compilation time; fail fast if placeholders cannot be resolved.
- [x] Update `ExecutableActionFactory` to derive `ActionMetadata` solely from annotations + templates and persist it within `ExecutableAction`.
- [x] Adjust DadJoke scenario annotations to use the new template syntax while preserving runtime behaviour.

## Milestone 3 — Context Contracts & Execution DAG
- [x] Build a standalone `ExecutionDAG` structure that consumes all metadata (affinities, mutability, context contracts) and materialises the executable dependency graph.
- [x] Provide thorough unit tests for `ExecutionDAG` (and ordering strategy) independent of actual execution, covering template expansion, dependency creation, and cycle detection.
- [x] Validate that every required context key is produced (or marked pre-existing) and fail fast on contradictions.
- [x] Add a new scenario to try out functionality added in Milestone 3.

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

