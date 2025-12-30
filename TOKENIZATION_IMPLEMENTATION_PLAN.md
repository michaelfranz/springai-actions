# Tokenization and Processing Pipeline: Implementation Plan

## Executive Summary

This document provides a detailed step-by-step implementation plan for the tokenization and processing pipeline architecture described in `TOKENIZATION_AND_PIPELINE_DESIGN.md`. The plan is organized into phases, with each phase building incrementally on previous work, allowing for early validation and course correction.

**Timeline Estimate**: 4-6 weeks for core implementation (Phases 1-3), additional time for production hardening and integration testing.

---

## Phase 0: Schema Metadata Loading Infrastructure (Weeks 0-0.5)

### Goal
Build the pluggable schema metadata provider abstraction and DBT YAML parser.

### 0.1 Create Schema Metadata Provider Interface

**Tasks**:

#### 0.1.1 Define `SchemaMetadataProvider` Interface
- [ ] Create interface with methods:
  - `loadAllTables()`: Returns list of all tables
  - `getTableMetadata(tableName)`: Get table-specific metadata
  - `getFieldsForTable(tableName)`: Get all fields for a table
  - `getFieldMetadata(tableName, fieldName)`: Get field-specific metadata
  - `getRelationships(tableName)`: Get foreign key relationships

- [ ] Create value objects:
  - `TableMetadata` (name, description, type, fields, relationships)
  - `FieldMetadata` (name, description, dataType, nullable, qualityFlag)
  - `FieldRelationship` (source field, target table, target field)
  - `DescriptionQuality` enum (CLEAR, UNCERTAIN, INCOMPLETE, MISSING)

**Location**: `src/main/java/org/javai/springai/schema/metadata/`

**Tests**: Test interface contracts

---

#### 0.1.2 Implement DBT Schema Metadata Strategy
- [ ] Create `DbtSchemaMetadataProvider` implementing `SchemaMetadataProvider`
  - Takes path to DBT schema YAML file as configuration
  - Parse YAML structure (version, models[], columns[])
  - Extract table names, descriptions from models
  - Extract field names, descriptions from columns
  - Extract relationships from tests[].relationships[]

- [ ] YAML Parsing
  - Use existing SnakeYAML dependency
  - Handle multi-line descriptions (>)
  - Parse tests section for relationships

- [ ] Description Quality Analysis
  - Mark descriptions containing "(semantics not clear)" as UNCERTAIN
  - Mark empty descriptions as MISSING
  - Mark all others as CLEAR

**Location**: `src/main/java/org/javai/springai/schema/metadata/dbt/`

**Tests**: Test parsing with your example DBT schema file

---

#### 0.1.3 Create Schema Caching Layer
- [ ] `CachingSchemaMetadataProvider` decorator
  - Wraps any `SchemaMetadataProvider`
  - Caches results to avoid repeated parsing
  - Optional TTL for cache refresh
  - Configuration-driven

**Location**: `src/main/java/org/javai/springai/schema/metadata/`

**Tests**: Test cache hits and misses

---

### 0.2 Integration Configuration

**Tasks**:

#### 0.2.1 Create Configuration
- [ ] YAML configuration for schema metadata provider
  ```yaml
  schema_metadata:
    provider: "dbt"
    config:
      schema_file_path: "models/schema.yml"
      cache_enabled: true
      cache_ttl_seconds: 3600
  ```

- [ ] Spring Boot integration
  - Create `@Configuration` class
  - Instantiate chosen provider based on config
  - Wire into tokenization system

**Location**: `src/main/java/org/javai/springai/config/`

**Tests**: Test configuration loading and instantiation

---

#### 0.2.2 Validation
- [ ] Validate schema file exists
- [ ] Validate schema file is parseable YAML
- [ ] Validate required fields present
- [ ] Flag description quality issues

**Location**: Tests in validation package

---

**Phase 0 Completion Criteria**:
- ✅ `SchemaMetadataProvider` interface defined
- ✅ `DbtSchemaMetadataProvider` fully functional
- ✅ Parsing works with your example DBT schema
- ✅ Description quality flagging implemented
- ✅ Configuration system in place
- ✅ Ready to be used by tokenization system

