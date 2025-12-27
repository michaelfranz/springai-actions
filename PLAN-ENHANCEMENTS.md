# Enhancement Plan

This document captures gaps and proposed steps to evolve the framework and domain scenarios.

## Framework gaps and steps

1) ✅ Tool & action observability + payload augmentation  
   - Unified invocation events (tools + actions), PII tokenizing augmentor, manual emit helper; demonstrated in `ProtocolNotebookScenarioTest`.

2) Prompt/persona shaping  
   - Provide a structured way to supply assistant persona and scenario-specific guidance (beyond ad hoc prompt contributions).  
   - Ensure persona can be composed with DSL guidance and tool descriptions.

3) Validation/guardrails  
   - Introduce structured validators for action inputs (beyond regex/allowedValues).  
   - Return rich, user-presentable reasons for rejection; consider a validation result type.

4) Idempotency/dedupe  
   - Add support to mark actions as idempotent or dedupe-able; provide a helper to suppress duplicate “add” steps in a plan/conversation.

5) Session/context lifecycle  
   - Standardize start/end lifecycle hooks; document freeze semantics.  
   - Add optional context persistence/restore API and namespacing for multi-session/tenant.

6) Observability  
   - Add structured logging/metrics around plan resolution, tool usage, action execution, and context mutations.

7) Error handling & partial failures  
   - Provide patterns for partial success and recoverable errors (surface back to planning or user); avoid fail-fast when desirable.

## Shopping-domain gaps and steps

1) Basket/session model  
   - Define a basket with line items, prices, currency, discounts, taxes, and states (open/checked-out/cancelled).  
   - Store basket in context with clear transitions.

2) Domain actions/tools  
   - Add actions for inventory search/selection, add/update/remove items, apply/remove offers, compute totals (tax/discount), checkout, cancel, feedback.  
   - Add tools for inventory/offers/price lookup (mocked in tests), returning structured data.

3) Pricing/stock/policy rules  
   - Implement stock validation, tax/discount calculation, age restrictions, per-item limits, spend limits (mock rules for tests).

4) Persona/prompt content  
   - Craft a shopping assistant persona (tone, upsell rules, when to ask for quantity/flavor, when to summarize).  
   - Compose persona with framework prompts and tool descriptions.

5) Localization/currency  
   - Support currency/locale formatting in totals and line items; include currency in basket model.

6) Analytics/UX hooks  
   - Add domain-level logging for basket conversion, offer uptake, session outcomes; keep minimal in tests.

## Next incremental steps (suggested order)

1) Framework: add persona/prompt structuring helper.  
2) Framework: add validation result type for actions; allow actions to return structured validation errors.  
3) Domain: introduce a simple Basket model (items, currency, total) and refactor shopping actions to use it.  
4) Domain: add mock tools for inventory/offers/pricing and actions for computeTotal/checkout that use the basket.  
5) Tests: expand shopping scenario to cover offer selection, add/update/remove, compute total, checkout, feedback.  
6) Optional: add context persistence/restore API and document freeze semantics.  
7) Optional: add idempotency/dedupe helpers for repeated “add” intents.

