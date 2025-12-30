# Tokenization and Processing Pipeline Design

## Executive Summary

This document outlines a comprehensive approach to handling schema tokenization and building an extensible processing pipeline that protects sensitive organizational data while maintaining LLM effectiveness. The solution addresses compliance requirements for organizations unable or unwilling to expose internal schema details to external LLM providers, while remaining flexible enough to support additional data protection layers (PII detection, guardrails, etc.).

---

## 1. Problem Statement

Organizations with strict data governance requirements face a dilemma:
- They need LLM-powered SQL query generation to improve developer productivity
- They cannot expose internal schema names, table structures, or data context to cloud-based LLMs
- Current implementations either tokenize tables (losing semantic context) or expose full schema details (violating compliance)

Additionally, the pipeline must support multiple concurrent processing layers:
- **Tokenization**: Schema name abstraction
- **PII Detection**: Screening user input for personally identifiable information
- **Future Guardrails**: SQL injection prevention, query complexity limits, hallucination safeguards

These layers must compose cleanly without creating data flow bottlenecks, redundant scans, or information leakage.

---

## 2. Core Architectural Principles

### 2.0 Pluggable Chain of Responsibility Pattern

The entire system is built on a **pluggable chain of responsibility** pattern where:

**Core Concept**: 
- Each processing layer is an optional, swappable component
- Layers pass requests through a chain
- Each layer can transform, validate, or short-circuit the request
- No layer has hard dependencies on other layers
- Configuration determines which layers are active
- New layers can be added without modifying existing code

**Benefits**:
- **Flexibility**: Enable/disable any layer via config
- **Testability**: Test layers in isolation
- **Composability**: Combine layers in any order
- **Extensibility**: Add new layers without touching existing code
- **Organization-Specific**: Each deployment can have different layer combinations
- **A/B Testing**: Run different configurations in parallel

**Example Chains**:
```
Strict Organization:
  Validation → PII Detection(BLOCK) → Schema Tokenization → 
  System Prompt → LLM → Detokenization

Permissive Organization:
  Validation → PII Detection(TOKENIZE) → Schema Tokenization → 
  System Prompt → LLM → PII Detokenization → Schema Detokenization

Internal SLM (No Tokenization Needed):
  Validation → System Prompt → Internal SLM → Result
```

### 2.1 Layered Pipeline Architecture

The system operates on a **composable scanning and transformation pipeline** with the following characteristics:

```
User Input 
  ↓ [Validation Layer]
  ↓ [PII Detection & Masking]
  ↓ [Tokenization Preparation]
  ↓ [System Prompt Assembly]
  ↓ [LLM Invocation]
  ↓ [Detokenization & Conversion]
  ↓ User Output
```

Each layer is:
- **Independent**: Can be enabled/disabled based on deployment configuration
- **Composable**: Layers execute in defined order without interdependencies
- **Observable**: Each layer logs transformations for audit trails
- **Idempotent**: Can be re-run without side effects
- **Reversible**: Where applicable (e.g., detokenization reverses tokenization)

### 2.2 Separation of Concerns

Three distinct concerns drive the design:

1. **Metadata Management** (Static)
   - Schema catalog (tables, fields, descriptions)
   - Token assignments and mappings
   - Versioning and evolution
   
2. **Transformation Layers** (Dynamic)
   - Input sanitization (PII, injection patterns)
   - Name mapping (tokenization)
   - Output reconstitution (detokenization)
   
3. **Orchestration** (Flow Control)
   - Pipeline composition
   - Layer sequencing
   - Error handling and rollback

---

## 3. Schema Metadata Provision (Pluggable Strategy)

### 3.1 Schema Metadata Provider Interface

The system uses a **pluggable strategy pattern** for schema metadata provision, allowing different sources to be used without code modification.