---

## Phase 1: Foundation (Weeks 1-1.5)

### 1.0 Core Chain of Responsibility Framework

**Goal**: Build the foundational pluggable processing layer architecture.

**Tasks**:

#### 1.0.1 Create Processing Layer Interface
- [ ] `ProcessingLayer` interface
  ```java
  public interface ProcessingLayer {
      ProcessingResult process(ProcessingRequest request, Context context);
      boolean isEnabled();
      String getName();
      String getDescription();
      int getOrder();  // Determines execution sequence
  }
  ```

- [ ] `ProcessingRequest` and `ProcessingResult` classes
  - Request: user input, context metadata
  - Result: processed data, state changes, continuation flag

**Location**: `src/main/java/org/javai/springai/pipeline/core/`

**Tests**: Test interface contracts

---

#### 1.0.2 Create Pipeline Orchestrator
- [ ] `ProcessingPipeline` orchestrator
  - Maintains ordered list of enabled layers
  - Executes layers in sequence
  - Handles short-circuiting (e.g., BLOCK action)
  - Collects audit trail across all layers

**Location**: `src/main/java/org/javai/springai/pipeline/`

**Tests**: Test layer sequencing, short-circuiting, error propagation

---

#### 1.0.3 Create Configuration Loader
- [ ] `PipelineConfiguration` class
  - Load YAML configuration
  - Specify which layers are enabled
  - Layer-specific configuration
  - Validation (detect conflicts, missing required config)

**Location**: `src/main/java/org/javai/springai/pipeline/config/`

**Tests**: Parse various configs, validate constraints

---

### 1.1 Token Mapping Infrastructure

**Goal**: Build the core token generation and mapping persistence layer.

**Tasks**:

#### 1.1.1 Create Token Mapping Data Structures
- [ ] Create `Token` class (immutable value object)
  - Token ID (e.g., "t846", "f1203")
  - Original name
  - Type (TABLE or FIELD)
  - Creation timestamp
  - Status (ACTIVE, DEPRECATED)
  
- [ ] Create `TokenMapping` class
  - Map<Token, SchemaElement>
  - Map<SchemaElement, Token>
  - Bidirectional lookup capability
  
- [ ] Create `TokenMappingVersion` class
  - Version identifier
  - Created timestamp
  - Status (DRAFT, STAGING, PRODUCTION, ARCHIVED)
  - Hash for integrity verification

**Location**: `src/main/java/org/javai/springai/tokenization/`

**Tests**: Create unit tests for bidirectional mapping accuracy

---

#### 1.1.2 Implement Token Generation Strategy
- [ ] Create `TokenGenerationStrategy` interface
  ```java
  public interface TokenGenerationStrategy {
      Token generateForTable(String originalName);
      Token generateForField(String originalName);
      boolean isDeterministic();
  }
  ```

- [ ] Implement `DeterministicHashTokenization`
  - Use consistent hash of original name + type
  - Deterministic: same input always produces same token
  - Example: hash("fct_elasticity") → "t846"

- [ ] Add configuration to support future strategies

**Location**: `src/main/java/org/javai/springai/tokenization/strategy/`

**Tests**: Verify deterministic behavior; same name always gets same token

---

#### 1.1.3 Create Token Mapping Store
- [ ] Create `TokenMappingStore` interface
  ```java
  public interface TokenMappingStore {
      Token getToken(SchemaElement element);
      SchemaElement resolve(Token token);
      void saveMapping(Token token, SchemaElement element);
      TokenMappingVersion getCurrentVersion();
  }
  ```

- [ ] Implement `FileBasedTokenMappingStore`
  - YAML serialization/deserialization
  - Versioning support
  - Atomic writes with backup

- [ ] Implement `InMemoryTokenMappingStore`
  - For testing and development

**Location**: `src/main/java/org/javai/springai/tokenization/store/`

**Tests**: Test loading, saving, versioning, and recovery

---

#### 1.1.4 Integrate with Schema Extraction
- [ ] Extend existing schema extraction to generate tokens
  - When extracting schema from database, run through `TokenGenerationStrategy`
  - Create initial token mappings
  
