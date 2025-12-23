# Plan: Curried Plan Steps and PENDING Handling

## Goal
Stop emitting empty/guessed values for required params. Instead, treat each plan step like a curried function: if required args are missing/invalid/unclear, produce a structured `PENDING` artifact, elicit just what’s missing from the user, merge it, and resume to an executable plan.

## Core Concepts
- **Universal guardrail (minimal):** Never guess; never emit empty strings for required params. If required info is unknown, emit `PENDING`.
- **Plan DSL `PENDING` (rich/typed):** Attach to a specific step/action/param with reason/kind. Presence of any `PENDING` blocks that step (and optionally the whole plan) from execution.
- **Kinds:** `missing_required`, `clarification`, `disambiguation`, `consent`, `invalid_value`.
- **Shape (Plan DSL):**  
  `(PENDING (stepId s1) (action exportControlChartToExcel) (param bundleId) (kind missing_required) (reason "bundleId required to export control chart") (prompt "Provide bundleId"))`

## High-Level Flow
1) User asks; planner parses.  
2) If a step lacks required info or has invalid/uncertain input, emit `PENDING` instead of an executable action.  
3) Runtime detects `PENDING`, halts execution, and surfaces a concise ask built from pending data.  
4) User replies with just the missing piece.  
5) Planner re-runs with prior context + new info; `PENDING` resolves to an executable `PS`.  
6) Execute when no pendings remain.

## TDD Scenarios (extend `StatsApplicationScenarioTest`)
- `requireMoreInformation_missingBundleId`: first turn missing `bundleId` → plan has `PENDING` for `bundleId`; no executable action; assistant message guides user.
- `requireMoreInformation_invalidBundleId`: bundle is malformed (e.g., “bundle ???”) → `PENDING(kind=invalid_value)` with reason; no execution.
- `requireMoreInformation_disambiguation`: ambiguous entity (“control chart for housing”) → `PENDING(kind=disambiguation)` offering options.
- `requireMoreInformation_consent`: risky action requires yes/no → `PENDING(kind=consent)`.
- `requireMoreInformation_multiPending`: multi-step plan with two missing params → two pendings, no execution.
- `requireMoreInformation_resolution`: follow-up provides missing `bundleId` → re-plan yields executable `exportControlChartToExcel` with all params; resolver succeeds.
- `requireMoreInformation_abandon`: user says “cancel” → plan/state cleared or marked cancelled.
- `requireMoreInformation_newInstructionAbandons`: user provides a wholly new instruction while a plan is pending; prior plan is discarded, new plan is generated fresh with no reused pendings.

## Implementation Steps
1) **Universal grammar/prompt** ✅  
   - Add guidance: “Do not guess or emit empty strings. Emit PENDING for missing/uncertain required params.”  
   - Ensure validation rejects empty strings for required params.
2) **Plan DSL grammar** ✅  
   - Add inline `PENDING` alongside `PA` in `PS`.  
   - Disallow coexisting executable action and unresolved pendings for the same step.
3) **Planner/validator** ✅  
   - Treat missing/empty/invalid required params as failure → require `PENDING`.  
   - If any `PENDING` exists, block execution for that step (and likely the whole plan).  
   - Normalize empty strings to “missing” and fail validation.
4) **Plan model** ✅  
   - Add symmetric step types: `ActionStep`, `PendingActionStep`, `ErrorStep`.  
   - Visitors and resolvers understand pending and block execution with surfaced reasons.
5) **Resolver/runtime** ✅  
   - On `PENDING`, do not execute; surface assistant message synthesized from `pending` messages.  
   - Provide a helper to build user-facing follow-up prompts.
6) **Conversation state**  
   - Persist partial plan + pendings; on user follow-up, merge new values and re-run planner (re-invocation path for previously pending plans once user supplies info).  
   - Maintain compact rolling context: original instruction (or summary), already-provided params, current pendings, latest user reply. Avoid replaying full history.
   - On retry, append a system-prompt addendum: note this is a retry, list pendings with user-friendly labels, include the latest user reply verbatim, and remind “use the new reply only if it truly satisfies pending items; otherwise emit PENDING; do not guess.”
   - Follow-up asks should be user-friendly (e.g., “I need the bundleId to export the control chart”), not internal action identifiers.
   - If user cancels, clear pendings/plan.
7) **Prompting & guardrails**  
   - In system messages, instruct: “For missing/unclear required params, emit PENDING; do not emit the executable action for that step.”  
   - Add guardrails to avoid inappropriate action invocation: if no suitable action exists, emit `ERROR` instead of guessing a “closest” action.

## Backward Compatibility
- Existing outputs with empty strings for required params should now fail validation, producing `PENDING` instead.  
- Existing grammars gain universal guardrail; Plan DSL adds the new construct. Ensure older tests that assume immediate execution are updated to allow pendings when info is missing.

## Open Questions / Decisions
- Should a single `PENDING` block the entire plan, or can independent steps execute? (Recommended: block whole plan initially, relax later if needed.)  
- Inline vs. top-level pendings: choose one to simplify parsing (recommended: inline per step).  
- How to represent multiple missing params for a single step: multiple `PENDING` entries vs. one with a list. (Recommended: multiple entries, one per param, keyed by `stepId`.)