```java
public interface SchemaMetadataProvider {
    /**
     * Load all available tables
     */
    List<TableMetadata> loadAllTables();
    
    /**
     * Get metadata for a specific table
     */
    TableMetadata getTableMetadata(String tableName);
    
    /**
     * Get all fields for a table
     */
    List<FieldMetadata> getFieldsForTable(String tableName);
    
    /**
     * Get metadata for a specific field
     */
    FieldMetadata getFieldMetadata(String tableName, String fieldName);
    
    /**
     * Get field relationships (foreign keys, joins)
     */
    List<FieldRelationship> getRelationships(String tableName);
}

public class TableMetadata {
    String name;
    String description;
    TableType type;  // FACT, DIMENSION
    List<FieldMetadata> fields;
    List<FieldRelationship> relationships;
}

public class FieldMetadata {
    String name;
    String description;
    DataType dataType;
    boolean nullable;
    DescriptionQuality qualityFlag;  // CLEAR, UNCERTAIN, MISSING
}
```

### 3.2 DBT Schema Metadata Strategy

**Primary Implementation**: Load schema from DBT YAML files

**Configuration**:
```yaml
schema_metadata:
  provider: "dbt"
  config:
    schema_file_path: "models/schema.yml"  # Relative to data warehouse root
    data_marts_folder: "models/data_marts"
    validate_relationships: true
    flag_uncertain_descriptions: true
```

**How It Works**:
1. Loads YAML file from configured path
2. Parses `models:` array
3. Extracts table name, description, and columns
4. For each field: extracts name, description, data type
5. Extracts relationships from `tests:` → `relationships:` declarations
6. Flags fields with "(semantics not clear)" descriptions

**Advantages**:
- ✅ Single source of truth (DBT schema)
- ✅ Rich, curated descriptions maintained by data team
- ✅ Already includes relationship information
- ✅ No separate schema documentation needed
- ✅ Updates to schema automatically propagate
- ✅ Includes field data types and constraints

### 3.3 Future Schema Metadata Strategies

Other implementations can be added without code modification:

```java
// CSV-based schema (simpler organizations)
public class CsvSchemaMetadataStrategy implements SchemaMetadataProvider {
    // Load from tables.csv, fields.csv
}

// Database introspection (for legacy systems)
public class DatabaseMetadataStrategy implements SchemaMetadataProvider {
    // Query INFORMATION_SCHEMA / pg_catalog
}

// API-based (for managed metadata services)
public class ApiSchemaMetadataStrategy implements SchemaMetadataProvider {
    // Fetch from metadata API (Collibra, Apache Atlas, etc.)
}

// Composite strategy (blend multiple sources)
public class CompositeSchemaMetadataStrategy implements SchemaMetadataProvider {
    // Try DBT first, fall back to database introspection
}
```

### 3.4 Description Quality Flagging

Since your DBT schema includes descriptions of varying quality:

```
clear:       "Primary key identifier for the elasticity measurement record"
uncertain:   "Standard bushing static/dynamic indicator... (semantics not clear)"
missing:     [no description provided]
```

The system flags these:

```java
public enum DescriptionQuality {
    CLEAR,           // Full, unambiguous description
    UNCERTAIN,       // Description provided but marked unclear
    INCOMPLETE,      // Partial or vague description
    MISSING          // No description provided
}
```

**Usage**:
- CLEAR descriptions → Use in system prompt (LLM can reason well)
- UNCERTAIN → Flag in audit log, use with warning
- INCOMPLETE → Recommend for human review
- MISSING → Flag for schema team to complete

---

## 5. Tokenization Design

### 5.1 Token Generation Strategy

**Principle**: Tokens must be:
- Opaque (reveal nothing about the original name)
- Deterministic (same table always gets the same token)
- Reversible (bidirectional mapping maintained)
- Stable (persist across schema versions)

**Format**: `t<hash>` for tables, `f<hash>` for fields

Example:
```
fct_elasticity          → t_fct_elasticity_hash  (simplified: t846)
elasticity_coefficient  → f_elasticity_coef_hash (simplified: f1203)
```

### 5.2 Token Mapping Store

A versioned metadata store maintains bidirectional mappings:

```yaml
version: "1.0"
schema: "production"
generated_at: "2025-12-15T10:30:00Z"

tables:
  t846:
    original_name: "fct_elasticity"
    description: "Fact table containing elasticity measurements for product demand analysis"
    token_created_at: "2025-12-15T09:00:00Z"
    status: "active"
    
  t847:
    original_name: "dim_product"
    description: "Dimension table for product master data"
    token_created_at: "2025-12-15T09:00:00Z"
    status: "active"

fields:
  t846.f1203:
    original_name: "fct_elasticity.elasticity_coefficient"
    description: "Calculated elasticity coefficient indicating price sensitivity"
    token_created_at: "2025-12-15T09:00:00Z"
    status: "active"
```

### 5.3 Token Lifecycle

**Token Assignment**:
1. Extract schema from source database
2. Generate deterministic tokens for all tables and frequently-queried fields
3. Create mapping store with versioning
4. Publish schema token mappings to deployment environment

**Token Stability**:
- Tokens are immutable once assigned
- New fields receive new tokens when discovered
- Retired tables/fields marked as `deprecated`, not reused
- Version management tracks mapping evolution

**Token Rotation** (Future):
- When security requires remapping, create new version
- Maintain historical mappings for audit trails
- Old tokens remain resolvable for a grace period

### 5.4 Scope of Tokenization

**What Gets Tokenized** (Sent to LLM):
- Table identifiers
- Field identifiers
- Optionally: foreign key relationships
- Optionally: join hints

**What Does NOT Get Tokenized** (Never sent to LLM):
- Actual data values (rows)
- Database connection strings
- User credentials
- System schema metadata

**System Prompt Content** (Tokenized):
```
Available Tables:
- t846: Fact table containing elasticity measurements...
- t847: Dimension table for product master data...
- t848: Dimension table for customer segmentation...

Common Fields:
- t846.f1203: Elasticity coefficient
- t847.f2015: Product category
- t848.f2104: Customer tier
```

---

## 3.5 PII Instance Tokenization

In some cases, completely blocking or masking PII is too limiting. For example, if a user asks "Show records for john@example.com", masking the email loses critical information. Instead, PII instances can be **tokenized**—replaced with semantically neutral but reversible substitutes.

### 3.5.1 PII Tokenization Strategy

**Key Principle**: PII tokens must:
- Be **semantically neutral** (reveal nothing about the original value)
- Be **syntactically valid** (work in SQL, XML, JSON contexts)
- Be **deterministic** (same input always produces same token)
- Be **reversible** (bidirectional mapping)
- Be **unambiguous** (easily identified as tokens in responses)

### 3.5.2 Example: Email Address Tokenization

**Format**: Numeric CompuServe-style email addresses

```
Original:  john.doe@acmecorp.com
Token:     3243443@compuserve.com

Original:  mary.smith@example.org
Token:     8912654@compuserve.com
```

**Why This Works**:
- ✅ The numeric format is obviously artificial (no real CompuServe users)
- ✅ Still a valid email syntax (LLM treats it normally)
- ✅ Deterministic hash of original email produces consistent numeric ID
- ✅ `@compuserve.com` is a reserved marker (unambiguous in responses)
- ✅ Easy to identify in audit logs and validate in detokenization

**Mapping Store**:
```yaml
pii_tokens:
  version: "1.0"
  type: "email"
  
  mappings:
    john.doe@acmecorp.com: "3243443@compuserve.com"
    mary.smith@example.org: "8912654@compuserve.com"
    
  # Reverse lookup for detokenization
  reverse:
    "3243443@compuserve.com": "john.doe@acmecorp.com"
    "8912654@compuserve.com": "mary.smith@example.org"
```

### 3.5.3 PII Tokenization for Other Types

This approach can be extended to other PII patterns:

**Phone Numbers**:
```
Original:  +1-555-123-4567
Token:     +1-555-999-0001
```

**Social Security Numbers**:
```
Original:  123-45-6789
Token:     999-00-0001
```

**Credit Card Numbers**:
```
Original:  4532-1111-2222-3333
Token:     4532-9999-0001-0001
```