- [ ] Create migration tool: `GenerateTokenMappings`
  - Takes: Database connection info
  - Produces: Initial token mappings file
  - Validates: Determinism (running twice produces identical mapping)

**Location**: Tools in `src/main/java/org/javai/springai/tools/`

**Tests**: Run against test database; verify reproducibility

---

### 1.2 Security Scanning Infrastructure

**Goal**: Build the basic framework for security scanning layers with PII detection as first implementation.

**Tasks**:

#### 1.2.1 Create Security Scanning Pipeline
- [ ] Create `SecurityScanningLayer` interface
  ```java
  public interface SecurityScanningLayer {
      ScanResult scan(UserQuery query, Context context);
      boolean isEnabled();
      String getName();
      ScanningLayerConfig getConfig();
  }
  ```

- [ ] Create `ScanResult` value object
  - Original query
  - Sanitized query
  - Issues found (PII, injection patterns, etc.)
  - Confidence levels
  - Recommended actions (mask, block, alert)

- [ ] Create `SecurityScanningPipeline` orchestrator
  - Composes multiple `SecurityScanningLayer` implementations
  - Executes layers in sequence
  - Handles errors and rollback

**Location**: `src/main/java/org/javai/springai/security/`

**Tests**: Test pipeline sequencing and error handling

---

#### 1.2.2 Implement PII Handling Strategy Interface
- [ ] Create `PiiHandlingStrategy` interface
  ```java
  public enum PiiAction { BLOCK, MASK, TOKENIZE, ALLOW }
  
  public interface PiiHandlingStrategy {
      PiiAction getAction(String piiType, String value, Context context);
      String transform(String value, String piiType, PiiTokenStore store);
      boolean canDetokenize();
  }
  ```

- [ ] Implement concrete strategies:
  - `StrictBlockAllStrategy`: BLOCK everything
  - `MaskAllStrategy`: MASK everything
  - `TokenizeAllStrategy`: TOKENIZE everything
  - `ConfigurableStrategy`: Per-PII-type configuration
  - Extensible for custom strategies

**Location**: `src/main/java/org/javai/springai/security/strategy/`

**Tests**: Test each strategy independently

---

#### 1.2.3 Implement PII Detection Layer (with Strategy)
- [ ] Create `PiiDetectionLayer` implementing `ProcessingLayer`
  - Takes a `PiiHandlingStrategy` as dependency
  - Uses strategy to decide action on PII detection
  
- [ ] Define PII patterns (regex-based)
  - Email addresses: `[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}`
  - Phone numbers: Various US/International formats
  - Social Security Numbers: `\d{3}-\d{2}-\d{4}`
  - Credit cards: Luhn algorithm validation
  - Expandable: Add more patterns as needed

- [ ] Support three transformation approaches:
  - **MASK**: Replace with `[MASKED_<TYPE>]`
  - **TOKENIZE**: Replace with semantic token (e.g., email → `3243443@compuserve.com`)
  - **BLOCK**: Reject request entirely

- [ ] Create `PiiTokenStore` for bidirectional mapping
  - PII tokenization mappings (original ↔ token)
  - Persistent or session-scoped
  - Enable detokenization of LLM responses

**Location**: `src/main/java/org/javai/springai/security/layers/`

**Tests**: Test each action (mask, tokenize, block) with various PII types

---

#### 1.2.3 Implement PII Instance Tokenization
- [ ] Create `PiiTokenizationStrategy` interface
  ```java
  public interface PiiTokenizationStrategy {
      String generateToken(String originalValue, String piiType);
      String detokenize(String token, String piiType);
      String getTokenPattern(String piiType);  // For response parsing
  }
  ```

- [ ] Implement concrete tokenization strategies:
  - `NumericCompuServeEmailTokenization`: Email → `NNN@compuserve.com`
  - `NumericPhoneTokenization`: Phone → `+1-555-999-NNNN`
  - `NumericSSNTokenization`: SSN → `999-00-NNNN`
  - `NumericCardTokenization`: Card → `4532-9999-00NN-NNNN`
  
- [ ] Each strategy generates deterministic tokens
  - Hash original value
  - Format as semantic token in target type
  - Support bidirectional lookup

