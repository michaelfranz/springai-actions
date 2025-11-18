# Scalability Requirements for Plan Execution

The DSL already separates planning from acting. To scale execution we need structured metadata that rides along every action (`@Action` → `ActionDefinition` → `PlanStep` → `ExecutableAction`). The metadata becomes the contract between declarative actions and a scheduler that performs dependency analysis, affinity-aware queueing, and safe parallelism.

## ActionMetadata Record

All actions return an immutable `ActionMetadata` instance instead of arbitrary values. The record is serialisable so queued actions can survive restarts, and it is the only channel through which the executor learns about scheduling needs. Core fields (grouped for clarity) are:

- **Identity & dependencies**
  - `stepId` plus `actionName` for logging/retries.
  - Explicit `dependsOn` edges for ordering rules external to context flow.

- **Affinity & resource access**
  - `affinityIds`: fully resolved, self-describing strings such as `tenant:acme:account:42`. Left-to-right tokens move from broad category to specific record (never to field level).
  - `mutability`: enum with at least `READ_ONLY`, `CREATE`, `MUTATE`.
    - `READ_ONLY` can be parallelised freely unless resource scopes overlap with mutators.
    - `CREATE` describes new-resource writes; still serialized when they share a parent affinity.
    - `MUTATE` covers updates/deletes and requires strict ordering per affinity.
  - `resourceScopes`: declares which logical resources are read vs written (context keys, domain aggregates, external systems) so conflicts across affinities can be detected.

- **Context contract**
  - `requiresContext`: keys that must exist in `ActionContext` before execution.
  - `producesContext`: keys guaranteed to be written before completion.
  - These declarations let the scheduler derive implicit dependencies, enforce sequencing, and reject circular graphs (topological sort detects cycles).

- **Execution policy**
  - `cost`/`priority` hints for work-stealing decisions.
  - `timeout`, `retryPolicy`, and `idempotent` flag to manage long-running or re-tried tasks.
  - `failureImpact` describing whether failure aborts the plan or is compensatable.

- **Extensions**
  - Optional typed attachments for future hints without bloating the base record (rate-limit buckets remain executor/admin concerns).

Actions still interact with `ActionContext` exactly as before for data exchange; the metadata simply documents those reads/writes so the executor can schedule them safely.

## Return Value Contract

- Every method annotated with `@Action` returns `ActionMetadata`.
- The method body performs its domain work, writes results to `ActionContext`, and then constructs metadata via fluent helpers (`ActionMetadata.builder()...build()`).
- Resolved affinity IDs originate from runtime data (e.g., action arguments); annotations supply only lightweight defaults.
- The metadata returned by the action is authoritative; the executor no longer inspects arbitrary return values.

## Construction Ergonomics

Keep the developer experience close to the existing DSL:

- `@Action` retains simple attributes (`description`, optional defaults for mutability or context keys).
- Provide convenience factories such as `ActionMetadata.readOnly(String affinityId)` or `ActionMetadata.create(String affinityId, ResourceScope scope)` to cover the common cases with one line.
- Offer typed `ContextKey<T>` handles so actions call `ctx.put(profileKey, profile)` while the metadata builder references the same handle (`produces(profileKey)`), eliminating stringly-typed mistakes.
- Support a fluent builder for advanced scenarios (multiple affinities, custom retry policy, explicit dependency overrides).

## Validation

Metadata construction must fail fast when contradictions appear:

- Read-only actions cannot list write scopes or `producesContext`.
- Mutating actions must specify the resources they change.
- Affinity IDs must not be empty when mutability ≠ read-only.
- Required context keys must either be produced by an upstream action or flagged as pre-existing inputs; missing producers cause validation errors.
- Dependency cycles detected during metadata assembly or plan compilation are rejected before execution.

Centralising these checks in the metadata factory prevents malformed plans from reaching executors.

## Executor Consumption

Given enriched metadata, a scalable `PlanExecutor` can:

- Build a dependency graph by combining explicit `dependsOn` edges with context contracts (`requiresContext` ← `producesContext`).
- Validate the graph for cycles and missing producers before any action runs.
- Determine eligible actions per topological layer, then apply affinity and mutability rules to decide which can execute concurrently.
- Route actions to affinity queues keyed by the leftmost tokens (e.g., tenant, account) so sequential guarantees hold where required, while unrelated affinities run in parallel.
- Respect cost/priority hints when multiple queues compete for worker threads.
- Apply timeout/retry policies and idempotency hints consistently during failure handling.

By making `ActionMetadata` the linchpin, we preserve the planner/actor separation while enabling high-throughput, coordinated execution without manual sequencing.