**Customer/Account IDs** (if marked as PII):
```
Original:  CUST-987654
Token:     CUST-000001
```

### 3.5.4 Detokenization of PII in Responses

When the LLM includes a PII token in its response, we detect and convert it back:

```
LLM Response:
  "WHERE customer_email = '3243443@compuserve.com'"

Detokenization:
  "WHERE customer_email = 'john.doe@acmecorp.com'"
```

This is safe because:
- Tokens are unambiguous (only `\d+@compuserve.com` are email tokens)
- Reverse mapping is deterministic
- 100% accuracy is achievable through regex validation

### 3.5.5 Scope of PII Tokenization

**What Gets Tokenized**:
- Email addresses identified in user input
- Phone numbers identified in user input
- Social Security Numbers identified in user input
- Other PII patterns as needed
- These appear in LLM requests with token values
- These appear in LLM responses with token values

**What Does NOT Get Tokenized**:
- PII that should be blocked entirely (configured per strategy)
- PII that should be masked (configured per strategy)
- Actual data values returned from queries (only schema+structure is affected)

---

## 6. Processing Pipeline Architecture

### 6.1 Pipeline Stages

#### Stage 1: Input Validation & Normalization
```
Purpose: Prepare user input for downstream processing
Inputs: Raw user natural language query
Outputs: Normalized query string, metadata
Operations:
  - Whitespace normalization
  - Encoding validation
  - Length limits
  - Format consistency
```

#### Stage 2: Security Scanning Layer
```
Purpose: Detect and handle sensitive information
Inputs: Normalized user query
Outputs: Sanitized query, PII detection report
Operations:
  - PII pattern detection (emails, phone numbers, SSNs, etc.)
  - Credential detection (passwords, API keys)
  - SQL injection pattern detection
  - Anomaly detection (large data dumps, etc.)

Actions on Detection (Pluggable Strategy):
  - BLOCK: Reject query if high-risk pattern detected
  - MASK: Replace PII with [MASKED_PII] tokens
  - TOKENIZE: Replace PII with semantic tokens (e.g., 3243443@compuserve.com for emails)
  - ALERT: Log to audit trail for review
  - ALLOW: Pass through (for low-risk or acceptable PII patterns)
  - Quarantine: Store for analysis
  
Note: Which action to take is configurable per PII type and per deployment
```

#### Stage 3: Tokenization Layer
```
Purpose: Convert schema references to tokens
Inputs: Sanitized user query, token mapping store
Outputs: Tokenized query ready for LLM
Operations:
  - Identify schema references (often implicit or natural language)
  - Note: Tokenization primarily applies to LLM OUTPUT, not input
    (User doesn't specify table names if we've done our job well)
  - Prepare tokenized system prompt with table/field descriptions
```

#### Stage 4: System Prompt Assembly
```
Purpose: Construct LLM system prompt with schema information
Inputs: DSL grammar, tokenized schema metadata, guardrails config
Outputs: Complete system prompt
Operations:
  - Load SQL DSL grammar (existing GrammarBackedDslGuidanceProvider)
  - Inject tokenized table names and descriptions
  - Add field information (initial set or hot fields)
  - Append guardrails guidance
  - Version the prompt for reproducibility
```

#### Stage 5: LLM Invocation & Response Handling
```
Purpose: Call LLM and capture response
Inputs: System prompt, user query, model config
Outputs: LLM response (tokenized SQL DSL)
Operations:
  - Invoke configured LLM (cloud or internal SLM)
  - Handle rate limiting and retries
  - Capture full response for audit trail
  - Validate response structure
```

#### Stage 6: Detokenization & Output Conversion
```
Purpose: Convert tokenized response back to production schema
Inputs: Tokenized SQL DSL response, token mapping store
Outputs: Real SQL query (or SQL DSL)
Operations:
  - Parse tokenized SQL DSL from LLM
  - Reverse token mappings (t846 → fct_elasticity)
  - Validate detokenized output
  - Return to user or execute against database
```

### 6.2 Chain of Responsibility Model