**Location**: `src/main/java/org/javai/springai/security/pii/`

**Tests**: Verify determinism (same input → same token), detokenization accuracy

---

#### 1.2.4 Create Audit Logging
- [ ] Create `SecurityEvent` class
  - Timestamp
  - Layer name
  - Query (with tokens/masks only, never raw PII)
  - Issues detected
  - Action taken (BLOCK, MASK, TOKENIZE, ALLOW)
  - User/session identifier

- [ ] Implement `SecurityEventLogger`
  - Write audit logs to secure location
  - Structured format (JSON)
  - Do NOT log unmasked PII (only tokens/masks)
  - Support audit trail queries
  
- [ ] Integration with pipeline
  - Each layer logs its events
  - Central logger collects events
  - Queryable audit trail

**Location**: `src/main/java/org/javai/springai/security/audit/`

**Tests**: Verify that PII is never logged in plaintext; only tokens appear

---

### 1.3 Integration Point: Validate Token & Security Scanning Work Together

**Goal**: Ensure the token mapping and security scanning layers work with existing code.

**Tasks**:

- [ ] Run existing test suite with token mapping code integrated
  - Confirm no regressions
  - Existing SQL DSL tests still pass

- [ ] Run existing test suite with security scanning enabled
  - Confirm PII detection doesn't break normal queries
  - Test with queries that contain no PII (should pass through unchanged)

- [ ] Create integration test: `TokenizationAndSecurityIntegrationTest`
  - Verify PII detection works
  - Verify token mapping works
  - Both layers together work correctly

**Location**: `src/test/java/org/javai/springai/integration/`

---

**Phase 1 Completion Criteria**:
- ✅ Token generation and bidirectional mapping working
- ✅ PII detection layer functional and well-tested
- ✅ Security event logging implemented
- ✅ No regressions in existing tests
- ✅ Ready to integrate into prompt building pipeline

---

## Phase 2: Pipeline Integration (Weeks 1.5-2.5)

### 2.1 Pipeline Orchestration

**Goal**: Build the central orchestrator that sequences multiple layers.

**Tasks**:

#### 2.1.1 Create Pipeline Configuration
- [ ] Create `ProcessingPipelineConfig` class
  ```yaml
  pipeline:
    version: "1.0"
    layers:
      - name: "input_validation"
        enabled: true
        order: 1
        config:
          max_length: 2000
      - name: "security_scanning"
        enabled: true
        order: 2
        config:
          pii_detection: true
      - name: "tokenization"
        enabled: true
        order: 3
  ```

- [ ] YAML parser for configuration
- [ ] Validation: Detect circular dependencies, missing configs

**Location**: `src/main/java/org/javai/springai/pipeline/config/`

**Tests**: Parse and validate various configurations

---

#### 2.1.2 Create Pipeline Orchestrator
- [ ] `ProcessingPipeline` class
  - Load configuration
  - Instantiate and order layers
  - Execute in sequence
  - Handle errors and recovery
  
- [ ] Error handling strategy
  - If a layer fails (e.g., PII detection times out), what happens?
  - Options: fail fast, skip layer, use cached result
  - Configuration-driven

- [ ] Pipeline execution context
  - Pass context through layers
  - Each layer can read/write metadata
  - Avoid duplicate work between layers

**Location**: `src/main/java/org/javai/springai/pipeline/`

**Tests**: Test various layer combinations; test error scenarios

---

#### 2.1.3 Create Input Validation Layer
- [ ] `InputValidationLayer` extending `ProcessingLayer`
  - Normalize whitespace
  - Validate encoding (UTF-8)
  - Enforce length limits
  - Reject null/empty inputs

**Location**: `src/main/java/org/javai/springai/pipeline/layers/`

**Tests**: Test normalization and validation

---

### 2.2 Extend System Prompt Builder

**Goal**: Integrate tokenization into existing `SystemPromptBuilder`.

**Tasks**:

#### 2.2.1 Create Tokenized Prompt Variant
- [ ] `TokenizedSystemPromptBuilder` extending `SystemPromptBuilder`
  - Takes `TokenMappingStore` as dependency
  - Loads token mappings instead of real schema names
  - Generates table/field descriptions using tokens
  
