# Data Warehouse Scenario

## Overview

This scenario demonstrates an AI assistant that helps end users formulate SQL queries against a data warehouse. The assistant transforms natural language requests into executable SQL, leveraging structured metadata about the warehouse schema.

## Why Star Schemas Matter

Data warehouses are often organized as **star schemas**: a central **fact table** (containing measures/metrics) surrounded by **dimension tables** (containing descriptive attributes). This structure is:

- **Predictable**: Known patterns for JOINs (fact → dimension via FK)
- **Semantic**: Clear distinction between "what to measure" (facts) and "how to slice" (dimensions)
- **Constrained**: Limited, well-defined relationships reduce query complexity

When the LLM knows the schema follows a star pattern, it can apply powerful heuristics:

- Measures come from fact tables; filter/group by attributes come from dimensions
- JOINs always flow from fact to dimension via foreign keys
- Aggregations (SUM, AVG, COUNT) apply to measures; GROUP BY applies to dimension attributes

## Domain Model

This scenario uses a simplified order analytics warehouse:

```
                    ┌──────────────────┐
                    │   dim_customer   │
                    ├──────────────────┤
                    │ id (PK)          │
                    │ customer_name    │
                    └────────▲─────────┘
                             │ FK
┌──────────────────┐         │         ┌──────────────────┐
│    dim_date      │         │         │   fct_orders     │
├──────────────────┤         │         ├──────────────────┤
│ id (PK)          │◄────────┼─────────│ customer_id (FK) │
│ date             │         │         │ date_id (FK)     │
└──────────────────┘         │         │ order_value      │
                             │         └──────────────────┘
                             │                  │
                             └──────────────────┘
```

### Tables

| Table | Type | Description |
|-------|------|-------------|
| `fct_orders` | Fact | Order transactions with value measures |
| `dim_customer` | Dimension | Customer master data |
| `dim_date` | Dimension | Calendar dimension for time analysis |

### Semantic Tags

Columns are tagged with semantic metadata to guide the LLM:

- `pk` — Primary key
- `fk:table.column` — Foreign key reference
- `measure` — Aggregatable numeric value
- `attribute` — Descriptive/categorical value

## Two Query Approaches

This scenario demonstrates that **two fundamentally different approaches** to database querying can coexist within the same application:

### Approach A: LLM-Generated SQL Queries

The LLM produces actual SQL SELECT statements that the application executes directly.

| Natural Language | LLM Output |
|------------------|------------|
| "Show me customer names from dim_customer" | `SELECT customer_name FROM dim_customer` |
| "Run query: select order_value from fct_orders" | `SELECT order_value FROM fct_orders` |

**When to use this approach:**
- The end user needs to **see the query** before execution
- The user may want to **edit or refine** the query
- Queries are **exploratory** and not anticipated at development time
- Maximum **flexibility** is required

**Trade-offs:**
- Requires robust SQL validation (syntax, schema references, injection prevention)
- User must understand SQL to verify correctness
- Harder to optimize or cache at the application layer

### Approach B: Structured Query Objects

The LLM populates a **structured record** (like `OrderValueQuery` with nested `Period`), which the application uses to construct and execute a query in its own way.

| Natural Language | LLM Output |
|------------------|------------|
| "Calculate total order value for Mike in January 2024" | `OrderValueQuery { customer_name: "Mike", period: { start: 2024-01-01, end: 2024-01-31 } }` |

**When to use this approach:**
- The query pattern is **already anticipated** by the developer
- Only **parameterization** is needed, not query design
- The application has a **specific, optimized query** for this use case
- End users don't need to see or understand the underlying SQL
- Query logic can include **business rules** not expressible in simple SQL

**Trade-offs:**
- Less flexible — new query patterns require code changes
- Developer must anticipate all needed query shapes
- More structured and predictable execution

### Coexistence in Practice

A real data warehouse application typically uses **both approaches**:

```
┌─────────────────────────────────────────────────────────────────┐
│                     User Request                                 │
└─────────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┴───────────────┐
              ▼                               ▼
    ┌─────────────────────┐       ┌─────────────────────┐
    │  Ad-hoc Exploration │       │  Standard Reports   │
    │  "show me a query   │       │  "total order value │
    │   for customer      │       │   for Mike in       │
    │   names"            │       │   January"          │
    └─────────────────────┘       └─────────────────────┘
              │                               │
              ▼                               ▼
    ┌─────────────────────┐       ┌─────────────────────┐
    │  Query (SQL String) │       │  OrderValueQuery    │
    │  → showSqlQuery()   │       │  → aggregateOrder   │
    │  → runSqlQuery()    │       │     Value()         │
    └─────────────────────┘       └─────────────────────┘
              │                               │
              ▼                               ▼
    ┌─────────────────────┐       ┌─────────────────────┐
    │  Direct SQL         │       │  Application builds │
    │  execution          │       │  optimized query    │
    └─────────────────────┘       └─────────────────────┘
```

The framework supports both patterns seamlessly — the same `Planner` can route to either `Query`-based actions or structured-record actions based on user intent.

## Framework Features Exercised

### 1. SQL Catalog Context Contribution

The `SqlCatalogContextContributor` injects schema metadata into the system prompt, enabling the LLM to generate valid SQL:

```java
InMemorySqlCatalog catalog = new InMemorySqlCatalog()
    .addTable("fct_orders", "Fact table for orders", "fact")
    .addColumn("fct_orders", "customer_id", "FK to dim_customer", "string",
        new String[] { "fk:dim_customer.id" }, null)
    // ... more schema definition
```

### 2. Validated Query Type (Approach A)

The `Query` type ensures SQL correctness before action execution:

- Parses SQL via JSqlParser
- Rejects non-SELECT statements
- Validates table references against catalog

This supports the **LLM-generated SQL** approach where flexibility is paramount.

### 3. Nested Record Binding (Approach B)

Complex parameters like `OrderValueQuery` with embedded `Period` demonstrate the framework's ability to bind structured LLM output:

```java
record OrderValueQuery(String customer_name, Period period) {}
record Period(LocalDate start, LocalDate end) {}
```

This supports the **structured query object** approach where the developer anticipates the query pattern and the LLM merely parameterizes it. The application then builds the actual query using these parameters, potentially with optimizations, caching, or business logic that wouldn't be expressible in raw SQL.

### 4. Action Differentiation

The scenario includes actions with similar purposes but different effects:

- `showSqlQuery` — Display SQL only (for user review/editing)
- `runSqlQuery` — Execute SQL against database
- `aggregateOrderValue` — Accept structured parameters, application handles query

The LLM must choose correctly based on user intent and the nature of the request.

## Running the Tests

### Prerequisites

```bash
export OPENAI_API_KEY="sk-..."
export RUN_LLM_TESTS=true
```

### Execution

```bash
./gradlew test --tests "*.DataWarehouseApplicationScenarioTest"
```

### Test Characteristics

- Uses `gpt-4.1-mini` model
- Temperature set to 0.0 for deterministic output
- Tests are skipped without env vars (safe for CI)

## Future Enhancements

See `PLAN-SCENARIO-DW.md` for the enhancement roadmap, including:

1. **Tool-based metadata lookup** — Dynamic schema discovery instead of static prompt
2. **Adaptive hybrid approach** — Learn frequently-used schema elements
3. **Expanded query patterns** — JOINs, aggregations, time intelligence
4. **Multi-turn query refinement** — Build on previous queries