The pipeline follows a **Chain of Responsibility** pattern where:
- Each layer is **optional** (can be enabled/disabled independently)
- Each layer is **pluggable** (implementations can be swapped)
- Processing continues through the chain only if enabled
- Each layer can short-circuit the chain (e.g., BLOCK action stops processing)

This enables:
- Testing individual layers in isolation
- A/B testing different layer implementations
- Gradual rollout of new layers
- Different configurations for different deployment environments
- Runtime configuration changes without code modification

### 6.3 Pluggable Strategy Pattern

Each layer supports pluggable strategies for different behaviors:

**Example: PII Detection Strategies**
```java
public interface PiiHandlingStrategy {
    // How should we handle this PII if detected?
    enum Action { BLOCK, MASK, TOKENIZE, ALLOW }
    
    Action getAction(String piiType, PiiMatch match);
    String transform(String value, String piiType);
}
```

Different organizations can choose different strategies:
- **Strict**: BLOCK all PII
- **Permissive**: ALLOW all PII
- **Balanced**: TOKENIZE emails, BLOCK SSNs, MASK phone numbers

**Example: Token Generation Strategies**
```java
public interface TokenizationStrategy {
    // How should we generate tokens?
    Token generateForTable(String originalName);
    Token generateForPii(String originalValue, String piiType);
}
```

Different strategies for different needs:
- **DeterministicHashTokenization**: Same value always gets same token
- **RandomUUIDTokenization**: Different token each time
- **EnvironmentSpecificTokenization**: Tokens vary by environment

### 6.4 Pipeline Composition Configuration

Layers are configured via a composition specification with optional chains:

```yaml
pipeline:
  version: "1.0"
  
  # Optional input validation layer
  input_validation:
    enabled: true
    config:
      max_length: 2000
      encoding: "utf-8"
  
  # Optional security scanning layer
  security_scanning:
    enabled: true
    pii_detection:
      enabled: true
      patterns:
        - type: "email"
          regex: "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
          strategy: "tokenize"        # BLOCK, MASK, TOKENIZE, or ALLOW
          token_format: "numeric_compuserve"
        
        - type: "phone"
          regex: "\\+?1?\\d{3}[-.\\s]?\\d{3}[-.\\s]?\\d{4}"
          strategy: "mask"
          
        - type: "ssn"
          regex: "\\d{3}-\\d{2}-\\d{4}"
          strategy: "block"           # No SSNs allowed
      
      sql_injection_detection:
        enabled: true
        strategy: "block"             # Always block injection attempts
  
  # Optional schema tokenization layer
  schema_tokenization:
    enabled: true
    config:
      mapping_store: "/path/to/token-mappings.yml"
      include_field_tokens: false
  
  # Optional system prompt assembly layer
  system_prompt_assembly:
    enabled: true
    config:
      dsl_grammar: "sql"
      include_hot_fields: true
  
  # LLM invocation (usually always enabled)
  llm_invocation:
    enabled: true
    config:
      model: "gpt-4"
      timeout: 30
  
  # Optional detokenization layer (inverse of tokenization)
  detokenization:
    enabled: true
    config:
      schema_mapping_store: "/path/to/token-mappings.yml"
      pii_mapping_store: "/path/to/pii-mappings.yml"
```

### 6.5 Example Configurations for Different Scenarios

**Scenario A: Strict Compliance (Block Everything)**
```yaml
security_scanning.pii_detection.patterns:
  - type: "email"
    strategy: "block"
  - type: "phone"
    strategy: "block"
  - type: "ssn"
    strategy: "block"
```

**Scenario B: Permissive (Tokenize Everything)**
```yaml
security_scanning.pii_detection.patterns:
  - type: "email"
    strategy: "tokenize"
  - type: "phone"
    strategy: "tokenize"
  - type: "ssn"
    strategy: "tokenize"
```

**Scenario C: Balanced (Mixed Strategies)**
```yaml
security_scanning.pii_detection.patterns:
  - type: "email"
    strategy: "tokenize"        # Useful, keep it
  - type: "phone"
    strategy: "mask"            # Less useful, hide it
  - type: "ssn"
    strategy: "block"           # Too risky, reject
```