- [ ] Example output:
  ```
  Available Tables:
  - t846: Fact table for elasticity measurements...
  - t847: Dimension table for product data...
  
  Hot Fields:
  - t846.f1203: Elasticity coefficient
  ```

**Location**: `src/main/java/org/javai/springai/dsl/prompt/`

**Tests**: Compare output with and without tokenization

---

#### 2.2.2 Integration with DSL Grammar
- [ ] Verify existing `GrammarBackedDslGuidanceProvider` works with tokens
  - SQL DSL grammar doesn't know about tokens (shouldn't)
  - Tokens are just identifiers in the DSL output
  
- [ ] Create test: `TokenizedDslGrammarTest`
  - Verify DSL grammar renders correctly with tokenized table/field names

**Location**: Tests in existing test directory

---

### 2.3 Create Detokenization Layer (Schema & PII)

**Goal**: Build component to convert tokenized elements back to originals.

**Tasks**:

#### 2.3.1 Implement Schema Detokenization Visitor
- [ ] `DetokenizingSqlNodeVisitor` extending `SqlNodeVisitor`
  - Takes `TokenMappingStore` as dependency
  - During AST traversal, detokenize table and field references
  - Example: encounters `t846` in FROM clause → resolves to `fct_elasticity`

- [ ] Error handling
  - What if token not found in mapping store?
  - Log warning, fail with clear error message

**Location**: `src/main/java/org/javai/springai/dsl/sql/`

**Tests**: Test detokenization of various SQL constructs

---

#### 2.3.2 Implement PII Detokenization Layer
- [ ] Create `PiiDetokenizationLayer` implementing `ProcessingLayer`
  - Takes `PiiTokenStore` as dependency
  - Processes LLM response looking for PII tokens
  - Uses regex patterns (e.g., `\d+@compuserve.com`) to identify tokens
  - Converts back to original values

- [ ] Detokenization approach:
  - Email: `\d+@compuserve.com` → original email
  - Phone: `\+1-555-999-\d{4}` → original phone
  - Similar patterns for other types
  
- [ ] Error handling
  - Token not found in store? Log and continue (or fail based on config)
  - Multiple detokenization passes (safety measure)

**Location**: `src/main/java/org/javai/springai/pipeline/layers/`

**Tests**: Test detokenization of various PII types in mock responses

---

#### 2.3.3 Create Round-Trip Tests
- [ ] `SchemaTokenizationRoundTripTest`
  - Sample table: "fct_elasticity"
  - Generate token: "t846"
  - Create system prompt with "t846"
  - Mock LLM response: `(SELECT ... (FROM t846))`
  - Detokenize: `(SELECT ... (FROM fct_elasticity))`
  - Verify: matches original

- [ ] `PiiTokenizationRoundTripTest`
  - Original email: john.doe@acmecorp.com
  - Tokenize: 3243443@compuserve.com
  - Send to LLM: "WHERE email = '3243443@compuserve.com'"
  - LLM response: includes token
  - Detokenize: "WHERE email = 'john.doe@acmecorp.com'"
  - Verify: matches original

**Location**: `src/test/java/org/javai/springai/integration/`

---

### 2.4 Integration Testing

**Goal**: Verify the entire pipeline works end-to-end.

**Tasks**:

- [ ] `EndToEndPipelineTest`
  - Real user input: "Show me high elasticity products"
  - Pipeline processes: validation → PII detection → (no tokenization yet)
  - System prompt built with table/field names
  - Verify prompt contains correct schema information

- [ ] Test with PII: "Show SSN 123-45-6789"
  - PII detection masks it
  - Verify masked version goes to LLM

- [ ] Test with tokenization enabled (mock LLM)
  - Mock LLM returns tokenized response
  - Detokenization converts back to real names
  - Verify accuracy

**Location**: `src/test/java/org/javai/springai/integration/`

---

**Phase 2 Completion Criteria**:
- ✅ Pipeline orchestrator working correctly
- ✅ Tokenized system prompt builder functional
- ✅ Detokenization visitor implemented
- ✅ Round-trip tokenization tests passing
- ✅ Integration tests demonstrate full flow
- ✅ Ready for end-to-end testing with real LLM

