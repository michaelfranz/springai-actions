# Data Warehouse Scenario Enhancement Plan

## Scenario Vision

The **data_warehouse** scenario simulates an AI assistant helping end users formulate queries against a data warehouse. The key insight is that data warehouses, when structured as a **star schema** (fact tables surrounded by dimension tables), exhibit highly regular patterns that can provide powerful guidance to an LLM.

### Core Goal

Transform natural language query requests into **executable SQL** that, in the best case, can be run against the database without additional user modification.

### Key Assumptions

- The data warehouse uses a **star schema** with clearly identified fact and dimension tables
- Foreign key relationships between facts and dimensions are well-defined
- Measures (aggregatable numeric columns) and attributes (descriptive columns) are semantically tagged
- The assistant has access to metadata about the schema structure

---

## Implementation Phases Overview

| Phase | Name | Status | Completed |
|-------|------|--------|-----------|
| 1 | Documentation & Foundation | ✅ Complete | 2024-12-30 |
| 2 | Static Approach Hardening | ✅ Complete | 2024-12-30 |
| 3 | Tool-Based Dynamic Metadata | ✅ Complete | 2024-12-31 |
| 4 | Adaptive Hybrid Approach | ✅ Complete | 2024-12-31 |
| 5 | Multi-Turn Context Tracking | 🔄 In Progress | — |
| 6 | SQL Module Positioning | 🔲 Deferred | — |

**Status Legend**: 🔲 Not Started | 🔄 In Progress | ✅ Complete | ⏸️ Deferred

---

## Phase 1: Documentation & Foundation

**Goal**: Establish clear documentation and baseline understanding of the scenario.

### Tasks

| ID | Task | Status | Completed |
|----|------|--------|-----------|
| 1.1 | Create `scenarios/data_warehouse/README.md` explaining scenario purpose | ✅ | 2024-12-30 |
| 1.2 | Document the two query approaches (SQL vs structured objects) in README | ✅ | 2024-12-30 |
| 1.3 | Review and document current test coverage gaps | ✅ | 2024-12-30 |
| 1.4 | Identify framework weaknesses exposed by current tests | ✅ | 2024-12-30 |

### Deliverables

- [x] `scenarios/data_warehouse/README.md`
- [x] Gap analysis documented in this plan (see below)

### Test Coverage Gap Analysis

#### Current Coverage Summary

| Test | What It Exercises | Tables | SQL Complexity |
|------|-------------------|--------|----------------|
| `selectWithoutDatabaseObjectConstraintsTest` | Show action, simple SELECT | Single (dim_customer) | Trivial |
| `selectWithDatabaseObjectConstraintsTest` | Run action, action selection | Single (fct_orders) | Trivial |
| `aggregateOrderValueWithJsonRecordParameters` | Structured query binding, nested records | None (structured object) | N/A |

**Verdict**: Only 3 tests covering basic happy paths. No error cases, no complex SQL, no JOINs.

---

#### Gap Category 1: Schema Complexity

| Gap | Current State | Desired State | Priority |
|-----|---------------|---------------|----------|
| Schema size | 3 tables, ~8 columns | 15+ tables, 100+ columns | Medium |
| Relationships | Simple single-column FKs | Composite FKs, self-referential FKs | Low |
| Ambiguous names | None | Same column names across tables (e.g., `id`, `name`) | High |
| Constraints | Basic PK/FK/unique | CHECK constraints, NOT NULL, DEFAULT | Low |

---

#### Gap Category 2: SQL Query Patterns

| Gap | Example Natural Language | Expected SQL | Priority |
|-----|--------------------------|--------------|----------|
| **Single-table JOIN** | "Show customer names with their orders" | `SELECT ... FROM fct_orders JOIN dim_customer ...` | **High** |
| **Multi-dimension JOIN** | "Orders by customer and date" | `SELECT ... FROM fct_orders JOIN dim_customer ... JOIN dim_date ...` | **High** |
| **Simple aggregation** | "Total order value" | `SELECT SUM(order_value) FROM fct_orders` | **High** |
| **GROUP BY** | "Order totals by customer" | `SELECT customer_name, SUM(...) ... GROUP BY customer_name` | **High** |
| **HAVING** | "Customers with >1000 in orders" | `... GROUP BY ... HAVING SUM(...) > 1000` | Medium |
| **Date filtering** | "Orders in January 2024" | `WHERE date BETWEEN '2024-01-01' AND '2024-01-31'` | **High** |
| **Column aliasing** | "Show customer as 'name'" | `SELECT customer_name AS name ...` | Low |
| **ORDER BY** | "Top 10 customers by value" | `... ORDER BY order_value DESC LIMIT 10` | Medium |

---

#### Gap Category 3: Query Validation

| Gap | Current Behavior | Desired Behavior | Priority |
|-----|------------------|------------------|----------|
| Non-existent table | `QueryValidationException` thrown | ✓ Covered but not tested | **High** |
| Non-existent column | No validation | Validate columns against catalog | **High** |
| DDL attempt (CREATE, DROP) | Rejection via SELECT-only check | ✓ Implemented but not tested | **High** |
| DML attempt (INSERT, UPDATE, DELETE) | Rejection via SELECT-only check | ✓ Implemented but not tested | **High** |
| Malformed SQL | `QueryValidationException` thrown | ✓ Implemented but not tested | Medium |
| SQL injection patterns | Unknown | Test common injection patterns | Medium |

---

#### Gap Category 4: Error Handling

| Gap | Current State | Desired State | Priority |
|-----|---------------|---------------|----------|
| LLM returns invalid SQL | Unknown behavior | Graceful error with helpful message | **High** |
| LLM references wrong table | Unknown behavior | Suggest correct table from catalog | Medium |
| Empty/null user request | Unknown behavior | Appropriate error or clarification request | Medium |
| Ambiguous request | Unknown behavior | Request clarification or return PENDING | Low |

---

#### Gap Category 5: Structured Query Objects

| Gap | Current State | Desired State | Priority |
|-----|---------------|---------------|----------|
| Only one structured query type | `OrderValueQuery` with `Period` | Multiple query types for different patterns | Medium |
| Missing required fields | Not tested | Test PENDING param behavior | Medium |
| Invalid date ranges | Not tested | Validate end >= start | Low |
| Null nested objects | Not tested | Handle gracefully | Low |

---

#### Gap Category 6: Schema Metadata Delivery

| Gap | Current State | Desired State | Priority |
|-----|---------------|---------------|----------|
| Tool-based discovery | Not implemented | `SqlCatalogTool` with listTables, getTableDetails | Phase 3 |
| Adaptive hybrid | Not implemented | Frequency tracking, auto-promotion | Phase 4 |
| Large schema handling | Not tested | Test with 100+ columns in prompt | Medium |

---

#### Gap Summary by Priority

**HIGH Priority (Phase 2 focus):**
1. JOIN pattern tests (fact → dimension)
2. Aggregation tests (SUM, GROUP BY)
3. Date filtering tests
4. Column-level validation in `Query`
5. Error handling tests (invalid table, invalid SQL, DDL/DML rejection)
6. Ambiguous column name handling

**MEDIUM Priority (Phase 2-3):**
7. Multi-dimension JOIN tests
8. HAVING, ORDER BY, LIMIT tests
9. Large schema performance
10. SQL injection tests
11. Multiple structured query types

**LOW Priority (Phase 5+):**
12. Composite FK handling
13. CHECK constraint awareness
14. Column aliasing
15. Date range validation in structured objects

---

### Framework Weaknesses Identified

The following framework weaknesses have been exposed by analyzing the current data warehouse scenario implementation:

#### FWK-WEAK-001: Catalog Not Used During Query Resolution (Critical) — ✅ FIXED

**Location**: `DefaultPlanResolver.convertSqlString()` (line 228)

