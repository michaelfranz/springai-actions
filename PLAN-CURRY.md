# Plan: Curried Plan Steps and PENDING Handling

## Goal
Stop emitting empty/guessed values for required params. Instead, treat each plan step like a curried function: if required args are missing/invalid/unclear, produce a structured `PENDING` artifact, elicit just what’s missing from the user, merge it, and resume to an executable plan.

## Core Concepts
- **Universal guardrail (minimal):** Never guess; never emit empty strings for required params. If required info is unknown, emit `PENDING`.
- **Plan DSL `PENDING` (rich/typed):** Attach to a specific step/action/param with reason/kind. Presence of any `PENDING` blocks that step (and optionally the whole plan) from execution.
- **Kinds:** `missing_required`, `clarification`, `disambiguation`, `consent`, `invalid_value`.
- **Shape (Plan DSL):**  
  `(PENDING (stepId s1) (action exportControlChartToExcel) (param bundleId) (kind missing_required) (reason "bundleId required to export control chart") (prompt "Provide bundleId"))`

## Conversation State & Orchestration
- **Conversation-aware API:** `formulatePlan(String userInput, ConversationState state)` appends a retry addendum from the state (pending items, prior provided params, latest user reply) and returns a plan plus derived status.
- **Retry addendum:** System addendum reminds the model it is a retry, lists pendings with user-friendly labels, includes the latest user reply verbatim, and reiterates “use the new reply only if it truly satisfies pending items; otherwise emit PENDING; do not guess.”
- **ConversationManager:** Orchestrates a turn: load state (by session), call `formulatePlan`, branch on status (`READY` → resolve/execute, `PENDING` → ask, `ERROR` → inform), and persist the next `ConversationState` derived from the returned plan. Supports multiple sessions/tabs via keyed storage.
- **State persistence:** Currently in-memory for unit flow; planned to be Spring-backed (e.g., bean + repository) for real deployment.
- **Token discipline:** Keep structured state exact (pendings, provided params, latest reply). Summaries are only for long free-form user text and are bounded; never summarize pending/provided maps. Keep a tiny rolling window for UX-only context; feed the model only structured state + latest reply.

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
6) **Conversation state & orchestration (in progress)**  
   - Planner entrypoint is conversation-centric: `formulatePlan(userInput, state)` builds retry addendum from `ConversationState` and returns a plan + status.  
   - `ConversationState.fromPlan(plan, prev, latestUserMsg)` (planned helper) derives next state; status enum (READY, PENDING, ERROR) drives branching.  
   - `ConversationManager` loads/persists state (in-memory now; Spring-backed later), calls planner, and branches execution vs. user follow-up.  
   - Planner remains stateless; external persistence supplies continuity across turns/sessions.  
   - Token discipline enforced: keep structured state exact; summarize long free-form user replies only when necessary and never summarize pending/provided maps.
   - `ConversationTurnResult` (target shape): include the latest `Plan`, updated `ConversationState`, a `PlanStatus` (READY, PENDING, ERROR), and—when status is READY—the resolved plan (or resolution result) ready for execution; when PENDING, include structured pending asks; when ERROR, include the surfaced error. Single carrier of per-turn UX artifacts; keep it small and user-facing, no raw chat history.
7) **Prompting & guardrails**  
   - In system messages, instruct: “For missing/unclear required params, emit PENDING; do not emit the executable action for that step.”  
   - Add guardrails to avoid inappropriate action invocation: if no suitable action exists, emit `ERROR` instead of guessing a “closest” action.

## Open Questions / Decisions
- Pending UX: shape of a `PendingRequirement` DTO, richer turn result (assistant message + structured ask + status).  
- PENDING blocking scope: should one pending block the entire plan or can independent steps execute? (Current stance: block whole plan; revisit later.)  
- Inline vs. top-level pendings: keep inline per step vs. promoting to a dedicated section.  
- Representation of multiple missing params: multiple `PENDING` entries vs. one with a list (current stance: multiple, keyed by `stepId`).  
- Placement of pending asks in the user turn: inline within the pending step vs. a summarized top-level ask block.

## Current State (WIP)
- Steps 1–5 implemented (guardrails, Plan DSL PENDING, validator, symmetric plan model, resolver blocks pending, executor added).
- Step 6 status:
  - Conversation scaffolding implemented: `ConversationState` now stores `List<PlanStep.PendingParam>` (no `PendingParamSnapshot`), helpers (`initial`, `withLatestUserMessage`, `withProvidedParam`), and tests updated.
  - Conversation storage: `ConversationStateStore` returns `Optional`; `InMemoryConversationStateStore` added.
  - Prompting: `ConversationPromptBuilder.buildRetryAddendum` implemented and tested.
  - Orchestration: `ConversationManager.converse` implemented; returns `ConversationTurnResult` (plan + next state) and persists state; currently branches up to planning/resolution only.
  - Planner: conversation-centric API (`formulatePlan(userInput, ConversationState)`), prompt preview carries retry addendum, internal helpers refactored for readability, `Plan.pendingParams()` exposed.
  - Tests: `ConversationManagerUnitTest` uses mocks to cover two-turn flow; integration follow-up test `requireMoreInformationFollowUpProvidesMissingBundleId` remains **disabled** until end-to-end re-plan/resolve/execute wiring is completed.
  - Remaining Step 6 work: user-facing pending ask helper, merge newly provided params into state for retries, cancel/new-instruction handling, executing resolved plans after successful retries, token-discipline safeguards for long replies.