---

## Phase 3: SQL DSL Integration & Testing (Weeks 2.5-4)

### 3.1 SQL DSL Output Tokenization

**Goal**: Ensure LLM outputs tokenized SQL DSL, and we can correctly detokenize it.

**Tasks**:

#### 3.1.1 Update System Prompt for Tokenized Output
- [ ] Modify `SystemPromptBuilder` to instruct LLM to use tokens
  - "Use table identifiers as provided in the schema (e.g., t846 for elasticity facts)"
  - "Use field identifiers as provided (e.g., f1203 for elasticity coefficient)"
  
- [ ] Test with mock LLM responses
  - Verify LLM is actually producing tokenized output

**Location**: Update in `src/main/java/org/javai/springai/dsl/prompt/`

**Tests**: Create mock LLM that returns tokenized DSL

---

#### 3.1.2 Comprehensive Detokenization Testing
- [ ] Test detokenization with various SQL DSL constructs:
  - Simple SELECT: `(SELECT (COLUMNS f1203) (FROM t846))`
  - Joins: `(JOIN t846 t847 (ON ...))`
  - WHERE clauses with field references
  - GROUP BY with tokenized fields
  - ORDER BY with tokenized fields
  
- [ ] Error cases:
  - Unknown token (should fail gracefully)
  - Malformed token format
  - Circular token reference

- [ ] Performance:
  - Tokenization should be < 1ms
  - Detokenization should be < 1ms

**Location**: `src/test/java/org/javai/springai/dsl/sql/DetokenizingSqlNodeVisitorTest.java`

---

### 3.2 Production-Ready Token Mapping

**Goal**: Move from simple in-memory mappings to production-grade persistent storage.

**Tasks**:

#### 3.2.1 Production Token Mapping Store
- [ ] `FileBasedTokenMappingStore` enhancements
  - Atomic writes (write to temp file, then rename)
  - Backup on every write
  - Transaction log for audit
  - Integrity checking (checksums)

- [ ] Support multiple storage backends
  - File system (YAML)
  - Database (optional, for larger organizations)
  - Version control friendly format

**Location**: `src/main/java/org/javai/springai/tokenization/store/`

**Tests**: Test atomicity, backup recovery, integrity

---

#### 3.2.2 Token Mapping Versioning
- [ ] Version management
  - Track all historical versions
  - Migrate between versions (old tokens → new tokens)
  - Grace period where old tokens still work
  
- [ ] Schema evolution handling
  - New table added: generate new token
  - Table renamed: old token deprecated, new token created
  - Field added to existing table: generate new field token

**Location**: `src/main/java/org/javai/springai/tokenization/versioning/`

**Tests**: Test migration scenarios, backward compatibility

---

#### 3.2.3 Token Mapping Export/Import
- [ ] Export token mappings for deployment
  - Command-line tool: `java ... GenerateTokenMappings`
  - Outputs: YAML file with all table/field tokens
  
- [ ] Import into application
  - Read mappings on startup
  - Validate completeness (all tables covered)
  - Fail fast if mappings missing

**Location**: Tools in `src/main/java/org/javai/springai/tools/`

**Tests**: Test export/import round-trip

---

### 3.3 Security Hardening

**Goal**: Ensure token mappings and sensitive data are protected.

**Tasks**:

#### 3.3.1 Encryption for Sensitive Data
- [ ] (Optional but recommended) Encrypt token mapping files at rest
  - Use organization's key management system
  - Reversible: decryption needed at runtime
  
- [ ] Access control
  - Who can read token mappings?
  - Who can modify?
  - Audit log all access

**Location**: `src/main/java/org/javai/springai/security/`

**Tests**: Test encryption/decryption, access control

---

#### 3.3.2 Audit Trail
- [ ] Complete audit trail of all transformations
  - User input (masked if needed)
  - Security scanning results
  - Tokenization (input tokens → output tokens)
  - Detokenization (output tokens → real names)
  - Timestamp and user/session info
  
- [ ] Immutable audit log
  - Append-only
  - Cryptographic signing (optional)
  - Retention policy configurable

**Location**: Extend existing in `src/main/java/org/javai/springai/security/audit/`