**Issue**: When the LLM returns SQL in a plan, the resolver calls `Query.fromSql(sql)` **without the catalog**, bypassing schema validation entirely.

**Resolution (2024-12-30)**:
1. Created `ResolutionContext` record bundling `ActionRegistry` + `SqlCatalog`
2. Updated `PlanResolver.resolve()` to accept `ResolutionContext`
3. Updated `DefaultPlanResolver.convertSqlString()` to pass catalog to `Query.fromSql()`
4. Updated `Planner.parseRawPlan()` to extract catalog from `promptContext` and pass to resolver
5. Added 3 tests validating schema validation during resolution

---

#### FWK-WEAK-002: No Column-Level Validation

**Status**: ✅ Fixed (2024-12-30)

**Location**: `Query.validateSchemaReferences()` (line 114)

**Issue**: Only table names are validated. Column references are not checked against the catalog.

**Solution Implemented**:
- Added `synonyms` field to `SqlColumn` record
- Added `matchesName()` method to `SqlColumn` for case-insensitive synonym matching
- Added `resolveColumnName()` and `findColumn()` methods to `SqlTable`
- Added `withColumnSynonyms()` method to `InMemorySqlCatalog` with uniqueness validation
- Extended `applySynonymSubstitution()` in `Query` to handle column synonyms
- Added `validateColumnReferences()` to extract and validate columns from SELECT, WHERE, and JOIN ON clauses
- Uses JSqlParser to extract column references and validates against catalog
- **Column validation is optional** (disabled by default) via `withValidateColumns(true)` on the catalog
  - This allows LLM-generated SQL with minor column errors to pass while still benefiting from synonym substitution
- 16 new tests in `QueryTest.ColumnSynonymSubstitution`

---

#### FWK-WEAK-003: No Error Recovery or Retry Mechanism

**Location**: `Planner.formulatePlan()` (line 113-127)

**Issue**: When plan parsing fails, an error plan is returned but there's no mechanism to retry with corrective guidance.

**Impact**: Single LLM mistakes result in permanent failure for that request.

**Recommended Fix**: Consider a retry loop with incremental guidance:
- Parse error → retry with syntax reminder
- Schema error → retry with available tables/columns
- Limit retries to prevent infinite loops

---

#### FWK-WEAK-004: PENDING Parameter Flow Not Exercised

**Location**: `DataWarehouseApplicationScenarioTest` / Persona constraints

**Issue**: The persona includes `"If any required parameter is unclear, use PENDING"`, but no test exercises this path.

**Impact**: We don't know if:
- The LLM actually uses PENDING correctly
- The framework handles PENDING parameters properly
- The conversation can recover from PENDING state

**Recommended Fix**: Add test case where user request is ambiguous and LLM should return PENDING.

---

#### FWK-WEAK-005: No Multi-Step Plan Testing

**Location**: `DataWarehouseApplicationScenarioTest`

**Issue**: All 3 tests expect exactly 1 plan step. No test validates multi-step query sequences.

**Impact**: We don't know if:
- Multi-step SQL plans execute correctly
- Step dependencies are handled (e.g., step 2 uses result of step 1)
- Failure in step N properly halts execution

**Recommended Fix**: Add test like "Show me customers, then show their total orders" expecting 2 steps.

---

#### FWK-WEAK-006: Action Selection Not Verified

**Location**: `DataWarehouseApplicationScenarioTest.selectWithDatabaseObjectConstraintsTest()`

**Issue**: Tests verify that `runSqlQueryInvoked()` is true, but don't verify `showSqlQueryInvoked()` is false. The LLM could be calling both.

**Impact**: Can't confirm the LLM is selecting the *correct* action exclusively.

**Recommended Fix**: Assert that wrong action was NOT invoked.

---

#### FWK-WEAK-007: No Instrumentation in Tests

**Location**: `DataWarehouseApplicationScenarioTest`

**Issue**: `DefaultPlanExecutor` is created without an `InvocationEmitter`, so no execution events are captured.

**Impact**: Cannot observe:
- Execution timing
- Parameter values at execution time
- Success/failure patterns

**Recommended Fix**: Add tests with emitter to validate event capture.

---

#### FWK-WEAK-008: Prompt Size Not Monitored

**Location**: `SqlCatalogContextContributor`

**Issue**: No safeguard or warning when schema metadata exceeds reasonable prompt size limits.

**Impact**: Large schemas could:
- Exceed context window limits
- Dilute the LLM's attention to important content
- Increase costs significantly

**Recommended Fix**: 
1. Log warning when contribution exceeds threshold (e.g., 2000 tokens)
2. Consider summarization or pagination for large schemas

---

#### Weakness Summary

| ID | Severity | Area | Effort to Fix | Status |
|----|----------|------|---------------|--------|
| FWK-WEAK-001 | **Critical** | Resolution | Medium | ✅ Fixed |
| FWK-WEAK-002 | High | Validation | Medium | ✅ Fixed |
| FWK-WEAK-003 | Medium | Reliability | High |
| FWK-WEAK-004 | Medium | Testing | Low |
| FWK-WEAK-005 | Medium | Testing | Low |
| FWK-WEAK-006 | Low | Testing | Low |
| FWK-WEAK-007 | Low | Observability | Low |
| FWK-WEAK-008 | Low | Scalability | Medium |

---

### Phase 1 Completion Checklist

```
[x] All tasks marked complete
[x] README reviewed and approved (2024-12-30)
[x] Gap analysis complete
[x] Framework weaknesses documented
[x] Phase status updated to ✅ in overview table
```

---

## Phase 2: Static Approach Hardening

**Goal**: Strengthen the existing `SqlCatalogContextContributor` approach with comprehensive testing, and implement database object tokenization.

### Tasks — Testing

| ID | Task | Status | Completed |
|----|------|--------|-----------|
| 2.1 | Add test for large schema (15+ tables, 100+ columns) | ✅ | 2024-12-30 |
| 2.2 | Add test for complex constraints (composite FKs, check constraints) | ✅ | 2024-12-30 |
| 2.3 | Add test for ambiguous column names across tables | ✅ | 2024-12-30 |
| 2.4 | Add star schema JOIN pattern tests (fact-to-dimension) | ✅ | 2024-12-30 |
| 2.5 | Add multi-dimension JOIN tests | ✅ | 2024-12-30 |
| 2.6 | Add aggregation tests (SUM, COUNT, GROUP BY, HAVING) | ✅ | 2024-12-30 |
| 2.7 | Add time intelligence tests (date ranges, period comparison) | ✅ | 2024-12-30 |
| 2.8 | Add error handling tests (non-existent tables/columns) | ✅ | 2024-12-30 |
| 2.9 | Add security tests (DDL/DML rejection, injection attempts) | ✅ | 2024-12-30 |
| 2.10 | Enhance `Query` type with column-level validation | ✅ | 2024-12-30 |

**Notes**:
- 2.4, 2.5: `joinFactToDimensionTable`, `joinMultipleDimensions`, `joinWithColumnSelection`, `joinWithFilterOnDimensionAttribute` tests in `DataWarehouseApplicationScenarioTest`
- 2.10: Implemented as FWK-WEAK-002 with optional column validation and column synonyms

### Tasks — Tokenization (FWK-SQL-002)

| ID | Task | Status | Completed |
|----|------|--------|-----------|
| 2.11 | Create `TokenGenerator` with hash + semantic prefix strategy | ✅ | 2024-12-30 |
| 2.12 | Add `withTokenization(boolean)` to `InMemorySqlCatalog` | ✅ | 2024-12-30 |
| 2.13 | Create `TokenizedSqlCatalogContextContributor` | ✅ | 2024-12-30 |
| 2.14 | Implement de-tokenization in query post-processing | ✅ | 2024-12-30 |
| 2.15 | Add `tokenizedSql()` method to `Query` for debugging | ✅ | 2024-12-30 |
| 2.16 | Fix FWK-WEAK-001: Pass catalog during Query creation in resolver | ✅ | 2024-12-30 |
| 2.17 | Handle table aliases during de-tokenization | ✅ | 2024-12-30 |
| 2.18 | Add tokenization tests to data warehouse scenario | ✅ | 2024-12-30 |
| 2.19 | Implement synonym-based tokenization (first synonym = token) | ✅ | 2024-12-30 |

