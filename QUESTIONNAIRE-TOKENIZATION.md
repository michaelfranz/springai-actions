# Database Object Tokenization — Design Questionnaire

This questionnaire captures design decisions for the database object tokenization feature. Please provide answers below each question.

---

## 1. Token Generation Strategy

### 1.1 How should tokens be generated?

Options:
- **Sequential**: `ft01`, `ft02`, `ft03`... (simple, predictable)
- **Hash-based**: Deterministic hash of real name (reproducible across sessions)
- **Random**: Cryptographically random (maximum obfuscation)
- **Prefix-based**: Type prefix + sequence, e.g., `tbl_01` for tables, `col_01` for columns

**Answer**: Hash-based, but with a simple prefix to easily distinguish the object type

---

### 1.2 Must the same table always map to the same token?

Options:
- **Stable per catalog instance**: Same catalog configuration always produces same tokens
- **Stable per session**: Tokens regenerated on each application restart
- **Stable globally**: Tokens persisted and never change

**Answer**: Stable per catalog instance

---

### 1.3 Where should the token mappings be stored?

Options:
- **In-memory only**: Generated at catalog creation, lost on restart
- **Persisted with catalog**: Stored alongside schema metadata
- **External configuration**: Separate mapping file/database

**Answer**: In memory

---

## 2. Column Token Scoping

### 2.1 Are column tokens globally unique or scoped per table?

Options:
- **Globally unique**: `fct_orders.id` → `c01`, `dim_customer.id` → `c02` (different tokens)
- **Table-scoped**: Both `id` columns → `c01`, but qualified as `ft01.c01` vs `dc03.c01`

**Answer**: table-scoped

---

### 2.2 How should the LLM understand that two tables share a relationship?

Example: `fct_orders.customer_id` is a FK to `dim_customer.id`

Options:
- **Semantic description**: "This column references the customer dimension's primary key"
- **Tokenized FK reference**: `fk:dc03.c01` (token-based relationship)
- **Relationship tokens**: Separate relationship identifiers like `rel01`

**Answer**: FK references are unambiguous declarations of these relationships, but the intent of the relationship will under normal circumstances be backed by prose in the cataloged item's description.
More context: DBT's schema objects likely be used to source this kind of metainformation; but this will be down to the project that uses the framework. 

---

## 3. SQL Parsing and Alias Handling

### 3.1 How should table aliases be handled during de-tokenization?

Example LLM output: `SELECT t.c01, d.c02 FROM ft01 t JOIN dc03 d ON t.c03 = d.c01`

Options:
- **Parse and track aliases**: Use SQL parser to resolve alias → token → real name
- **Require no aliases**: Instruct LLM to always use full token names
- **Allow aliases with heuristics**: Best-effort resolution

**Answer**: Parse and track aliases.

---

### 3.2 Should the framework validate column references against the catalog?

(This relates to FWK-WEAK-002)

Options:
- **Yes, after de-tokenization**: Validate real column names exist
- **Yes, before de-tokenization**: Validate token column names exist in token map
- **Both**: Validate tokens, then validate real names after conversion
- **No**: Trust the LLM and let database handle errors

**Answer**: Yes, before de-tokenization. Reason: if validation only fails AFTER detokenisation, then something has gone wrong with the tokenisation. This is a failure of the framework's implementation.
But as long as tokenisation is working solidly, then validation after detokenisation is merely reassurance and likely overly defensive. We will still find out if the detokenisation has failed if the query fails against the database because a table or field does not exist. 

---

## 4. Error Handling and Reporting

### 4.1 Which names should appear in error messages?

Options:
- **Token names only**: Consistent with what LLM sees
- **Real names only**: Developer-friendly
- **Both**: "Unknown token 'ft99' (no mapping to real table)"

**Answer**: Both

---

### 4.2 What should happen if the LLM returns an unknown token?

Example: LLM returns `SELECT * FROM ft99` but `ft99` is not in the token map.

Options:
- **Reject immediately**: Return error plan
- **Attempt fuzzy match**: Suggest closest token
- **Ask for clarification**: Return PENDING state

**Answer**: An unknown token name means one of two things: Either guidance is wrong or there is a fault in the tokenisation implementation. Both of these circumstances point to immediate rejection.

---

### 4.3 Should there be a retry mechanism with corrective guidance?

Options:
- **No retry**: Single attempt, fail on error
- **Retry with token list reminder**: Re-prompt with available tokens
- **Retry with specific correction**: "ft99 is not valid; did you mean ft01?"

**Answer**: Only experience will show if a retry mechanism is required. Our system is designed on the principle that LLMs have had deep training on SQL grammar no matter the names of the objects involved.
If we have provided unambiguous database metadata then the LLM has been given every chance to deliver a valid SELECT statement. For the time being we will assume that re-tries will not necessary.

This does nevertheless raise a highly important question: How to we handle user requests, which references database objects which do not exist at all? 
Scenario: The user requests a list of, say, planets. Yet the database has no planet table. We want to respond to the end user with a message like: "Sorry, the system has no data on planets." 
This is a situation the LLM must recognise and response in a well-defined manner to. It will look for "planets" in the descriptions of the tables in the catalog that has been provided. It will not find anything.
And so when it returns, what should we put in the Query object? I do not currently have an answer to this question, but clearly it cannot be a validated query. 
What this suggests is that we must enhance the Query type so it can provide one of two responses: A legitimate query or a message indicating why a query could not be formulated.

---

## 5. Prompt Contribution

### 5.1 How should the tokenized schema appear in the system prompt?