**Scenario D: No Security (Minimal)**
```yaml
security_scanning.enabled: false
schema_tokenization.enabled: false
detokenization.enabled: false
```

---

## 7. Data Flow Diagrams

### 7.1 End-to-End Flow (Tokenized Deployment)

```
┌─────────────────────────────┐
│  User Natural Language      │
│  "What products have high   │
│   price elasticity?"        │
└──────────────┬──────────────┘
               │
               ▼
        ┌─────────────────┐
        │ Validation      │
        └─────────┬───────┘
                  │
                  ▼
         ┌──────────────────┐
         │ PII Detection    │
         │ [Scans for       │
         │  sensitive data] │
         └─────────┬────────┘
                   │
                   ▼
    ┌──────────────────────────┐
    │ System Prompt Assembly   │
    │ [Injects Tokenized Schema]
    │                          │
    │ "Available tables:       │
    │  t846: elasticity facts  │
    │  t847: product dims      │
    │  ..."                    │
    └──────────────┬───────────┘
                   │
                   ▼
        ┌─────────────────────┐
        │  LLM Invocation     │
        │  [Cloud/SLM]        │
        │                     │
        │  Sees: Tokens only  │
        │  Returns: Tokenized │
        │  SQL DSL            │
        └─────────────┬───────┘
                      │
                      ▼
         ┌────────────────────────┐
         │ Detokenization         │
         │ t846 → fct_elasticity  │
         │ f1203 → elasticity_coef│
         └──────────────┬─────────┘
                        │
                        ▼
        ┌──────────────────────────┐
        │ Output Validation &      │
        │ Execution               │
        │                         │
        │ Real SQL Query Ready    │
        └──────────────────────────┘
```

### 7.2 Pipeline Architecture (Logical View)

```
┌─────────────────────────────────────────────────────────────┐
│                  Pipeline Orchestrator                       │
└─────────────────────────────────────────────────────────────┘
         │              │              │              │
         ▼              ▼              ▼              ▼
   ┌────────────┐ ┌──────────────┐ ┌──────────┐ ┌─────────┐
   │Validation  │ │Security      │ │Token     │ │Prompt   │
   │            │ │Scanning      │ │ization   │ │Assembly │
   ├────────────┤ ├──────────────┤ ├──────────┤ ├─────────┤
   │-Normalize  │ │-PII Detection│ │-Map      │ │-Load DSL│
   │-Validate   │ │-Injection    │ │  Schema  │ │-Inject  │
   │-Length     │ │-Anomaly      │ │-Tokenize │ │  Tokens │
   └────────────┘ └──────────────┘ └──────────┘ └─────────┘
         │              │              │              │
         └──────────────┴──────────────┴──────────────┘
                        │
                        ▼
         ┌──────────────────────────┐
         │  LLM Interface           │
         │ ┌────────────────────┐   │
         │ │ Cloud LLM          │   │
         │ │ OR                 │   │
         │ │ Internal SLM       │   │
         │ └────────────────────┘   │
         └────────────┬─────────────┘
                      │
                      ▼
         ┌──────────────────────────┐
         │  Response Processing     │
         │ ┌────────────────────┐   │
         │ │ Detokenization     │   │
         │ │ Validation         │   │
         │ │ Format Conversion  │   │
         │ └────────────────────┘   │
         └──────────────────────────┘
```

---

## 8. Metadata Management

### 8.1 Schema Catalog Structure

The system maintains a comprehensive catalog:

```yaml
schema_catalog:
  version: "1.0"
  source_database: "production_postgresql"
  snapshot_date: "2025-12-15"
  
  hot_tables:
    - "t846"  # fct_elasticity
    - "t847"  # dim_product
  
  tables:
    t846:
      original_name: "fct_elasticity"
      description: "Fact table containing elasticity coefficients..."
      fields:
        f1203:
          original_name: "elasticity_coefficient"
          data_type: "DECIMAL(10,6)"
          description: "Calculated elasticity coefficient..."
          nullable: false
          indexed: true
        f1204:
          original_name: "product_id"
          data_type: "BIGINT"
          description: "Foreign key to product dimension..."
          
  relationships:
    - from: "t846.f1204"
      to: "t847.f2001"
      type: "foreign_key"
      description: "Links elasticity facts to products"
```