**Notes**:
- 2.19: When synonyms are defined, the first synonym becomes the token instead of a cryptic hash. This produces more readable SQL while still hiding the real database object names. Remaining synonyms are shown as "also: ..." in the prompt.

### Deliverables

- [x] Expanded `DataWarehouseApplicationScenarioTest.java`
- [x] Enhanced `Query.java` validation
- [x] Documented findings on LLM query generation quality
- [x] `TokenGenerator` and tokenization infrastructure
- [x] Synonym-based tokenization (first synonym as token)
- [x] Tokenization integration tests

### Phase 2 Completion Checklist

```
[ ] All testing tasks (2.1-2.10) marked complete
[ ] All tokenization tasks (2.11-2.18) marked complete
[ ] All new tests passing
[ ] Test coverage documented
[ ] Phase status updated to ✅ in overview table
```

---

## Phase 3: Tool-Based Dynamic Metadata

**Goal**: Implement alternative approach where LLM discovers schema via tool calls.

### Tasks

| ID | Task | Status | Completed |
|----|------|--------|-----------|
| 3.1 | Create `SqlCatalogTool` with `listTables()` method | ✅ | 2024-12-30 |
| 3.2 | Add `getTableDetails(tableName)` method to tool | ✅ | 2024-12-30 |
| 3.3 | ~~Add `getTableRelationships(tableName)` method to tool~~ (removed - FK info in column tags) | ❌ | 2024-12-30 |
| 3.4 | Create supporting types: `TableSummary`, `TableDetail`, `ColumnDetail` | ✅ | 2024-12-30 |
| 3.5 | Create tool-based variant of scenario test | ✅ | 2024-12-30 |
| 3.6 | Test: LLM calls `listTables` before formulating query | ✅ | 2024-12-30 |
| 3.7 | Test: LLM calls `getTableDetails` for relevant tables only | ✅ | 2024-12-30 |
| 3.8 | Test: LLM correctly joins tables using FK tags from column info | ✅ | 2024-12-30 |
| 3.9 | Test: System handles tool errors gracefully | ✅ | 2024-12-30 |
| 3.10 | ~~Document tool call patterns and latency characteristics~~ (skipped - empirical knowledge sufficient) | ⏭️ | 2024-12-31 |

### Design Decision: No Separate Relationships Tool

The `getTableRelationships` tool was removed as redundant. FK relationships are fully
described in column tags (e.g., `fk:dim_customer.id`) returned by `getTableDetails`.
This simplifies the API and reduces tool calls needed for JOINs.

### Deliverables

- [x] `actions/sql/SqlCatalogTool.java`
- [x] `actions/sql/TableSummary.java`
- [x] `actions/sql/TableDetail.java`
- [x] `actions/sql/ColumnDetail.java`
- [x] `SqlCatalogToolTest.java` - Unit tests for the tool
- [x] `DataWarehouseToolBasedScenarioTest.java` - Tool-based integration tests

### Phase 3 Completion Checklist

```
[x] All tasks marked complete (3.10 skipped - empirical knowledge sufficient)
[x] All new tests passing
[x] Tool documented via Javadoc
[ ] Latency comparison documented (skipped)
[x] Phase status updated to ✅ in overview table
```

### Phase 3 Notes

**Tool call latency**: Experience shows tool calls approximately double total response time
due to LLM processing + network overhead. Local computation is negligible.

**Adaptive hybrid is preferred**: Rather than benchmarking, the focus shifts to Phase 4's
adaptive approach that promotes frequently-used schema to the system prompt while keeping
tools available for infrequent tables. This avoids bloating the prompt with rarely-used
information while maintaining low-latency access to common patterns.

---

## Phase 4: Adaptive Hybrid Approach

**Goal**: Implement frequency-tracking system that promotes popular schema to system prompt.

### Tasks

| ID | Task | Status | Completed |
|----|------|--------|-----------|
| 4.1 | Create `SchemaAccessTracker` interface | ✅ | 2024-12-31 |
| 4.2 | Implement `InMemorySchemaAccessTracker` for testing | ✅ | 2024-12-31 |
| 4.3 | Create `FrequencyAwareSqlCatalogTool` (wraps base tool, records access) | ✅ | 2024-12-31 |
| 4.4 | Create `AdaptiveSqlCatalogContributor` | ✅ | 2024-12-31 |
| 4.5 | Test: Initial state has no schema in prompt, tool available | ✅ | 2024-12-31 |
| 4.6 | Test: After N requests for same table, table appears in prompt | ✅ | 2024-12-31 |
| 4.7 | Test: Tool still works for infrequent tables | ✅ | 2024-12-31 |
| 4.8 | Test: Frequency thresholds are configurable | ✅ | 2024-12-31 |
| 4.9 | Document configuration options and usage patterns | ✅ | 2024-12-31 |
| 4.10 | ~~Consider persistence options for production (JDBC tracker)~~ (skipped - no current use case) | ⏭️ | 2024-12-31 |

### Deliverables

- [x] `actions/sql/SchemaAccessTracker.java`
- [x] `actions/sql/InMemorySchemaAccessTracker.java`
- [x] `actions/sql/FrequencyAwareSqlCatalogTool.java`
- [x] `actions/sql/AdaptiveSqlCatalogContributor.java`
- [x] `InMemorySchemaAccessTrackerTest.java` - Unit tests
- [x] `FrequencyAwareSqlCatalogToolTest.java` - Unit tests
- [x] `AdaptiveSqlCatalogContributorTest.java` - Unit tests
- [x] `DataWarehouseAdaptiveHybridScenarioTest.java` - LLM integration tests

### Phase 4 Completion Checklist

```
[x] All tasks marked complete (4.10 skipped - no current use case)
[x] All new tests passing
[x] Configuration documented in README.md
[x] Cold-start behavior documented in README.md
[x] Phase status updated to ✅ in overview table
```

---

## Phase 5: Multi-Turn Context Tracking & Query Refinement

**Goal**: Enable sophisticated multi-turn conversations where users can incrementally refine queries and the framework maintains context across turns.

### Design Overview

#### Core Concept: Working Context

The framework tracks a **working context** — the current object being refined across turns:

| Scenario | Working Object | Example Refinements |
|----------|---------------|---------------------|
| SQL | `Query` | "filter that by...", "add column..." |
| Shopping | `BasketRef` (reference) | "add 2 more", "remove the milk" |

Both share:
- A primary object that persists across turns
- Incremental operations that modify it
- Pronouns/references ("that", "those", "it") pointing to prior context

#### Key Design Decision: Application-Owned Persistence

The **application** owns persistence of conversation state, not the framework. This is because:
- Only the application knows what the conversation context is associated with (session, user, basket, etc.)
- Only the application knows the persistence infrastructure in use
- Transactional consistency between conversation state and domain data requires application control

The **framework** provides:
- An opaque, persistable **blob** that the application can request and restore
- Versioning and migration for schema evolution
- Integrity verification (hash) to detect tampering
- Compression for efficient storage

#### Architecture Layers

