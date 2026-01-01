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

## Schema Delivery Strategies

The framework supports three strategies for delivering SQL schema information to the LLM, each with distinct trade-offs:

| Strategy | Schema in Prompt | Tools Available | Latency | Best For |
|----------|------------------|-----------------|---------|----------|
| **Static** | Full schema | None | Lowest | Small schemas (<20 tables) |
| **Tool-based** | None | `listTables`, `getTableDetails` | Higher | Large schemas, exploration |
| **Adaptive Hybrid** | Hot tables only | Full tools | Balanced | Production workloads |

### Static Schema Contribution

The entire schema is injected into the system prompt via `SqlCatalogContextContributor`. The LLM has immediate access to all table and column metadata.

```java
Planner.builder()
    .promptContributor(new SqlCatalogContextContributor(catalog))
    .build();
```

**Advantages:**
- Lowest latency — no tool round-trips
- Simplest configuration
- Predictable prompt content

**Disadvantages:**
- Prompt size grows with schema
- All tables consume tokens, even if unused
- Changes require application restart

### Tool-Based Discovery

Schema is not in the prompt. The LLM uses `SqlCatalogTool` to discover tables on demand.

```java
Planner.builder()
    .tools(new SqlCatalogTool(catalog))
    .build();
```

**Advantages:**
- Minimal prompt size
- Scales to large schemas (100+ tables)
- LLM fetches only what it needs

**Disadvantages:**
- Higher latency (tool round-trips)
- More LLM calls per request
- Discovery adds cognitive load to LLM

### Adaptive Hybrid (Recommended for Production)

Combines both approaches: frequently-used tables are promoted to the prompt, while tools remain available for infrequent tables.

```java
Planner.builder()
    .promptContributor(new AdaptiveSqlCatalogContributor(catalog, tracker, threshold))
    .tools(new FrequencyAwareSqlCatalogTool(baseTool, tracker))
    .build();
```

**Advantages:**
- Low latency for common queries (hot tables in prompt)
- Scales to large schemas (cold tables via tools)
- Self-optimizing based on usage patterns

**Disadvantages:**
- More components to configure
- Cold start requires tool calls
- Requires access tracking infrastructure

### Choosing a Strategy

```
┌─────────────────────────────────────────────────────────────────┐
│                    How many tables?                             │
└─────────────────────────────────────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
     < 20 tables        20-100 tables        100+ tables
          │                   │                   │
          ▼                   ▼                   ▼
       Static            Adaptive             Tool-based
                          Hybrid              (or Adaptive)
```

## Adaptive Hybrid Approach

The adaptive hybrid approach combines the best of static schema contribution and tool-based discovery. It starts cold (no schema in prompt) and progressively promotes frequently-used tables to the system prompt, reducing tool call latency for common queries while keeping tools available for infrequent tables.

### Components

| Component | Purpose |
|-----------|---------|
| `SchemaAccessTracker` | Interface for recording table access patterns |
| `InMemorySchemaAccessTracker` | In-memory implementation (suitable for testing, single-instance) |
| `FrequencyAwareSqlCatalogTool` | Wraps `SqlCatalogTool`, records access to tracker |
| `AdaptiveSqlCatalogContributor` | Includes only "hot" tables in the system prompt |

### Configuration

```java
// Create tracker and tools
SchemaAccessTracker tracker = new InMemorySchemaAccessTracker();
SqlCatalogTool baseTool = new SqlCatalogTool(catalog);
FrequencyAwareSqlCatalogTool trackingTool = new FrequencyAwareSqlCatalogTool(baseTool, tracker);

// Configure hot threshold (minimum accesses to include in prompt)
int hotThreshold = 3;  // Tables accessed 3+ times appear in prompt
AdaptiveSqlCatalogContributor contributor = new AdaptiveSqlCatalogContributor(
        catalog, tracker, hotThreshold);

// Wire into Planner
Planner planner = Planner.builder()
        .withChatClient(chatClient)
        .promptContributor(contributor)  // Adaptive schema in prompt
        .tools(trackingTool)             // Tool-based discovery (records access)
        .actions(actions)
        .addPromptContext("sql", catalog)
        .build();
```

