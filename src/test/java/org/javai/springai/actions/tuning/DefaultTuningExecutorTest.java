package org.javai.springai.actions.tuning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.execution.ExecutableAction;
import org.javai.springai.actions.execution.ExecutablePlan;
import org.javai.springai.actions.execution.PlanExecutionException;
import org.javai.springai.testsupport.DeterministicPlanSupplierFactory;
import org.junit.jupiter.api.Test;

class DefaultTuningExecutorTest {

	@Test
	void executeAggregatesScoresAndBreakdownsDeterministically() {
		LlmTuningConfig baseline = new LlmTuningConfig("baseline", 0.2, 0.9);
		LlmTuningConfig variant = new LlmTuningConfig("variant", 0.3, 0.8);

		DeterministicPlanSupplierFactory planSupplierFactory = new DeterministicPlanSupplierFactory()
				.register(baseline, () -> planFor("baseline"))
				.register(variant, () -> planFor("variant"));

		List<PlanTestCase> testCases = List.of(
				new PlanTestCase("easy-case", "easy prompt", "EASY scenario", null, DifficultyLevel.EASY),
				new PlanTestCase("hard-case", "hard prompt", "HARD scenario", null, DifficultyLevel.HARD)
		);

		TuningExperiment experiment = new TuningExperiment(
				"deterministic-run",
				List.of(baseline, variant),
				testCases,
				2
		);

		DefaultTuningExecutor executor = new DefaultTuningExecutor(new ConfigAwareQualityEvaluator(), planSupplierFactory);

		TuningExperimentResult result = executor.execute(experiment);

		assertThat(result.configPerformances()).hasSize(2);
		ConfigPerformance baselinePerformance = performanceFor(result, baseline);
		ConfigPerformance variantPerformance = performanceFor(result, variant);

		int expectedRunsPerConfig = testCases.size() * experiment.runsPerConfig();
		assertThat(baselinePerformance.queryResults()).hasSize(expectedRunsPerConfig);
		assertThat(variantPerformance.queryResults()).hasSize(expectedRunsPerConfig);

		assertThat(planSupplierFactory.invocationsFor(baseline)).isEqualTo(expectedRunsPerConfig);
		assertThat(planSupplierFactory.invocationsFor(variant)).isEqualTo(expectedRunsPerConfig);

		assertThat(baselinePerformance.averageScore()).isCloseTo(0.80, within(1e-9));
		assertThat(baselinePerformance.breakdown()).containsKeys("easy", "hard");
		assertThat(baselinePerformance.breakdown().get("easy")).isCloseTo(0.90, within(1e-9));
		assertThat(baselinePerformance.breakdown().get("hard")).isCloseTo(0.70, within(1e-9));

		assertThat(variantPerformance.averageScore()).isCloseTo(0.65, within(1e-9));
		assertThat(variantPerformance.breakdown()).containsKeys("easy", "hard");
		assertThat(variantPerformance.breakdown().get("easy")).isCloseTo(0.75, within(1e-9));
		assertThat(variantPerformance.breakdown().get("hard")).isCloseTo(0.55, within(1e-9));

		assertThat(result.getBestPerformance()).isSameAs(baselinePerformance);
	}

	private ConfigPerformance performanceFor(TuningExperimentResult result, LlmTuningConfig config) {
		return result.configPerformances().stream()
				.filter(perf -> perf.config().equals(config))
				.findFirst()
				.orElseThrow();
	}

	private static ExecutablePlan planFor(String label) {
		return new ExecutablePlan(List.of(new ConfigAwareAction(label)));
	}

	private static final class ConfigAwareQualityEvaluator implements PlanQualityEvaluator {

		@Override
		public PlanQualityScore evaluate(String userInput, ExecutablePlan generatedPlan) {
			ConfigAwareAction action = (ConfigAwareAction) generatedPlan.executables().get(0);
			double base = userInput.contains("easy") ? 0.80 : 0.60;
			double modifier = action.label().equals("baseline") ? 0.10 : -0.05;
			double score = clamp(base + modifier);
			return new PlanQualityScore(score, score, score, score, Map.of("configScore", score));
		}

		private double clamp(double value) {
			return Math.max(0.0, Math.min(1.0, value));
		}
	}

	private record ConfigAwareAction(String label) implements ExecutableAction {

		@Override
		public void perform(ActionContext ctx) throws PlanExecutionException {
			// no-op for testing purposes
		}
	}
}