```
┌─────────────────────────────────────────────────────────────────┐
│  Application                           Framework                 │
├─────────────────────────────────────────────────────────────────┤
│  Owns:                                 Provides:                 │
│  - Storage infrastructure              - Opaque blob             │
│  - Transaction boundaries              - Serialization           │
│  - Session lifecycle                   - Versioning/migration    │
│  - Domain data (Basket, etc.)          - Integrity verification  │
│                                                                  │
│  app.saveSession(sessionId, basket,    blob = manager            │
│                  result.blob())            .toBlob(state)        │
│                                                                  │
│  state = manager.fromBlob(blob)        framework validates,      │
│                                        migrates, deserializes    │
└─────────────────────────────────────────────────────────────────┘
```

```
┌─────────────────────────────────────────────────────────────────┐
│                     Framework Core                               │
│                   (conversation pkg)                             │
├─────────────────────────────────────────────────────────────────┤
│  WorkingContext<T>              - holds the working object       │
│  WorkingContextContributor<T>   - renders context for prompt     │
│  WorkingContextExtractor<T>     - extracts context after exec    │
│  ConversationState              - stores WorkingContext          │
│  ConversationTurnResult         - includes blob for persistence  │
│  ConversationStateConfig        - history size configuration     │
│  ConversationStateMigration     - schema version upgrades        │
└─────────────────────────────────────────────────────────────────┘
                              │
          ┌───────────────────┴───────────────────┐
          ▼                                       ▼
┌───────────────────────────┐         ┌───────────────────────────┐
│     SQL Layer             │         │    Shopping Layer         │
│     (sql pkg)             │         │    (future)               │
├───────────────────────────┤         ├───────────────────────────┤
│ SqlWorkingContextContrib  │         │ BasketWorkingContextContrib│
│ SqlWorkingContextExtractor│         │ BasketWorkingContextExtract│
│ Query (working object)    │         │ BasketRef (reference only) │
└───────────────────────────┘         └───────────────────────────┘
```

---

### Tasks — Generic Framework (conversation pkg)

| ID | Task | Status | Completed |
|----|------|--------|-----------|
| 5.1a | Create `WorkingContext<T>` record | ✅ | 2024-12-31 |
| 5.1b | Create `WorkingContextContributor<T>` interface | ✅ | 2024-12-31 |
| 5.1c | Create `WorkingContextExtractor<T>` interface | ✅ | 2024-12-31 |
| 5.1d | Create `PayloadTypeRegistry` for polymorphic deserialization | ✅ | 2024-12-31 |
| 5.1e | Add `workingContext` field to `ConversationState` | ✅ | 2024-12-31 |
| 5.1f | Add `turnHistory` list to `ConversationState` | ✅ | 2024-12-31 |
| 5.1g | Add `blob` field to `ConversationTurnResult` | ✅ | 2024-12-31 |
| 5.1h | Create `ConversationStateConfig` (history size) | ✅ | 2024-12-31 |
| 5.1i | Update `ConversationManager.converse()` to accept/return blobs | ✅ | 2024-12-31 |
| 5.1j | Add `toBlob()` and `fromBlob()` methods to `ConversationManager` | ✅ | 2024-12-31 |
| 5.1k | Add `expire()` method to `ConversationManager` | ✅ | 2024-12-31 |

### Tasks — Blob Serialization & Versioning

| ID | Task | Status | Completed |
|----|------|--------|-----------|
| 5.2a | Create blob format with header (version, flags, hash) + compressed payload | ✅ | 2024-12-31 |
| 5.2b | Implement gzip compression for payload | ✅ | 2024-12-31 |
| 5.2c | Implement SHA-256 integrity hash | ⏸️ | — |
| 5.2d | Add `toReadableJson()` for debugging (decompresses blob to pretty JSON) | ✅ | 2024-12-31 |
| 5.2e | Create `ConversationStateMigration` interface | ✅ | 2024-12-31 |
| 5.2f | Create `ConversationStateMigrationRegistry` | ✅ | 2024-12-31 |
| 5.2g | Integrate versioning into blob deserialization | ✅ | 2024-12-31 |
| 5.2h | Add migration tests (v1→v2 simulation) | ✅ | 2024-12-31 |

### Tasks — SQL Layer (sql pkg)

| ID | Task | Status | Completed |
|----|------|--------|-----------|
| 5.3a | Create `SqlWorkingContextContributor` | 🔲 | — |
| 5.3b | Create `SqlWorkingContextExtractor` | 🔲 | — |
| 5.3c | Add `referencedTables()` helper to `Query` | 🔲 | — |
| 5.3d | Add `selectedColumns()` helper to `Query` | 🔲 | — |
| 5.3e | Add `whereClause()` helper to `Query` (if extractable) | 🔲 | — |
| 5.3f | Register `Query.class` in `PayloadTypeRegistry` | 🔲 | — |

### Tasks — Integration Tests (Data Warehouse)

| ID | Task | Status | Completed |
|----|------|--------|-----------|
| 5.4a | Test: "Now filter that by region = 'West'" (incremental refinement) | 🔲 | — |
| 5.4b | Test: "Add the customer name to those results" (column addition) | 🔲 | — |
| 5.4c | Test: "Show me the same query for last year" (parameter substitution) | 🔲 | — |
| 5.4d | Test: Context persists across multiple turns via blob | 🔲 | — |
| 5.4e | Test: `expire()` returns empty state | 🔲 | — |
| 5.4f | Test: History is capped at configured size | 🔲 | — |
| 5.4g | Test: Tampered blob is rejected (integrity check) | 🔲 | — |
| 5.4h | Test: Old version blob is migrated on load | 🔲 | — |

### Tasks — Documentation

| ID | Task | Status | Completed |
|----|------|--------|-----------|
| 5.5a | Document conversation patterns for query refinement | 🔲 | — |
| 5.5b | Add multi-turn examples to README | 🔲 | — |
| 5.5c | Document blob persistence pattern for applications | 🔲 | — |
| 5.5d | Document migration authoring for framework maintainers | 🔲 | — |

---

### Key Type Definitions

#### WorkingContext

```java
/**
 * The current working object in a multi-turn conversation.
 * Domain-agnostic: holds a typed payload plus metadata.
 */
public record WorkingContext<T>(
    String contextType,           // e.g., "sql.query", "shopping.basket"
    T payload,                    // The working object
    Instant lastModified,
    Map<String, Object> metadata  // Domain-specific extras
) {
    public static <T> WorkingContext<T> of(String type, T payload) {
        return new WorkingContext<>(type, payload, Instant.now(), Map.of());
    }
}
```

#### ConversationState (Enhanced)

```java
public record ConversationState(
    String originalInstruction,
    List<PendingParam> pendingParams,
    Map<String, Object> providedParams,
    String latestUserMessage,
    WorkingContext<?> workingContext,  // Current working object
    List<WorkingContext<?>> turnHistory // Prior turns (capped)
) { ... }
```

#### ConversationStateConfig

```java
public record ConversationStateConfig(
    int maxHistorySize       // Default: 10
) {
    public static ConversationStateConfig defaults() {
        return new ConversationStateConfig(10);
    }
    
    public static Builder builder() { return new Builder(); }
    
    public static class Builder {
        private int maxHistorySize = 10;
        public Builder maxHistorySize(int size) { this.maxHistorySize = size; return this; }
        public ConversationStateConfig build() { return new ConversationStateConfig(maxHistorySize); }
    }
}
```

#### ConversationTurnResult (Enhanced)

```java
public record ConversationTurnResult(
    Plan plan,
    ConversationState state,
    byte[] blob,                    // Ready-to-persist opaque blob
    List<PendingParam> pendingParams,
    Map<String, Object> providedParams
) { ... }
```

#### ConversationManager API (Revised)