**Tests**: Verify audit trail completeness and immutability

---

### 3.4 Comprehensive Integration Tests

**Goal**: Test the full system with realistic scenarios.

**Tasks**:

#### 3.4.1 Tokenized End-to-End Test
- [ ] `TokenizationEndToEndTest`
  - Real token mappings
  - Real security scanning
  - Real system prompt with tokens
  - Mock or real LLM
  - Verify output is correct SQL query

**Location**: `src/test/java/org/javai/springai/integration/`

---

#### 3.4.2 Multi-Scenario Test Suite
- [ ] Scenario 1: High-compliance organization
  - All security layers enabled
  - Tokenization enabled
  - Audit logging enabled
  
- [ ] Scenario 2: Low-compliance organization
  - Security layers disabled
  - Tokenization disabled
  - Verify system still works

- [ ] Scenario 3: Mixed
  - Some features enabled, some disabled
  - Verify compatibility

**Location**: `src/test/java/org/javai/springai/integration/`

---

**Phase 3 Completion Criteria**:
- ✅ Tokenized SQL DSL output working with detokenization
- ✅ Production-grade token mapping store
- ✅ Versioning and schema evolution handled
- ✅ Security hardening in place
- ✅ Comprehensive audit trail
- ✅ Integration tests pass for all scenarios
- ✅ Ready for deployment to real organizations

---

## Phase 4: Documentation & Hardening (Weeks 4-6)

### 4.1 User Documentation

**Goal**: Help organizations set up and use the system.

**Tasks**:

- [ ] `TOKENIZATION_USER_GUIDE.md`
  - How to generate token mappings from your database
  - How to configure the pipeline
  - How to understand audit logs
  - Troubleshooting guide

- [ ] `TOKENIZATION_DEPLOYMENT_GUIDE.md`
  - How to deploy tokenized system
  - How to generate fresh tokens for each environment
  - How to rotate tokens securely

- [ ] `TOKENIZATION_FAQ.md`
  - Common questions
  - Performance impacts
  - Security considerations

**Location**: Create in workspace root

---

### 4.2 Performance Optimization

**Goal**: Ensure system is production-ready.

**Tasks**:

- [ ] Profile all layers
  - Identify bottlenecks
  - Optimize hot paths
  
- [ ] Caching strategies
  - Cache token mappings in memory (with TTL)
  - Cache detokenized queries
  
- [ ] Batch operations
  - Support batch tokenization
  - Support batch detokenization

**Location**: Performance tests in test suite

---

### 4.3 Configuration Management

**Goal**: Make the system easy to configure for different organizations.

**Tasks**:

- [ ] External configuration files
  - `tokenization-config.yml`
  - `security-scanning-config.yml`
  - `pipeline-config.yml`
  
- [ ] Environment-specific configs
  - Development, staging, production
  - Supports overrides via environment variables

**Location**: `src/main/resources/`

---

### 4.4 Extended Test Coverage

**Goal**: Ensure reliability and maintainability.

**Tasks**:

- [ ] Edge case testing
  - Empty queries
  - Very long queries
  - Special characters
  - SQL injection attempts
  - Malformed tokenization
  
- [ ] Stress testing
  - 1000+ queries/second through pipeline
  - Verify no data leaks under load
  - Memory stability over time
  
- [ ] Regression testing
  - Maintain test suite from Phase 1-3
  - Add new tests for discovered bugs
  - Target >85% code coverage

**Location**: `src/test/java/`

---

**Phase 4 Completion Criteria**:
- ✅ Comprehensive documentation
- ✅ Performance acceptable for production
- ✅ Configuration management in place
- ✅ >85% test coverage
- ✅ System ready for customer deployments

---

## Phase 5: Optional Enhancements (Future)

### 5.1 Hot Field Detection
- [ ] Track which fields are frequently requested
- [ ] Automatically add hot fields to system prompt
- [ ] Reduce latency for common queries

### 5.2 Multi-Tenant Support
- [ ] Support different tokenization schemes per tenant
- [ ] Isolation of token mappings
- [ ] Per-tenant audit trails

