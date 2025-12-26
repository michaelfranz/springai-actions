Spring AI Actions
=================

Purpose
-------
- Framework for building Java agentic applications where LLM calls are side-effect free. The model returns a declarative plan; the framework executes it and reports any missing pieces instead of making direct world changes during inference.
- Uses S-expression–based DSLs (SXL) for plans, SQL, and other artifacts to communicate complex structures compactly, reducing token usage versus JSON while preserving full expressiveness.
- Focused on robustness: if a full plan cannot be produced, the response includes structured diagnostics so callers or users can address gaps and retry safely.

Intended application
--------------------
- Targets data scientists and engineers in the auto industry who need to statistically analyze warehouse data for FDX modeling.
- Supports rapid requests for common statistical work (e.g., SPC readiness checks, SPC control charts) and arbitrary SQL SELECTs against a star-schema warehouse.
- Provides both a traditional “platform” UI and a chat interface; in chat, the LLM is always instructed to respond with a Plan (never execute directly).

Why S-expressions here
----------------------
- Compact: smaller prompts/responses than JSON for nested plans and structured queries, lowering token costs and latency.
- Structured: explicit grammar-backed DSLs reduce ambiguity and help contain hallucinations by constraining allowed symbols.
- Composable: plan steps can embed other DSL fragments (e.g., SQL) without bloating specs.
- Efficient parsing: simple LL(1)-style parsing enables fast conversion into typed Java objects.

Core ideas
----------
- Plan/execution separation: LLM produces a plan; runtime executes it and enforces side-effect boundaries.
- Grammar-backed guidance: universal SXL guidance plus domain grammars (plan, SQL, etc.) keep outputs well-formed across models.
- Tokenization pipeline: optional schema/PII tokenization and detokenization protect sensitive data while keeping LLM outputs valid and reversible.
- Chain-of-responsibility pipeline: pluggable layers (validation, PII scan, tokenization, prompt assembly, LLM call, detokenization) configured per deployment.
- Typed results: plans and nested DSL outputs are instantiated into strongly typed Java objects for reliability and downstream execution.

Project layout (high level)
---------------------------
- `src/main/java/org/javai/springai/` — framework code for actions, DSLs, SXL parsing/guidance, pipeline, tokenization.
- `src/main/resources/` — SXL grammars and prompt templates (plan, SQL, universal guidance).
- `src/test/java/` — tests; use AssertJ `assertThat` in new tests per project style.
- Design docs: `action-plan.md`, `PLAN-*`, `TOKENIZATION_AND_PIPELINE_DESIGN.md`, `TOKENIZATION_IMPLEMENTATION_PLAN.md`, `scalability.md`, etc.

Scenarios (examples, not shipped apps)
--------------------------------------
- `DataWarehouseApplicationScenarioTest`: simulates a user asking for a SQL SELECT plan against a warehouse (demonstrates plan/execution separation).
- `StatsApplicationScenarioTest`: simulates predefined statistical tests whose DAO inputs would drive subsequent queries.
- `ProtocolNotebookScenarioTest`: simulates selecting a statistical protocol (via a tool that lists protocol files) and planning a Marimo notebook that follows it.
- These scenarios illustrate how to exercise the framework for real-world patterns without embedding a full application in the repo.

Quick start
-----------
- Build & test: `./gradlew test`
- Run (Spring Boot): `./gradlew bootRun`
- Explore prompts/grammars: see `src/main/resources/META-INF/` for SXL meta-grammars and `build/prompt-samples/` for sample outputs.

Conceptual workflow
-------------------
1) User input enters the processing pipeline.  
2) Optional validation + PII scanning + schema tokenization run via pluggable layers.  
3) System prompt is assembled using SXL grammars and (optionally) tokenized schema hints.  
4) LLM returns an S-expression plan/SQL; the framework parses into typed objects.  
5) Detokenization (if enabled) restores real identifiers; execution engine runs the plan.  
6) On incomplete planning, the framework returns structured diagnostics instead of acting.

Roadmap highlights
------------------
- Production-grade token mapping stores, versioning, and audit trails.
- End-to-end tokenized SQL flow with detokenizing visitors.
- Extended guardrails (hallucination risk scoring, injection detection) as additional pipeline layers.

Contributing
------------
- Keep LLM interactions side-effect free; all actions flow from validated plans.
- Prefer S-expression DSLs over ad-hoc JSON for structured exchanges.
- Follow existing style in tests with AssertJ `assertThat`.