```java
public class ConversationManager {
    
    /**
     * Process a conversation turn.
     * @param userMessage the user's input
     * @param priorBlob the previously stored blob (null for new conversation)
     * @return result including new blob to persist
     */
    public ConversationTurnResult converse(String userMessage, byte[] priorBlob);
    
    /**
     * Restore state from a blob (for inspection/debugging).
     * Verifies integrity, migrates if needed.
     * @throws IntegrityException if blob has been tampered with
     * @throws MigrationException if migration fails
     */
    public ConversationState fromBlob(byte[] blob);
    
    /**
     * Get readable JSON from blob (for debugging).
     */
    public String toReadableJson(byte[] blob);
    
    /**
     * Create an expired/empty state.
     * Use case: User cancels, logs out, starts over.
     */
    public ConversationTurnResult expire();
}
```

---

### Blob Format

```
┌─────────────────────────────────────────────────────────────────┐
│  Header (fixed size)                │  Payload (variable)       │
├─────────────────────────────────────┼───────────────────────────┤
│  magic: 4 bytes ("CVST")            │  gzip(JSON state)         │
│  version: 2 bytes                   │                           │
│  flags: 2 bytes                     │                           │
│  hash: 32 bytes (SHA-256 of payload)│                           │
│  payload_length: 4 bytes            │                           │
├─────────────────────────────────────┴───────────────────────────┤
│  Total header: 44 bytes                                          │
└─────────────────────────────────────────────────────────────────┘
```

- **Magic**: Identifies blob type, enables quick rejection of invalid data
- **Version**: Schema version for migration
- **Flags**: Reserved (compression type, encryption, etc.)
- **Hash**: SHA-256 of compressed payload for integrity
- **Payload**: gzip-compressed JSON

---

### Schema Versioning & Migration

#### Migration Interface

```java
/**
 * Migrates persisted ConversationState from one schema version to the next.
 */
public interface ConversationStateMigration {
    int fromVersion();
    int toVersion();
    ObjectNode migrate(ObjectNode json);
}
```

#### Migration Registry

```java
public interface ConversationStateMigrationRegistry {
    void register(ConversationStateMigration migration);
    List<ConversationStateMigration> getMigrationPath(int fromVersion, int toVersion);
    int currentVersion();
}
```

#### Migration on Load

When deserializing a blob:
1. Read version from header
2. If version < current, apply migrations sequentially (v1→v2→v3...)
3. Verify hash after decompression
4. Parse migrated JSON to `ConversationState`

---

### SQL Working Context Contribution

```java
public class SqlWorkingContextContributor implements WorkingContextContributor<Query> {
    
    @Override
    public String contextType() { return "sql.query"; }
    
    @Override
    public String renderForPrompt(WorkingContext<Query> ctx) {
        Query query = ctx.payload();
        return """
            PREVIOUS QUERY CONTEXT:
            SQL: %s
            Tables: %s
            Columns: %s
            
            User may refine with:
            - "filter by X" → Add WHERE clause
            - "add column Y" → Extend SELECT
            - "for last month" → Substitute date parameters
            """.formatted(
                query.sqlString(),
                String.join(", ", query.referencedTables()),
                String.join(", ", query.selectedColumns())
            );
    }
}
```

---

### Application Usage Example

```java
public class ShoppingSessionService {
    
    private final ConversationManager conversationManager;
    private final SessionRepository sessionRepository;  // Application's storage
    
    public ShoppingResponse processUserMessage(String sessionId, String message) {
        // Load prior blob from application's storage
        SessionEntity session = sessionRepository.findById(sessionId).orElse(null);
        byte[] priorBlob = session != null ? session.getConversationBlob() : null;
        
        // Framework processes the turn
        ConversationTurnResult result = conversationManager.converse(message, priorBlob);
        
        // Execute the plan
        PlanExecutionResult executed = executor.execute(result.plan());
        
        // Application saves everything in ONE transaction
        session = session != null ? session : new SessionEntity(sessionId);
        session.setConversationBlob(result.blob());  // Framework's opaque blob
        session.setBasket(basketService.getBasket(sessionId));  // Application's data
        sessionRepository.save(session);
        
        return buildResponse(executed);
    }
    
    public void cancelShopping(String sessionId) {
        SessionEntity session = sessionRepository.findById(sessionId).orElseThrow();
        ConversationTurnResult expired = conversationManager.expire();
        session.setConversationBlob(expired.blob());  // Or set to null
        session.setBasket(null);
        sessionRepository.save(session);
    }
}
```

---

### What Gets Removed (vs. previous design)

