# Scenario Enhancement Plan

This document outlines the high-level plan to enhance each of the four test scenarios in the `scenarios` package. Each scenario exercises a distinct slice of the framework's capabilities and serves as a driver for identifying weaknesses.

---

## Overview

The scenarios package contains four integration test suites that simulate real-world application patterns:

| Scenario | Domain | Primary Focus |
|----------|--------|---------------|
| `data_warehouse` | SQL Analytics | SQL catalog context, star schema queries, nested record binding |
| `protocol` | Quality Assurance | Multi-step plans, tools, instrumentation, protocol filtering |
| `shopping` | E-commerce | Multi-turn conversations, state management, pending params |
| `stats_app` | Statistical Analysis | Parameter constraints, SPC workflows, context merging |

Each scenario will be enhanced in sequence, with a dedicated plan document created when work begins.

---

## 1. `data_warehouse`

**Current State:**
- 3 tests covering basic SQL query generation and execution
- Uses `SqlCatalogContextContributor` for schema-aware prompts
- Tests nested record binding via `OrderValueQuery` with embedded `Period`
- Actions: `showSqlQuery`, `runSqlQuery`, `aggregateOrderValue`

**Enhancement Goals:**
1. **Expand SQL Coverage** — Add tests for JOINs across fact/dimension tables, aggregations with GROUP BY, and WHERE clause filtering
2. **Multi-Step Queries** — Test plans that chain multiple SQL operations (e.g., subqueries, CTEs)
3. **Schema Validation** — Test error handling when queries reference non-existent tables/columns
4. **Dialect Support** — Add tests for SQL dialect variations if framework supports them
5. **Edge Cases** — Test ambiguous column references, optional date ranges, null handling
6. **Real Data Assertions** — Extend beyond invocation checks to verify parameter binding accuracy

**Framework Weaknesses to Probe:**
- How well does the LLM respect schema constraints from the SQL catalog?
- Can nested records with multiple levels be parsed correctly?
- Does the system handle SQL-injection-like inputs gracefully?

---

## 2. `protocol`

**Current State:**
- 1 comprehensive test exercising protocol-driven notebook generation
- Integrates `ProtocolCatalogTool` for protocol lookup (list + get)
- Uses `InvocationEmitter` for event capture and `PiiTokenizingAugmentor`
- Tests filtering between FDX 2024, Legacy, and Experimental protocols
- Validates notebook content contains correct sections

**Enhancement Goals:**
1. **Protocol Selection Logic** — Add tests for Legacy FDX v1 and Experimental protocols to ensure correct action routing
2. **Multi-Bundle Workflows** — Test plans that operate on multiple bundles in sequence
3. **Tool Invocation Ordering** — Verify that `listProtocols` → `getProtocol` ordering is enforced
4. **Instrumentation Coverage** — Assert complete event lifecycle (REQUESTED → SUCCEEDED/FAILED) for both tools and actions
5. **PII Tokenization Verification** — Add assertions on token replacement and de-tokenization
6. **Error Paths** — Test behavior when protocol ID is invalid or bundle doesn't exist
7. **ActionContext Flow** — Verify context accumulation across multi-step plans

**Framework Weaknesses to Probe:**
- Does the model correctly avoid legacy/experimental actions when following FDX 2024?
- How robust is tool→action handoff?
- Are instrumentation events captured for all invocation types?

---

## 3. `shopping`

**Current State:**
- 12 tests — the most comprehensive scenario
- Full shopping lifecycle: start session, add items, view basket, checkout
- Multi-turn conversation with context merging for pending parameters
- State management via `ShoppingActions` basket map
- Tool integration with `SpecialOfferTool`
- Tests for error cases (out-of-stock, unrecognized requests)

**Enhancement Goals:**
1. **Complex Cart Operations** — Test quantity updates, partial removals, basket modifications
2. **Offer Application** — Verify offers from `SpecialOfferTool` influence plan or basket
3. **Confirmation Flows** — Test checkout confirmation constraint (persona says "do not commit without confirmation")
4. **Session Isolation** — Verify different session IDs maintain separate basket state
5. **Disambiguation** — Test ambiguous product names requiring clarification
6. **Conversation Recovery** — Test resumption after errors or abandoned turns
7. **Price Calculations** — Add assertions on `computeTotal` results with known prices
8. **Bulk Operations** — Test adding multiple distinct items in one request

**Framework Weaknesses to Probe:**
- How well does context merge work across 5+ turns?
- Can the system disambiguate between similar products?
- Is session state isolation properly enforced?

---

## 4. `stats_app`

**Current State:**
- 6 tests covering SPC workflows
- Tests `displayControlChart`, `exportControlChartToExcel`, `evaluateSpcReadiness`
- Uses parameter constraints (`allowedValues`, `allowedRegex`)
- Multi-turn test for pending parameter resolution (missing bundleId)
- Error test for unsupported operation (ANOVA)

**Enhancement Goals:**
1. **Constraint Enforcement** — Add tests that violate `allowedValues` and `allowedRegex` to verify rejection
2. **Multi-Action Plans** — Test requests requiring multiple SPC operations in sequence
3. **Measurement Concept Normalization** — Test synonyms ("disp", "displacement force" → "displacement")
4. **Bundle ID Formats** — Test edge cases in regex matching
5. **Comparative Analysis** — Test requests comparing metrics across bundles
6. **Context Carryover** — Test that measurement concept persists across turns like bundleId
7. **Threshold Parameters** — If SPC has configurable limits, test their binding

**Framework Weaknesses to Probe:**
- Are parameter constraints enforced before execution or during parsing?
- How does the system handle synonym/alias resolution?
- Does pending parameter resolution work for multiple missing params?

---

## Execution Order

1. **`data_warehouse`** — Start here; exercises SQL DSL and nested binding core to many apps
2. **`shopping`** — Build on multi-turn patterns; stress-test conversation state
3. **`stats_app`** — Refine constraint handling and parameter validation
4. **`protocol`** — Cap with the most complex tool+action+instrumentation scenario

Each scenario will receive a dedicated `PLAN-{SCENARIO}.md` document upon commencement.

---

## Success Criteria

For each scenario enhancement:

- [ ] All existing tests continue to pass
- [ ] New tests cover at least 3 additional edge cases
- [ ] At least 1 test exercises a known or suspected framework weakness
- [ ] Test coverage includes both happy-path and error scenarios
- [ ] Framework improvements are identified and documented as issues

---

## Next Steps

Create `PLAN-DATA_WAREHOUSE.md` and begin enhancement work on the `data_warehouse` scenario.

