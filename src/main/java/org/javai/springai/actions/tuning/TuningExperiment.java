package org.javai.springai.actions.tuning;

import java.util.List;

public record TuningExperiment(
		String name,
		List<LlmTuningConfig> configsToTest,
		List<PlanTestCase> testCases,
		int runsPerConfig  // For averaging (LLMs have stochastic behavior)
) {}