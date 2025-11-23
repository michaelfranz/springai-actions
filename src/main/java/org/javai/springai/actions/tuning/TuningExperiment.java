package org.javai.springai.actions.tuning;

import java.util.List;

/**
 * Defines a tuning experiment that sweeps across multiple LLM configurations.
 * Can optionally include automatic system prompt variant generation and model sweeps.
 * 
 * @param name descriptive name for the experiment (e.g., "customer-order-v2")
 * @param configsToTest list of LLM configurations to evaluate
 * @param testCases scenarios to test each configuration against
 * @param runsPerConfig how many times to run each config per test case (for averaging LLM stochasticity)
 * @param promptStrategy optional strategy for generating system prompt variants; if null, no variants are generated
 * @param promptVariants how many system prompt variants to generate (only used if promptStrategy is non-null)
 * @param scenario optional {@link ScenarioPlanSupplier} used to provide context for system prompt variant generation
 */
public record TuningExperiment(
		String name,
		List<LlmTuningConfig> configsToTest,
		List<PlanTestCase> testCases,
		int runsPerConfig,
		SystemPromptVariationStrategy promptStrategy,
		int promptVariants,
		ScenarioPlanSupplier scenario
) {
	
	/**
	 * Convenience constructor for simpler experiments without system prompt variation.
	 */
	public TuningExperiment(
			String name,
			List<LlmTuningConfig> configsToTest,
			List<PlanTestCase> testCases,
			int runsPerConfig
	) {
		this(name, configsToTest, testCases, runsPerConfig, null, 0, null);
	}
}