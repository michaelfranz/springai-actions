# About the tuning package

This package provides a small experimentation harness for systematically evaluating and improving the quality of executable plans that are produced by an LLM-powered planner. Its mission is to let you vary LLM configuration parameters, replay representative user prompts, measure multiple facets of plan quality, and capture artifacts that make it easy to compare runs over time.

## Core building blocks

- `LlmTuningConfig` – describes a single model configuration (system prompt, temperature, top‑P) that should be evaluated.
- `PlanTestCase` – captures an input prompt, optional golden plan, and an approximate difficulty bucket so results can be segmented.
- `PlanQualityEvaluator` – plug point for your scoring logic; returns a multi-dimensional `PlanQualityScore` with an adjustable weighted average.
- `PlanSupplierFactory`/`PlanSupplier` – abstraction that knows how to ask your planner to build an `ExecutablePlan` for a given `LlmTuningConfig`.
- `TuningExperiment` – immutable definition of an experiment: name, configs to test, test suite, and number of stochastic runs per config.
- `DefaultTuningExecutor` – orchestrates the experiment: runs every test multiple times, logs outcomes, aggregates quality stats, and produces a `TuningExperimentResult`.
- `TuningReportGenerator` – turns results into CSV/JSON/HTML reports plus a human-readable recommendation (see `generateRecommendation` for the completed piece).

## Running an experiment

1. Implement `PlanSupplierFactory` so it can instantiate or configure your `PlanningChatClient` (or equivalent) using the provided `LlmTuningConfig`.
2. Provide a `PlanQualityEvaluator` that compares the produced `ExecutablePlan` to expectations (e.g., syntax validation, semantic diff, latency, safety checks).
3. Assemble a list of `PlanTestCase` instances that reflect the real prompts, data access patterns, and difficulty levels you care about.
4. Create a `TuningExperiment` with a descriptive name, the configs you want to sweep across, the test cases, and a `runsPerConfig` value to smooth out LLM stochasticity.
5. Execute the experiment and emit artifacts:

```java
PlanQualityEvaluator evaluator = /* your scoring logic */;
PlanSupplierFactory supplierFactory = config -> () -> planningClient(config).createPlan();

TuningExperiment experiment = new TuningExperiment(
		"prompt-baseline-vs-iter1",
		List.of(
				new LlmTuningConfig(BASE_PROMPT, 0.2, 0.95),
				new LlmTuningConfig(ITER1_PROMPT, 0.1, 0.9)
		),
		testCases,
		3 // repeat each config to average stochastic responses
);

TuningExecutor executor = new DefaultTuningExecutor(evaluator, supplierFactory);
TuningExperimentResult result = executor.execute(experiment);

Path artifactsDir = Path.of("build/tuning/prompt-baseline-vs-iter1");
Files.createDirectories(artifactsDir);
new TuningReportGenerator().generateReport(result, artifactsDir);
```

## Extending the framework

- **Custom metrics:** Add dimensions to `PlanQualityScore.metadata` (e.g., cost, token count, guardrail status) and fold them into `overallScore`.
- **Segmented insights:** Difficulty breakdowns come from `PlanTestCase.difficulty`, but you can tailor the grouping logic in `DefaultTuningExecutor`.
- **Reporting:** `TuningReportGenerator` stubs (`generateSummaryCSV`, `generateDetailedJSON`, `generateHTMLReport`) are ready for bespoke artifact formats.
- **Observability:** Fill in `TuningLogger` to push structured logs or trace spans for each run; useful for diffing raw LLM outputs after a regression.

With these pieces in place you can iterate on prompts, safety rules, and sampling parameters with data-backed confidence instead of anecdotal testing.