| Component | Status |
|-----------|--------|
| `ConversationStateStore` interface | **Remove** |
| `InMemoryConversationStateStore` | **Remove** |
| TTL management in framework | **Remove** (application's concern) |
| `cleanupExpired()` | **Remove** |
| `expire(sessionId)` | **Replaced** by `expire()` returning empty state |

---

### Deliverables

#### Generic Framework (conversation pkg)
- [ ] `WorkingContext.java`
- [ ] `WorkingContextContributor.java`
- [ ] `WorkingContextExtractor.java`
- [ ] `PayloadTypeRegistry.java`
- [ ] `ConversationStateConfig.java`
- [ ] `ConversationStateMigration.java`
- [ ] `ConversationStateMigrationRegistry.java`
- [ ] `DefaultConversationStateMigrationRegistry.java`
- [ ] Enhanced `ConversationState.java`
- [ ] Enhanced `ConversationTurnResult.java`
- [ ] Enhanced `ConversationManager.java` with blob API
- [ ] Blob serialization with header, compression, hash
- [ ] `ConversationStateDebug.java` (toReadableJson)
- [ ] Unit tests for all new components

#### SQL Layer (sql pkg)
- [ ] `SqlWorkingContextContributor.java`
- [ ] `SqlWorkingContextExtractor.java`
- [ ] Enhanced `Query.java` with extraction helpers
- [ ] Unit tests for SQL context handling

#### Integration Tests
- [ ] Multi-turn refinement tests in `DataWarehouseApplicationScenarioTest`
- [ ] Blob persistence round-trip tests
- [ ] Integrity verification tests
- [ ] Migration tests
- [ ] History cap tests

#### Documentation
- [ ] README section on multi-turn conversations
- [ ] Blob persistence pattern guide for applications
- [ ] Migration authoring guide

---

### Phase 5 Completion Checklist

```
[x] All generic framework tasks (5.1a-5.1k) complete
[x] All blob/versioning tasks (5.2a-5.2h) complete (5.2c deferred)
[ ] All SQL layer tasks (5.3a-5.3f) complete
[ ] All integration tests (5.4a-5.4h) passing
[ ] All documentation tasks (5.5a-5.5d) complete
[ ] Phase status updated to ✅ in overview table
```

---

## Phase 6: SQL Module Positioning (Deferred)

**Status**: ⏸️ Deferred — to be revisited after Phase 5

**Goal**: Determine the appropriate architectural positioning of SQL support within the project.

### Context

The SQL support code (`Query`, `SqlCatalog`, `QueryResolver`, tokenization, synonyms, etc.) is currently located in `org.javai.springai.actions.sql`. A decision is pending on whether to:

1. **Keep in framework** — SQL remains a framework feature in `actions.sql`
2. **Move to scenario** — SQL moves to `scenarios.data_warehouse.sql` as an example implementation
3. **Separate module** — SQL becomes `springai-actions-sql`, an optional add-on module

### Considerations

| Option | Pros | Cons |
|--------|------|------|
| **Keep in framework** | Simple, no restructuring | SQL-specific code in core framework |
| **Move to scenario** | Clean core, SQL is "just an example" | Not reusable as library |
| **Separate module** | Reusable, follows Spring pattern | Multi-module complexity |

### Decision Criteria

The decision should be made after:
- Phase 5 (multi-turn context) is complete and tested
- Shopping scenario is designed (to see if similar patterns emerge)
- The framework's extension points are proven stable

### Tasks (When Activated)

| ID | Task | Status |
|----|------|--------|
| 6.1 | Evaluate reusability of SQL code across hypothetical applications | 🔲 |
| 6.2 | Design shopping scenario to validate framework abstractions | 🔲 |
| 6.3 | Decide on module vs. scenario placement | 🔲 |
| 6.4 | Execute restructuring if decided | 🔲 |
| 6.5 | Update documentation to reflect new structure | 🔲 |

---

## Current State

### Files
```
scenarios/data_warehouse/
├── DataWarehouseApplicationScenarioTest.java  # 3 integration tests
├── DataWarehouseActions.java                  # 3 actions
├── OrderValueQuery.java                       # Nested record for aggregate queries
└── Period.java                                # Date range record
```

### Current Tests

| Test | Description | Framework Feature |
|------|-------------|-------------------|
| `selectWithoutDatabaseObjectConstraintsTest` | Simple SELECT with column names | Basic action invocation |
| `selectWithDatabaseObjectConstraintsTest` | SELECT with run (vs show) | Action selection |
| `aggregateOrderValueWithJsonRecordParameters` | Aggregate with nested Period | Nested record binding |

### Current Actions

| Action | Parameters | Purpose |
|--------|------------|---------|
| `showSqlQuery` | `Query query` | Display SQL without execution |
| `runSqlQuery` | `Query query` | Execute SQL and return results |
| `aggregateOrderValue` | `OrderValueQuery orderValueQuery` | Aggregate with customer/period filter |

### Current Framework Components

- **`SqlCatalogContextContributor`**: Adds full schema metadata to system prompt
- **`InMemorySqlCatalog`**: In-memory catalog implementation with tables/columns
- **`Query`**: Validated SQL SELECT with JSqlParser parsing and schema validation

---

## Enhancement Areas

### Area 1: Scenario README

Create `src/test/java/org/javai/springai/scenarios/data_warehouse/README.md` explaining:

1. **Purpose**: What this scenario demonstrates and why star schemas matter
2. **Domain Model**: The fictional data warehouse schema (fct_orders, dim_customer, dim_date)
3. **Use Cases**: What kinds of queries users might ask
4. **Framework Features Exercised**: Which capabilities are being tested
5. **Running the Tests**: Prerequisites (OPENAI_API_KEY, RUN_LLM_TESTS)

---

### Area 2: Schema Metadata Delivery Approaches

The scenario currently uses only **Approach 1** (static prompt contribution). We need to implement and test all three approaches:

#### Approach 1: Static Prompt Contribution (Current)

**How it works**: Developer provides `SqlCatalogContextContributor` with a pre-populated catalog. All schema metadata is included in every system prompt.

**Pros**: Simple, deterministic, low latency  
**Cons**: System prompt can become large; irrelevant schema info included

**Existing Implementation**:
- `SqlCatalogContextContributor` ✓
- `InMemorySqlCatalog` ✓
- Integration in `DataWarehouseApplicationScenarioTest` ✓

**Enhancements Needed**:
- [ ] Add tests for large schemas (many tables/columns)
- [ ] Add tests for schema with complex constraints
- [ ] Add tests for ambiguous column names across tables

---

#### Approach 2: Tool-Based Dynamic Metadata (New)

**How it works**: Developer provides a tool (`SqlCatalogTool`) that the LLM can call to dynamically retrieve schema information. System prompt starts light; LLM queries for needed metadata.

**Pros**: Smaller system prompts; only relevant metadata fetched  
**Cons**: Added latency from tool calls; requires LLM to know when to call the tool

**Implementation Plan**:

1. **Create `SqlCatalogTool`** interface and default implementation:
   ```java
   public class SqlCatalogTool {
       @Tool(description = "List all tables in the data warehouse with their descriptions and types (fact/dimension)")
       public List<TableSummary> listTables();
       
       @Tool(description = "Get column details for a specific table including data types, constraints, and tags")  
       public TableDetail getTableDetails(String tableName);
       // Note: No separate getTableRelationships - FK info is in column tags (e.g., fk:dim_customer.id)
   }
   ```

2. **Create supporting types**:
   - `TableSummary`: name, description, type (fact/dimension), column count, synonyms
   - `TableDetail`: columns with full metadata including FK tags
   - `ColumnDetail`: name, type, description, tags, constraints, synonyms

3. **Add scenario test variant** that uses tools instead of prompt contributor

4. **Test cases**:
   - [ ] LLM calls `listTables` before formulating query
   - [ ] LLM calls `getTableDetails` for relevant tables only
   - [ ] LLM correctly joins tables using relationship info
   - [ ] System handles tool errors gracefully

---

#### Approach 3: Adaptive Hybrid with Frequency Tracking (New)

**How it works**: Starts with Approach 2 (tool-based). Framework tracks which schema elements are frequently requested. Popular elements are "promoted" to the system prompt, reducing future tool calls.

**Pros**: Best of both worlds; adapts to actual usage patterns  
**Cons**: More complex; requires persistence; cold-start latency

**Implementation Plan**:

1. **Create `SchemaAccessTracker`** interface:
   ```java
   public interface SchemaAccessTracker {
       void recordTableAccess(String tableName);
       void recordColumnAccess(String tableName, String columnName);
       Set<String> frequentlyAccessedTables(int threshold);
       Set<String> frequentlyAccessedColumns(String tableName, int threshold);
   }
   ```

2. **Create `InMemorySchemaAccessTracker`** (for testing) and `JdbcSchemaAccessTracker` (for production)

3. **Create `AdaptiveSqlCatalogContributor`**:
   - Consumes `SchemaAccessTracker` and `SqlCatalog`
   - On `contribute()`, includes only frequently-accessed schema elements
   - Registers tool for less-frequent lookups

4. **Create `FrequencyAwareSqlCatalogTool`**:
   - Wraps base tool
   - Records access via tracker on each call

5. **Add scenario tests**:
   - [ ] Initial state: no schema in prompt, tool available
   - [ ] After N requests for same table: table appears in prompt
   - [ ] Tool still works for infrequent tables
   - [ ] Frequency thresholds are configurable

---

### Area 3: Expanded Query Test Cases

Add tests for SQL patterns common in data warehouse scenarios:

#### 3.1 Star Schema JOIN Patterns

- [ ] Fact-to-dimension JOIN (e.g., fct_orders JOIN dim_customer)
- [ ] Multi-dimension JOIN (fact joined to 2+ dimensions)
- [ ] JOIN with filtering on dimension attributes

#### 3.2 Aggregations

- [ ] Simple aggregation (SUM, COUNT, AVG on measures)
- [ ] GROUP BY with dimension attributes
- [ ] HAVING clause filtering
- [ ] Multiple measures in one query

#### 3.3 Time Intelligence

- [ ] Date range filtering (between dates)
- [ ] Period comparison (this month vs last month)
- [ ] Rolling aggregations (if supported)

#### 3.4 Query Variations

- [ ] Synonym handling ("sales" vs "order_value")
- [ ] Ambiguous column names requiring table qualification
- [ ] Column selection vs SELECT *

---

### Area 4: Error Handling and Edge Cases

- [ ] Reference to non-existent table → clear error message
- [ ] Reference to non-existent column → clear error message  
- [ ] Attempted DDL (CREATE, DROP) → rejection
- [ ] Attempted DML (INSERT, UPDATE, DELETE) → rejection
- [ ] SQL injection attempts → safe handling
- [ ] Empty/null input handling

---

### Area 5: Query Type Validation

The `Query` type already validates:
- Syntactic correctness (JSqlParser)
- SELECT-only enforcement
- Schema reference validation

Enhancements:
- [ ] Add column-level validation (not just table)
- [ ] Add semantic validation for star schema patterns
- [ ] Consider validation result as structured feedback (not just exceptions)

---

### Area 6: Result Context and Chaining

Enable queries that build on previous results:

- [ ] "Now filter that by region = 'West'"
- [ ] "Add the customer name to those results"
- [ ] "Show me the same query for last year"

This requires:
- Maintaining query context across turns
- Tracking which tables/columns were previously referenced
- Enabling incremental query refinement

---

## Success Criteria

| Criterion | Phase | Status |
|-----------|-------|--------|
| README clearly explains scenario purpose and star schema concepts | 1 | ✅ |
| All three metadata delivery approaches implemented and tested | 2, 3, 4 | 🔲 |
| Test coverage includes happy path, edge cases, and error scenarios | 2, 3, 4 | 🔲 |
| Framework weaknesses identified and documented | All | 🔲 |
| New framework capabilities reusable beyond this scenario | 3, 4 | 🔲 |

---

## Files to Create/Modify

### New Files

| File | Purpose |
|------|---------|
| `scenarios/data_warehouse/README.md` | Scenario documentation |
| `actions/sql/SqlCatalogTool.java` | Tool for dynamic schema lookup |
| `actions/sql/TableSummary.java` | Tool response type |
| `actions/sql/TableDetail.java` | Tool response type |
| `actions/sql/Relationship.java` | FK relationship type |
| `actions/sql/SchemaAccessTracker.java` | Interface for frequency tracking |
| `actions/sql/InMemorySchemaAccessTracker.java` | Test implementation |
| `actions/sql/AdaptiveSqlCatalogContributor.java` | Hybrid approach |

### Modified Files

| File | Changes |
|------|---------|
| `DataWarehouseApplicationScenarioTest.java` | Add new test cases |
| `DataWarehouseActions.java` | Potentially add new actions |
| `Query.java` | Enhanced validation |
| `InMemorySqlCatalog.java` | Additional metadata support if needed |

---

## Progress Tracking

### How to Update This Document

When completing a task:
1. Change the task status from `🔲` to `✅`
2. Add the completion date in the `Completed` column (format: YYYY-MM-DD)
3. Check off corresponding deliverables in the phase section
4. When all tasks in a phase are complete:
   - Complete the Phase Completion Checklist
   - Update the phase status in the Overview table from `🔲 Not Started` or `🔄 In Progress` to `✅ Complete`
   - Add completion date to the Overview table

### Status Symbols

| Symbol | Meaning |
|--------|---------|
| 🔲 | Not started |
| 🔄 | In progress |
| ✅ | Complete |
| ⏸️ | Blocked/Paused |
| ❌ | Cancelled |

---

## Framework Improvement Requirements

The following framework improvements have been identified during scenario analysis. These are not specific to the data warehouse scenario but apply broadly to SQL-related functionality.

### FWK-SQL-001: Query Dialect Configuration

**Current State**: The `Query` type requires callers to explicitly specify the dialect on each call:

```java
String sql = query.sqlString(Query.Dialect.POSTGRES);  // Must specify every time
```

**Problem**: This is cumbersome and error-prone. The target database dialect is typically fixed for the lifetime of an application and should not need to be repeated.

**Desired State**: The developer configures the dialect once (at application startup or in configuration), and thereafter `query.sqlString()` automatically returns dialect-appropriate SQL:

```java
// Configuration (once, at startup)
QueryDialectConfiguration.setDefault(Query.Dialect.POSTGRES);

// Usage (throughout application)
String sql = query.sqlString();  // Automatically uses POSTGRES
```

**Implementation Options**:

1. **Static default dialect**: `Query.setDefaultDialect(Dialect.POSTGRES)` — simple but not thread-safe for multi-tenant scenarios
2. **Builder/Factory pattern**: `QueryFactory.withDialect(POSTGRES).fromSql(...)` — creates Query instances pre-configured with dialect
3. **Context-based**: Dialect stored in `SqlCatalog` and Query reads from its catalog reference
4. **Spring configuration**: `@ConfigurationProperties` for `spring.ai.actions.sql.dialect`

**Recommended Approach**: Option 3 (context-based via SqlCatalog) since Query already holds a catalog reference. This keeps dialect configuration close to schema configuration.

**Priority**: Medium — Quality-of-life improvement, not blocking

**Status**: ✅ Complete (2024-12-30)

**Implementation Summary**:
- Added `dialect()` default method to `SqlCatalog` interface (returns ANSI by default)
- Added `withDialect(Query.Dialect)` fluent method to `InMemorySqlCatalog`
- Updated `Query.sqlString()` to use catalog's dialect when available
- Added 7 new tests in `QueryTest.CatalogDialectConfiguration`

---

### FWK-SQL-002: Database Object Tokenization

**Status**: ✅ Complete (2024-12-30)

**Purpose**: Prevent exposure of real database schema names to external LLM providers by replacing table and column names with tokens in the system prompt and LLM responses.

#### Design Decisions

Full design rationale captured in `QUESTIONNAIRE-TOKENIZATION.md`.

| Aspect | Decision |
|--------|----------|
| Token format | **Synonym-based** (first synonym becomes token) OR hash-based fallback |
| Semantic prefixes | `ft_` (fact), `dt_` (dimension), `bt_` (bridge), `t_` (generic), `c_` (column) |
| Token stability | Stable per catalog instance |
| Storage | In-memory only |
| Column scoping | Table-scoped |
| Tokenization toggle | Optional via `catalog.withTokenization(true/false)` |

#### Token Strategy (Updated 2024-12-30)

**Synonym-Based Tokenization**: Instead of always using cryptic hash-based tokens, the framework now uses the **first synonym as the token** when synonyms are defined:

| Scenario | Token Example | Advantages |
|----------|--------------|------------|
| Synonyms defined | `fct_orders` → `orders` (first synonym) | Readable SQL, self-documenting |
| No synonyms | `fct_orders` → `ft_abc123` (cryptic hash) | Full obfuscation |

**Key insight**: The LLM connects user input to tables via descriptions and synonyms, not the table name itself. Using `orders` as a token for `fct_orders` achieves:
- More readable generated SQL (`SELECT value FROM orders` vs `SELECT c_abc123 FROM ft_abc123`)
- No loss of obfuscation (real schema name `fct_orders` is still hidden)
- Remaining synonyms still communicated as "also: ..."

#### Processing Pipeline

```
LLM Response (tokenized SQL)
    ↓
Step 1a: Verify single SQL statement
    ↓
Step 1b: Verify SELECT statement  
    ↓
Step 1c: Verify valid ANSI SQL syntax
    ↓
Step 2: De-tokenize table/column names (catalog lookup, not pattern-based)
    ↓
Step 3: Apply synonym substitution (AST-based)
    ↓
Step 4: Validate schema references (optional column validation)
    ↓
Step 5: Convert to target dialect
    ↓
Query object ready for application
```

#### Prompt Format (Tokenized with Synonyms)

```
TOKENIZED SQL CATALOG:
- orders: Order transactions [tags: fact] (also: sales)
  • value (type=decimal; Order total; also=amount)
  • c_abc123 (type=integer; FK to customers; tags=fk:customers.id)
- customers: Customer dimension [tags: dimension] (also: cust)
  • id (type=integer; Customer PK; tags=pk)
  • name (type=varchar; Customer name; also=customer_name)
```

#### "No Matching Data" Handling

When the user requests data for which no relevant table exists, the LLM should recognize this and return a `PlanStep.PendingActionStep` with a clarification message rather than attempting to formulate an invalid query.

#### Query Type

```java
public record Query(Select select, SqlCatalog catalog) {
    public String sqlString() { ... }     // De-tokenized, dialect-converted
    public String tokenizedSql() { ... }  // Re-tokenized for debugging
}
```

#### Implementation Tasks (All Complete)

| ID | Task | Status |
|----|------|--------|
| TOK-01 | Create `TokenGenerator` with hash + prefix strategy | ✅ |
| TOK-02 | Add `withTokenization(boolean)` to `InMemorySqlCatalog` | ✅ |
| TOK-03 | Update `SqlCatalogContextContributor` for tokenized format | ✅ |
| TOK-04 | Implement de-tokenization in `Query.fromSql()` | ✅ |
| TOK-05 | Add `tokenizedSql()` method to `Query` | ✅ |
| TOK-06 | Update `DefaultPlanResolver` to pass catalog during Query creation | ✅ |
| TOK-07 | Add tokenization tests | ✅ |
| TOK-08 | Handle table aliases during de-tokenization | ✅ |
| TOK-09 | Implement synonym-based tokenization (first synonym = token) | ✅ |

**Priority**: High — Required for production use with external LLM providers

---

### FWK-EXEC-001: Declarative Plan State Handlers

**Status**: 🔲 Not started

**Purpose**: Improve developer experience by allowing declarative registration of handlers for PENDING and ERROR plan states, eliminating procedural state-checking in application code.

#### Design Summary

| Aspect | Decision |
|--------|----------|
| Approach | Interface-based (`PendingActionHandler`, `ErrorActionHandler`) |
| Return type | Void (fire-and-forget) |
| Partial execution | Never — any PENDING/ERROR step blocks all execution |
| Registration | On the Planner |
| Missing handler | Throws exception to encourage handler registration |

#### Interfaces

```java
@FunctionalInterface
public interface PendingActionHandler {
    void handle(
        Plan plan,
        List<PlanStep.PendingStep> pendingSteps,
        String originalRequest,
        ConversationState state
    );
}

@FunctionalInterface
public interface ErrorActionHandler {
    void handle(
        Plan plan,
        List<PlanStep.ErrorStep> errorSteps,
        String originalRequest,
        ConversationState state,
        Throwable cause  // May be null if no exception
    );
}
```

#### Planner Configuration

```java
Planner planner = Planner.builder()
    .withChatClient(chatClient)
    .actions(myActions)
    .onPending((plan, steps, request, state) -> {
        // Respond to user asking for clarification
    })
    .onError((plan, steps, request, state, cause) -> {
        // Log error, notify user
    })
    .build();
```

#### Execution Flow

```
Plan received
    ↓
Plan status?
    ├── READY → Execute all action steps normally
    ├── PENDING → Call PendingActionHandler (no steps executed)
    └── ERROR → Call ErrorActionHandler (no steps executed)
```

#### Exception on Missing Handler

When `execute()` is called on a PENDING or ERROR plan without a registered handler:

```java
throw new IllegalStateException(
    "Plan is PENDING but no PendingActionHandler is registered. " +
    "Register a handler via Planner.builder().onPending(...)"
);
```

#### Implementation Tasks

| ID | Task | Status |
|----|------|--------|
| EXEC-01 | Create `PendingActionHandler` interface | 🔲 |
| EXEC-02 | Create `ErrorActionHandler` interface | 🔲 |
| EXEC-03 | Add `onPending()` and `onError()` to `Planner.Builder` | 🔲 |
| EXEC-04 | Modify `DefaultPlanExecutor` to check plan status and route to handlers | 🔲 |
| EXEC-05 | Add exception for missing handlers | 🔲 |
| EXEC-06 | Add tests for handler invocation | 🔲 |
| EXEC-07 | Update data warehouse scenario to use handlers | 🔲 |

**Priority**: Medium — DX improvement, not blocking

---

### FWK-SQL-003: Table Synonyms for Automatic Mapping

**Status**: ✅ Complete (2024-12-30)

**Purpose**: Reduce LLM retry roundtrips by providing table synonyms that the framework can automatically substitute when the LLM uses informal/common table names instead of exact catalog names.

#### Problem Statement

The LLM occasionally uses informal table names (e.g., "orders", "customers") instead of the exact catalog names (e.g., "fct_orders", "dim_customer"). While the catalog description provides semantic context, the exact table name may be tokenized or use conventions unfamiliar to the LLM.

#### Solution

Add an optional array of **synonyms** to each table definition. When validating a query:
1. If a table name matches a catalog table exactly → proceed normally
2. If a table name matches a synonym for any table → automatically substitute with the catalog table name
3. If no match → raise validation error (or retry with LLM if retry mechanism is enabled)

This is a **local, fast correction** that avoids a roundtrip to the LLM.

#### API Design

```java
TableDefinition table = TableDefinition.create("fct_orders")
    .description("Fact table for order transactions")
    .withSynonyms("orders", "order", "sales", "transactions")
    .withColumn(...)
    .build();
```

#### Implementation

| ID | Task | Status |
|----|------|--------|
| SYN-01 | Add `synonyms` field to `SqlTable` record | ✅ |
| SYN-02 | Add `withSynonyms(String...)` builder method to `InMemorySqlCatalog` | ✅ |
| SYN-03 | Add `resolveTableName()` and `matchesName()` methods to catalog | ✅ |
| SYN-04 | Integrate synonym resolution into `Query.fromSql()` | ✅ |
| SYN-05 | Add tests for synonym substitution (11 tests) | ✅ |

**Priority**: High — Immediate fix for LLM indeterminism without requiring retry roundtrip

**Implementation Summary**:
- Added `synonyms` field to `SqlCatalog.SqlTable` record
- Added `matchesName(String)` method to `SqlTable` for case-insensitive synonym matching
- Added `resolveTableName(String)` method to `SqlCatalog` interface
- Added `withSynonyms(String tableName, String... synonyms)` to `InMemorySqlCatalog`
- Added `applySynonymSubstitution()` to `Query.fromSql()` that rewrites SQL before parsing
- Uses regex word-boundary matching to avoid partial replacements (e.g., "order" won't match "order_items")
- 11 comprehensive tests in `QueryTest.SynonymSubstitution`
- Updated data warehouse scenario tests to use synonyms

---

### FWK-PLAN-001: Generic Retry/Correction Mechanism

**Status**: 🔲 Not Started

**Purpose**: Provide a generic mechanism for retrying failed plan steps or validations by re-prompting the LLM with specific error context.

#### Problem Statement

LLMs occasionally generate responses that fail validation (syntax errors, schema violations, wrong table names). A single retry with clear error context often produces a correct response. This should be:
- Transparent to the application developer
- Configurable (enable/disable, max retries)
- Extensible for different correction strategies

#### Design

```java
public interface CorrectionStrategy<T> {
    boolean canHandle(ValidationError error);
    String buildCorrectionPrompt(T failedValue, ValidationError error, Map<String, Object> context);
    T parseCorrection(String llmResponse, Map<String, Object> context);
}
```

The `Planner` would orchestrate retries:
1. Initial response fails validation
2. Find applicable `CorrectionStrategy`
3. Build correction prompt with error details
4. Send to LLM
5. Parse correction response
6. Retry validation (max N times)
7. Return corrected value or final error

#### SQL-Specific Implementation

```java
public class QueryCorrectionStrategy implements CorrectionStrategy<Query> {
    @Override
    public String buildCorrectionPrompt(Query failed, ValidationError error, Map<String, Object> context) {
        SqlCatalog catalog = (SqlCatalog) context.get("sql");
        return """
            The following SQL has an error:
            ```sql
            %s
            ```
            Error: %s
            
            Available tables: %s
            
            Please provide a corrected SQL query.
            """.formatted(failed.tokenizedSql(), error.getMessage(), catalog.tableNames());
    }
}
```

#### Configuration

```java
Planner planner = Planner.builder()
    .withChatClient(chatClient)
    .withMaxRetries(2)
    .addCorrectionStrategy(new QueryCorrectionStrategy())
    .build();
```

**Priority**: Medium — Layered on top of synonym substitution for remaining edge cases

---

## Notes

- The star schema assumption is powerful but not universal. Consider how to handle other warehouse patterns (snowflake, data vault) in future iterations.
- The adaptive hybrid approach could benefit from ML-based prediction, but start with simple frequency counting.
- Consider how this scenario relates to the MCP (Model Context Protocol) for external database introspection.