### 8.2 Versioning Strategy

Maintain multiple versions of token mappings to support:
- **Production**: Currently active tokens
- **Staging**: New mappings under test
- **Archive**: Historical mappings for audit
- **Draft**: In-development changes

Versions are content-addressed (hash-based) for integrity verification.

---

## 9. Integration with Existing System

### 9.1 SQL DSL Integration

The tokenization system extends the existing SQL DSL framework:

1. **DSL Grammar Enhancement**
   - Tokens appear in the tokenized DSL output from LLM
   - Same syntax, just with token identifiers instead of real names
   - Example: `(SELECT (COLUMNS f1203) (FROM t846))`

2. **GrammarBackedDslGuidanceProvider Extension**
   - New provider: `TokenizedGrammarBackedDslGuidanceProvider`
   - Loads both DSL grammar AND token metadata
   - Generates system prompt with tokenized schema references

3. **SqlNodeVisitor Updates**
   - New visitor: `DetokenizingSqlNodeVisitor`
   - Inherits from existing `SqlNodeVisitor`
   - Adds detokenization mapping pass before converting to SQL

### 9.2 System Prompt Assembly

Integration with existing SystemPromptBuilder:

```java
SystemPrompt prompt = new SystemPromptBuilder()
    .withDslGrammar("sql")
    .withTokenization(tokenMappingStore)
    .withPiiDetection(piiConfig)
    .build();
```

---

## 10. Security Considerations

### 10.1 Data Protection

- **In Transit**: Tokens only (never real names sent to LLM)
- **At Rest**: Token mappings stored securely with access controls
- **In Logs**: Scrub real names, log only tokens and PII detection events
- **Audit Trail**: Immutable record of all transformations

### 10.2 Token Mapping Security

- Token mappings are sensitive—they're the decryption key
- Store separately from application logs
- Require authentication to access
- Rotate periodically (generate new tokens, maintain old for grace period)
- Support air-gapped deployments (mappings never leave organization)

### 10.3 Compliance Considerations

For organizations with specific compliance needs:
- **GDPR**: User data never leaves org; mappings maintained securely
- **HIPAA**: PII detection masks protected health information before LLM sees it
- **SOX**: Full audit trail of all transformations
- **Data Residency**: Tokenization enables cloud LLM use without violating residency rules

---

## 11. Extensibility Model

The entire system is built on pluggable strategies and chain of responsibility, making it highly extensible without code modification.

### 11.1 Adding New Processing Layers

The pipeline is designed to accommodate additional layers without breaking existing code:

```java
public interface ProcessingLayer {
    ProcessingResult process(UserQuery query, Context context);
    boolean isEnabled();
    String getName();
    String getDescription();
}

// Example new implementations:
// - PromptInjectionDetectionLayer
// - DataExfiltrationRiskLayer
// - QueryComplexityAnalysisLayer
// - HalluccinationRiskAssessmentLayer
// - RateLimitingLayer
// - CachingLayer
```

Each new layer:
- Implements the `ProcessingLayer` interface
- Is configured via YAML
- Can be enabled/disabled independently
- Can use pluggable strategies for different behaviors
- Integrates into the chain without modifying existing code

### 11.2 Custom PII Handling Strategies

Organizations can implement custom strategies for PII detection and handling:

```java
public interface PiiHandlingStrategy {
    enum Action { BLOCK, MASK, TOKENIZE, ALLOW }
    
    Action decide(String piiType, String value, Context context);
    String transform(String value, String piiType, PiiTokenStore store);
    boolean canDetokenize();
    String detokenize(String token, PiiTokenStore store);
}

// Examples:
// - StrictBlockAllStrategy
// - PermissiveAllowAllStrategy
// - ComplianceAwareStrategy (varies by data classification)
// - ContextAwareStrategy (varies by user role)
```