### 5.3 Advanced Guardrails
- [ ] SQL injection prevention
- [ ] Query complexity limiting
- [ ] Hallucination risk scoring

### 5.4 SLM Integration
- [ ] Support for internal SLM deployment
- [ ] Token mapping management for SLM
- [ ] Performance comparison: cloud LLM vs. SLM

---

## Implementation Notes

### Technology Stack
- **Language**: Java 17+
- **Parsing**: Existing ANTLR parser for SQL DSL
- **Configuration**: YAML (existing SnakeYAML dependency)
- **Testing**: JUnit 5, Mockito
- **Logging**: Existing SLF4J/Logback

### Key Design Patterns
- **Strategy Pattern**: TokenizationStrategy, SecurityScanningLayer
- **Chain of Responsibility**: ProcessingPipeline
- **Visitor Pattern**: DetokenizingSqlNodeVisitor
- **Value Objects**: Token, TokenMapping, ScanResult
- **Factory Pattern**: SecurityScanningLayerFactory

### Code Organization

```
src/main/java/org/javai/springai/
├── tokenization/
│   ├── Token.java
│   ├── TokenMapping.java
│   ├── TokenMappingStore.java
│   ├── strategy/
│   │   └── TokenizationStrategy.java
│   ├── store/
│   │   ├── FileBasedTokenMappingStore.java
│   │   └── InMemoryTokenMappingStore.java
│   └── versioning/
│       └── TokenVersionManager.java
├── security/
│   ├── SecurityScanningLayer.java
│   ├── SecurityScanningPipeline.java
│   ├── ScanResult.java
│   ├── layers/
│   │   └── PiiDetectionLayer.java
│   └── audit/
│       ├── SecurityEvent.java
│       └── SecurityEventLogger.java
├── pipeline/
│   ├── ProcessingPipeline.java
│   ├── ProcessingPipelineConfig.java
│   ├── layers/
│   │   ├── ProcessingLayer.java
│   │   └── InputValidationLayer.java
│   └── config/
│       └── PipelineConfigLoader.java
└── dsl/
    ├── prompt/
    │   └── TokenizedSystemPromptBuilder.java
    └── sql/
        └── DetokenizingSqlNodeVisitor.java
```

### Dependencies to Add
- None (leverage existing): YAML parsing, SQL parsing already available

### Compatibility Notes
- All changes are backward compatible
- Existing code paths remain unchanged
- New functionality is opt-in via configuration

---

## Risk Mitigation

### Risk 1: Token Mapping Corruption
- **Mitigation**: Atomic writes, versioning, integrity checks
- **Test**: `TokenMappingStoreCorruptionTest`

### Risk 2: Performance Degradation
- **Mitigation**: Caching, profiling, optimization early
- **Test**: `PerformanceBenchmarkTest`

### Risk 3: PII Leakage
- **Mitigation**: Comprehensive scanning, audit logging, never log PII
- **Test**: `PiiLeakagePrevention`

### Risk 4: Detokenization Errors
- **Mitigation**: Extensive round-trip testing, clear error messages
- **Test**: `DetokenizationAccuracyTest`

---

## Success Criteria - Overall

- ✅ Tokenization system reduces schema exposure to zero (only tokens sent to LLM)
- ✅ No performance regression (< 5ms overhead per request)
- ✅ PII detection catches 95%+ of common PII patterns
- ✅ Detokenization is 100% accurate (automated tests verify)
- ✅ Audit trail captures all transformations
- ✅ System is deployable and configurable by customers
- ✅ Documentation enables self-service for organizations

---

## Appendix: Quick Command Reference

### Generate Token Mappings
```bash
java -cp target/classes org.javai.springai.tools.GenerateTokenMappings \
  --database-url jdbc:postgresql://localhost:5432/mydb \
  --database-user admin \
  --output-file token-mappings.yml
```

### Validate Token Mappings
```bash
java -cp target/classes org.javai.springai.tools.ValidateTokenMappings \
  --mapping-file token-mappings.yml
```

### Run Tokenization Tests
```bash
mvn test -Dtest=*Tokenization*
```

### View Audit Log
```bash
tail -f logs/security-audit.log
# Or
cat build/tmp/audit-trail.json | jq '.'
```


