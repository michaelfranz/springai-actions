# PLAN — Action Parameter Value Constraints

## Goal
Allow developers to constrain action parameters beyond type (e.g., enums, whitelists, regex) so the LLM is guided to valid values and the framework enforces them during plan verification/resolution.

## Scope & Principles
- Keep grammars structural only; do value enforcement in action-aware layers.
- Derive constraints from action metadata (annotations, enum types) and surface them in prompts.
- Fail early with clear messages; when recoverable, surface pending items listing allowed options.

## Key Changes (High-Level)
1) **Action metadata**
   - Extend `ActionParameterDescriptor` to carry allowed values and/or allowed regex (derive enum constants automatically). **Done**
   - Extend reflection that builds the `ActionRegistry` (planner builder `addActions`) to populate these fields. **Done** (derives enum constants when no explicit allowedValues).
   - Extend `@ActionParam` (or a new attribute on `@Action`) with optional constraints: `allowedValues`, `allowedRegex`, optional `caseInsensitive`. Defaults = no constraint. These feed into the descriptor. **Done**
2) **Prompt emission**
   - Update `ActionPromptContributor` to include allowed values per parameter (e.g., “allowed: A|B|C”; for regex, emit pattern guidance). **Done** (SXL prompts include Constraints block).
   - Optionally add a short “pending example” showing how to ask for a valid choice. **Already present**
3) **Verification (plan phase)**
   - Enhance `PlanVerifier` to check literal values against allowed sets/regex; produce `ErrorStep` or `PendingActionStep` with a message like “value must be one of [...]”.
4) **Resolution**
   - Extend `DefaultPlanResolver` conversions to handle enums (case-insensitive name match) and validate constrained sets for scalars and collections; on violation, return an error/pending step.
5) **Surfacing to clients**
   - Ensure `ResolvedPlan` exposes pending/invalid parameter info so UIs can prompt users with allowed options.

## TDD / Work Plan
1) **Metadata & prompt**
   - Add tests for `ActionPromptContributor` to assert allowed values are emitted. **Done** (`ActionPromptContributorTest`).
   - Implement metadata changes (descriptor fields, annotation support, registry build). **Done**.
2) **Verification**
   - Add `PlanVerifier` tests: invalid enum value → error/pending; valid value passes.
   - Implement verifier enforcement using metadata.
3) **Resolution**
   - Add resolver tests for enum conversion (scalar/array/collection) and invalid value errors.
   - Implement enum-aware conversion and allowed-set checks.
4) **End-to-end**
   - Scenario test with an action having a constrained param; ensure LLM prompt includes allowed values and pending messaging is clear when invalid/missing.

## Decisions (clarified)
- Invalid value → `ErrorStep` (not pending).
- Annotation API: support `allowedValues`, `allowedRegex`, `caseInsensitive`.
- Prompt verbosity: list full allowed values, even for large enums (optimize later if needed).

## Status Summary
- ActionParam constraints added; enum values derived automatically when not explicitly provided.
- ActionParameterDescriptor and ActionRegistry carry constraints. **Done**
- ActionPromptContributor surfaces constraints in SXL prompts. **Done**
- Tests updated for prompt emission (ActionPromptContributorTest) and constructors (ActionSpecTest, ActionSerializationFootprintTest). **Done**
- Verification and resolution enforcement for value constraints:
  - PlanVerifier value checks **Done**
  - Enum-aware conversion and allowed-set validation in DefaultPlanResolver **Done**
  - Scenario/Resolver tests for invalid/valid constrained values **Partial** (unit coverage in DefaultPlanResolverTest; scenario still pending)