### 11.3 Custom Token Generation Strategies

Support different tokenization approaches for both schema and PII:

```java
public interface TokenizationStrategy {
    Token generateToken(String originalValue, TokenContext context);
    String detokenize(Token token);
    boolean isDeterministic();
}

// Examples:
// - DeterministicHashTokenization (repeatable)
// - RandomUUIDTokenization (different each time)
// - EnvironmentSpecificTokenization (varies by env)
// - PrefixedTokenization (adds human-readable prefix)
// - CompuServeEmailTokenization (emails → 123456@compuserve.com)
// - CustomNumericPhoneTokenization (phones → +1-555-999-NNNN)
```

### 11.4 Custom Detokenization Strategies

Similarly, different detokenization approaches:

```java
public interface DetokenizationStrategy {
    String detokenize(String response, TokenContext context);
    List<Token> extractTokens(String response);
    boolean validateDetokenizationCompleteness(String response);
}

// Examples:
// - RegexBasedDetokenization
// - PatternMatchingDetokenization
// - AstTraversalDetokenization (for SQL DSL)
// - MultiFormatDetokenization (handles multiple token types)
```

### 11.5 Configuration-Driven Extensibility

New strategies can be added entirely through configuration:

```yaml
# Deploy a new strategy without code changes
pii_handling:
  strategies:
    strict_block:
      class: "org.javai.springai.security.StrictBlockAllStrategy"
      config:
        block_all_pii: true
    
    custom_compliance:
      class: "com.myorg.security.CustomComplianceStrategy"
      config:
        classify_data: true
        use_data_labels: true
```

---

## 12. Deployment Scenarios

### 12.1 Scenario A: High-Compliance Organization (Cloud LLM + Tokenization)

```
Organization's Internal Systems
    │
    └─► [Validation] ─► [PII Detection] ─► [Tokenization]
           │                                    │
           └─ Audit Log ◄─────────────────────┘
                        │
                        ▼
              Cloud LLM Provider
              [Only sees tokens]
                        │
                        ▼
          Organization's Internal Systems
          [Detokenization] ─► [Real Query Execution]
```

### 12.2 Scenario B: Premium Organization (Internal SLM)

```
Organization's Internal Systems
    │
    └─► [Validation] ─► [PII Detection] ─► [Real Names OK]
           │                                    │
           └─ Audit Log ◄─────────────────────┘
                        │
                        ▼
            Internal SLM (On-Premises)
            [Has full schema access]
                        │
                        ▼
          Organization's Internal Systems
          [Real Query Execution]
```

---

## 13. Success Metrics

- **Security**: Zero schema name exposure to external LLMs
- **Performance**: Tokenization overhead < 5ms per request
- **Usability**: System transparent to end users (no UI changes)
- **Maintainability**: Token mappings remain consistent across schema versions
- **Compliance**: Audit trail shows all transformations
- **Reliability**: Detokenization is 100% accurate (automated tests)

---

## 14. Open Questions & Future Enhancements

1. **Token Format**: Should tokens be shorter/longer? Format preference (tNNN vs t_xxxxxx)?
2. **Field Tokenization**: Start without field tokens, or include from day one?
3. **Hot Field Detection**: How do we identify which fields are "hot" for a given organization?
4. **Conflict Resolution**: How do we handle schema name collisions or ambiguities?
5. **Performance Caching**: Should we cache detokenized queries?
6. **SLM Integration**: How does tokenization work differently with internal SLMs?
7. **Multi-Tenant**: How do we support different tokenization schemes per tenant?

---

## 15. Appendix: References to Existing Code

- `GrammarBackedDslGuidanceProvider`: Existing grammar loading mechanism
- `SystemPromptBuilder`: Existing prompt construction (to be extended)
- `SqlNodeVisitor`: Existing SQL DSL visitor (to be subclassed)
- `sxl-meta-grammar-universal.yml`: Universal DSL guidance
- SQL DSL grammar files