### Hot Threshold Configuration

The `hotThreshold` parameter controls when tables are promoted to the system prompt:

| Threshold | Behavior |
|-----------|----------|
| 1 | Aggressive — tables appear after first access |
| 2-3 | Balanced — tables need a few accesses before promotion |
| 5+ | Conservative — only very frequently used tables promoted |

**Choosing a threshold:**
- **Lower threshold** → Faster warm-up, larger prompt over time
- **Higher threshold** → Slower warm-up, leaner prompt, more tool calls

### Lifecycle

```
┌─────────────────────────────────────────────────────────────────┐
│  Phase 1: Cold Start                                            │
│  • Prompt: "No frequently-used tables yet. Use tools..."        │
│  • LLM discovers schema via listTables/getTableDetails          │
│  • Each getTableDetails call records access                     │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Phase 2: Warming Up                                            │
│  • Access counts accumulate: fct_orders=2, dim_customer=1       │
│  • Tables below threshold still require tools                   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Phase 3: Warm State                                            │
│  • Prompt: fct_orders schema (accessed 3+)                      │
│  • dim_customer still via tool (accessed <3)                    │
│  • Hot tables get low-latency, cold tables still discoverable   │
└─────────────────────────────────────────────────────────────────┘
```

### Production Considerations

**In-Memory Tracker:**
- Suitable for: Single-instance deployments, testing, prototyping
- Resets on application restart
- Thread-safe via `ConcurrentHashMap`

**For distributed/persistent tracking:**
- Implement `SchemaAccessTracker` with JDBC, Redis, or other backing store
- Consider time-decay to deprioritize stale access patterns
- Consider per-user vs. global tracking based on use case

### Test Variants

| Test Class | Approach |
|------------|----------|
| `DataWarehouseApplicationScenarioTest` | Static schema in prompt |
| `DataWarehouseToolBasedScenarioTest` | Tool-based discovery only |
| `DataWarehouseAdaptiveHybridScenarioTest` | Adaptive hybrid |

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

## Multi-Turn Query Refinement

The framework supports **multi-turn conversations** where users iteratively refine queries across multiple exchanges. This enables natural interactions like:

```
User: "Show me order values"
AI: [generates SELECT order_value FROM fct_orders]

User: "Add customer names"
AI: [refines to include JOIN with dim_customer]

User: "Filter to just the East region"
AI: [adds WHERE clause while preserving previous structure]
```

### Key Concepts

| Concept | Description |
|---------|-------------|
| `WorkingContext<T>` | Holds the current object being refined (e.g., a Query) |
| `ConversationState` | Tracks context, history, and pending parameters across turns |
| `WorkingContextContributor` | Renders the current context into the system prompt |
| `WorkingContextExtractor` | Extracts new context from executed plan results |

### Conversation Flow

```
┌─────────────────────────────────────────────────────────────────┐
│  Turn 1: "Show me order values"                                  │
└─────────────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────────┐
│  LLM generates: SELECT order_value FROM fct_orders               │
│  → Executed → SqlQueryPayload stored in WorkingContext          │
│  → Serialized to blob → Application stores blob                  │
└─────────────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────────┐
│  Turn 2: "Add customer names" (+ priorBlob)                      │
└─────────────────────────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────────────────────────┐
│  System prompt includes:                                         │
│  "PREVIOUS QUERY CONTEXT: SELECT order_value FROM fct_orders"   │
│  LLM sees prior query and refines it                            │
└─────────────────────────────────────────────────────────────────┘
```

### SQL Working Context Components

The SQL layer provides specialized components for query refinement:

| Component | Purpose |
|-----------|---------|
| `SqlQueryPayload` | Serializable record storing `modelSql` and extracted metadata |
| `SqlWorkingContextContributor` | Renders query context for LLM consumption |
| `SqlWorkingContextExtractor` | Extracts `Query` objects from executed plans |

### Model SQL vs Canonical SQL

The framework distinguishes between two SQL representations:

| Type | Description | Example |
|------|-------------|---------|
| **Model SQL** | SQL as the LLM sees/generates it (uses synonyms) | `SELECT value FROM orders` |
| **Canonical SQL** | SQL for database execution (real table names) | `SELECT order_value FROM fct_orders` |