Example current format (real names):
```
SQL CATALOG:
- fct_orders: Fact table for orders [tags: fact]
  • customer_id (type=string; FK to dim_customer; tags=fk:dim_customer.id)
```

Options:
- **Token names with descriptions**: `ft01: Fact table for orders`
- **Token names only, no descriptions**: `ft01` (maximum obfuscation)
- **Grouped by type**: Facts section, Dimensions section

**Answer**: We MUST include descriptions for the LLM to be able to connect the user's input with the tables. Grouping by type will likely help the LLM to make the correct decisions on joining fact tables to dimension tables, especially when the guidance makes it plain that it's dealing with a start schema.  

---

### 5.2 Should relationship hints use token names?

Example: Column tag `fk:dim_customer.id`

Options:
- **Tokenize FK references**: `fk:dc03.c01`
- **Use semantic description instead**: "References the customer dimension's primary key"
- **Hybrid**: Both tokenized reference and description

**Answer**: Hybrid

---

## 6. Pipeline and Processing Order

### 6.1 Confirm the proposed processing pipeline:

```
LLM Response (tokenized SQL)
    ↓
Step 1a: Verify single SQL statement
    ↓
Step 1b: Verify SELECT statement
    ↓
Step 1c: Verify valid ANSI SQL syntax
    ↓
Step 2: De-tokenize table/column names
    ↓
Step 3: Validate schema references (tables, columns)
    ↓
Step 4: Convert to target dialect
    ↓
Query object ready for application
```

Is this order correct?

**Answer**: Agree, but not that Step 2 is also a form of validation, while "validation" in step 3 is an additional, defensive form of validation. I support its inclusion because it will only serve to make the framework even more solid, but strictly speaking it should not be necessary once testing has validate the tokenisation mechanism.  

---

### 6.2 Should validation (step 3) happen before or after de-tokenization?

Options:
- **After**: Validate real names against real catalog
- **Before**: Validate tokens against token map (catch LLM errors earlier)
- **Both**: Validate tokens first, then validate real names

**Answer**: Both.

---

## 7. Logging and Debugging

### 7.1 Should the framework log both tokenized and de-tokenized SQL?

Options:
- **Development mode only**: Both versions in debug logs
- **Always**: Both versions at different log levels
- **Tokenized only**: Never expose real names in logs
- **De-tokenized only**: Real names for debugging

**Answer**: Both

---

### 7.2 Should Query expose the original tokenized SQL for debugging?

Options:
- **Yes**: `query.tokenizedSql()` method
- **No**: Only de-tokenized SQL available
- **Optional**: Configurable per catalog

**Answer**: Yes

---

## 8. Performance Considerations

### 8.1 How large might the token mappings be?

Estimate:
- Typical number of tables: ___
- Typical number of columns per table: ___
- Maximum expected: ___

**Answer**: 
Typical number of tables: Application-dependent, but in the specific example I have to mind around 30 tables, 10 of which are fact tables and the test dimension tables
Typical number of columns per table: Fact tables necessarily typically 1-2 double values and a foreign key references to most of the dimension tables. Dimension tables can themselves have anything from 5-20 columns.

---

### 8.2 Is token lookup performance critical?

Options:
- **Yes**: Use optimized data structures (hash maps)
- **No**: Simple linear lookup is acceptable

**Answer**: Hash maps

---

## 9. Optional/Advanced Features

### 9.1 Should tokenization be optional per catalog?

Options:
- **Yes**: `catalog.withTokenization(true/false)`
- **No**: Always tokenize if catalog configured for it
- **Per-table**: Some tables tokenized, others not

**Answer**: Optional, because it will not be needed at all when a locally hosted model is in use. It will also not needed if an organisation does not care about revealing schema information details.   

---

### 9.2 Should there be a "passthrough" mode for development?

In passthrough mode, real names are used everywhere (no tokenization) for easier debugging.

Options:
- **Yes**: Configurable via flag
- **No**: Always use tokenization if configured

**Answer**: This is the same as switching tokenization on or off with the 'withTokenization' option on the catalog. We do not require a second mechanism for this toggle. 

---

## 10. Additional Notes

Please add any additional requirements, constraints, or considerations not covered above:

**Notes**: 

---

## Summary of Decisions

| Area | Decision |
|------|----------|
| Token generation | Hash-based with semantic prefixes: `ft_` (fact), `dt_` (dimension), `c_` (column) |
| Token stability | Stable per catalog instance (same config → same tokens) |
| Token storage | In-memory only |
| Column scoping | Table-scoped (columns qualified as `ft_xxx.c_yyy`) |
| FK relationships | Hybrid: tokenized references + prose descriptions |
| Alias handling | Parse and track aliases using SQL parser |
| Column validation | Before de-tokenization (validate tokens exist in map) |
| Error messages | Both token and real names |
| Unknown tokens | Immediate rejection |
| No matching data | Return `PlanStep.PendingActionStep` (handled at action layer) |
| Retry mechanism | Not required initially; may revisit based on experience |
| Prompt format | Grouped by type (FACT TABLES, DIMENSION TABLES) with descriptions |
| Pipeline order | 1a→1b→1c→2(de-tokenize)→3(validate)→4(dialect) |
| Logging | Both tokenized and de-tokenized SQL |
| Query.tokenizedSql() | Yes, expose for debugging |
| Tokenization toggle | Optional via `catalog.withTokenization(true/false)` |
| Performance | Hash maps for token lookup |

---

*Document created: 2024-12-30*
*Last updated: 2024-12-30*