```java
Query query = Query.fromSql("SELECT value FROM orders", catalog);
query.modelSql();      // "SELECT value FROM orders" (LLM-facing)
query.canonicalSql();  // "SELECT order_value FROM fct_orders" (DB-facing)
```

### Example: Multi-Turn Setup

```java
// Create the conversation manager with blob-based persistence
PayloadTypeRegistry typeRegistry = new PayloadTypeRegistry();
typeRegistry.register("sql.query", SqlQueryPayload.class);

ConversationStateSerializer serializer = new JsonConversationStateSerializer();
ConversationStateConfig config = ConversationStateConfig.builder()
        .maxHistorySize(10)
        .build();

List<WorkingContextExtractor<?>> extractors = List.of(
        new SqlWorkingContextExtractor());

ConversationManager manager = new ConversationManager(
        planner, serializer, typeRegistry, config, extractors);

// First turn
ConversationTurnResult turn1 = manager.converse("Show me order values", null);
byte[] blob1 = turn1.blob();  // Application stores this

// Execute the plan
PlanExecutionResult exec1 = executor.execute(turn1.plan());

// Second turn (with prior blob and execution result)
ConversationTurnResult turn2 = manager.converse("Add customer names", blob1, exec1);
byte[] blob2 = turn2.blob();  // Application stores updated blob
```

## Application Persistence Pattern

The framework uses **application-owned persistence** for conversation state. The framework provides an opaque, versioned, integrity-protected blob; the application stores it however it chooses.

### Why Application-Owned?

- **Flexibility**: Applications can use any storage (SQL, NoSQL, files, session)
- **Control**: TTL, cleanup, and lifecycle are application concerns
- **Simplicity**: Framework has no JDBC/persistence dependencies
- **Security**: Application controls where sensitive data lives

### Blob Format

The serialized blob contains:

```
┌─────────────────────────────────────────────────────────────────┐
│  4 bytes: Magic number "CVST" (identifies blob type)            │
│  2 bytes: Schema version (for migration)                        │
│ 32 bytes: SHA-256 hash (integrity protection)                   │
│  N bytes: Gzip-compressed JSON payload                          │
└─────────────────────────────────────────────────────────────────┘
```

### Persistence Examples

**Database storage:**
```java
// Store blob in database
void saveSession(String sessionId, byte[] blob) {
    jdbcTemplate.update(
        "INSERT INTO sessions (id, state_blob, updated_at) VALUES (?, ?, ?) " +
        "ON CONFLICT (id) DO UPDATE SET state_blob = ?, updated_at = ?",
        sessionId, blob, Instant.now(), blob, Instant.now());
}

// Retrieve blob
byte[] loadSession(String sessionId) {
    return jdbcTemplate.queryForObject(
        "SELECT state_blob FROM sessions WHERE id = ?",
        byte[].class, sessionId);
}
```

**Redis storage:**
```java
// Store with TTL
redisTemplate.opsForValue().set(
    "session:" + sessionId, blob, Duration.ofHours(24));

// Retrieve
byte[] blob = redisTemplate.opsForValue().get("session:" + sessionId);
```

### Integrity Protection

The SHA-256 hash ensures tampered blobs are rejected:

```java
// Tampered blob throws IntegrityException
byte[] tamperedBlob = Arrays.copyOf(validBlob, validBlob.length);
tamperedBlob[50] ^= 0xFF;  // Flip bits

try {
    manager.fromBlob(tamperedBlob);  // Throws!
} catch (ConversationStateSerializer.IntegrityException e) {
    // "Blob integrity check failed - data may have been tampered with"
}
```

### Debugging Blobs

Use `toReadableJson()` to inspect blob contents:

```java
String json = manager.toReadableJson(blob);
System.out.println(json);
// {
//   "originalInstruction": "Show me orders",
//   "workingContext": {
//     "contextType": "sql.query",
//     "payload": { "modelSql": "SELECT ..." }
//   }
// }
```

### Session Expiry

Applications control when sessions expire:

```java
// Force expiry (e.g., on logout)
ConversationTurnResult expired = manager.expire();
byte[] emptyBlob = expired.blob();
saveSession(sessionId, emptyBlob);

// Or simply delete the stored blob
deleteSession(sessionId);
```

## Schema Migration Guide

The framework supports **schema versioning and migration** to handle evolution of the conversation state structure across framework versions.

### When Migrations Are Needed

Migrations are required when:

- Adding new fields to `ConversationState`
- Changing field names or types
- Restructuring nested objects (e.g., `WorkingContext` payload format)

Migrations are **not** needed for:

- Adding new `contextType` values (handled by `PayloadTypeRegistry`)
- Application-specific domain changes (application manages its own data)

### Creating a Migration

1. **Implement `ConversationStateMigration`:**

```java
public class V1ToV2Migration implements ConversationStateMigration {
    
    @Override
    public int fromVersion() { return 1; }
    
    @Override
    public int toVersion() { return 2; }
    
    @Override
    public String description() {
        return "Add turnCount field to conversation state";
    }
    
    @Override
    public void migrate(ObjectNode json) {
        // Add the new field with a default value
        json.put("turnCount", 0);
        
        // Or transform existing data
        if (json.has("oldField")) {
            json.set("newField", json.get("oldField"));
            json.remove("oldField");
        }
    }
}
```

2. **Register with the migration registry:**

```java
ConversationStateMigrationRegistry registry = 
    new DefaultConversationStateMigrationRegistry(2)  // Current version = 2
        .register(new V1ToV2Migration());

JsonConversationStateSerializer serializer = 
    new JsonConversationStateSerializer(registry);
```

3. **Migration executes automatically:**

```java
// Old v1 blob is automatically migrated to v2 on deserialize
byte[] v1Blob = loadFromStorage();
ConversationState state = serializer.deserialize(v1Blob, typeRegistry);
// → V1ToV2Migration.migrate() called automatically
```

### Migration Chain

For multi-version jumps, register all intermediate migrations:

```java
var registry = new DefaultConversationStateMigrationRegistry(4)
    .register(new V1ToV2Migration())  // 1 → 2
    .register(new V2ToV3Migration())  // 2 → 3
    .register(new V3ToV4Migration()); // 3 → 4

// A v1 blob will run: V1ToV2 → V2ToV3 → V3ToV4
```

### Migration Rules

| Rule | Rationale |
|------|-----------|
| Each migration increments version by exactly 1 | Ensures complete chain |
| Migrations must be idempotent-safe | May be re-applied if storage is stale |
| Never remove migrations for shipped versions | Old blobs in the wild need them |
| Test migrations with real v(N) blobs | Catch subtle format differences |

### Versioning Best Practices

```java
// In JsonConversationStateSerializer
public static final int CURRENT_SCHEMA_VERSION = 1;  // Increment for breaking changes

// Version history:
// v1 (2026-01): Initial schema
// v2 (2026-03): Added turnCount field
// v3 (2026-06): Renamed workingContext.sql to workingContext.modelSql
```

### Error Handling

```java
// Missing migration
try {
    serializer.deserialize(oldBlob, typeRegistry);
} catch (ConversationStateSerializer.MigrationException e) {
    // "No migration registered for version 1 -> 2"
}

// Future version (downgrade not supported)
try {
    serializer.deserialize(futureBlob, typeRegistry);
} catch (ConversationStateSerializer.MigrationException e) {
    // "Blob version 5 is newer than current version 3"
}
```

## Future Enhancements

See `PLAN-SCENARIO-DW.md` for the enhancement roadmap:

| Feature | Status |
|---------|--------|
| Tool-based metadata lookup | ✅ Complete |
| Adaptive hybrid approach | ✅ Complete |
| Table/column synonyms | ✅ Complete |
| Schema tokenization (model names) | ✅ Complete |
| Expanded query patterns (JOINs, aggregations) | ✅ Complete |
| Multi-turn query refinement | ✅ Complete |
| Blob persistence with integrity protection | ✅ Complete |
| Schema migration infrastructure | ✅ Complete |
| SQL validation retry mechanism | 🔲 Planned |

