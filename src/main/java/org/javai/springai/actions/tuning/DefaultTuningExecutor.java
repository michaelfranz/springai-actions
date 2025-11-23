package org.javai.springai.actions.tuning;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.javai.springai.actions.execution.ExecutablePlan;

public class DefaultTuningExecutor implements TuningExecutor {

	private final PlanQualityEvaluator qualityEvaluator;
	private final TuningLogger logger = new TuningLogger(DefaultTuningExecutor.class);

	private final PlanSupplierFactory planSupplierFactory;

	public DefaultTuningExecutor(PlanQualityEvaluator qualityEvaluator, PlanSupplierFactory planSupplierFactory) {
		this.qualityEvaluator = qualityEvaluator;
		this.planSupplierFactory = planSupplierFactory;
	}


	@Override
	public TuningExperimentResult execute(TuningExperiment experiment) {
		List<ConfigPerformance> performances = new ArrayList<>();

		for (LlmTuningConfig config : experiment.configsToTest()) {
			List<PlanTestResult> queryResults = new ArrayList<>();

			// Run each test case multiple times
			for (PlanTestCase testCase : experiment.testCases()) {
				for (int run = 0; run < experiment.runsPerConfig(); run++) {
					PlanTestResult result = runSingleTest(
							planSupplierFactory, testCase, experiment.name(), config, run
					);
					queryResults.add(result);
				}
			}

			// Calculate performance metrics
			double avgScore = queryResults.stream()
					.mapToDouble(r -> r.qualityScore().overallScore())
					.average()
					.orElse(0.0);

			Map<String, Double> breakdown = calculateBreakdown(queryResults);

			performances.add(new ConfigPerformance(
					config,
					queryResults,
					avgScore,
					breakdown
			));
		}

		return new TuningExperimentResult(
				experiment.name(),
				Instant.now(),
				performances
		);
	}

	private Map<String, Double> calculateBreakdown(List<PlanTestResult> queryResults) {
		Map<String, List<PlanTestResult>> groupedByDifficulty = queryResults.stream()
				.filter(result -> result.qualityScore() != null)
				.collect(Collectors.groupingBy(
						result -> result.testCase().difficulty().name().toLowerCase()
				));

		Map<String, Double> breakdown = new LinkedHashMap<>();

		groupedByDifficulty.forEach((difficulty, results) -> {
			double avgScore = results.stream()
					.mapToDouble(r -> r.qualityScore().overallScore())
					.average()
					.orElse(0.0);

			long successCount = results.stream()
					.filter(r -> r.qualityScore().overallScore() > 0.0)
					.count();

			logger.debug("Difficulty {}: {} results, {} successful, avg score: {:.2f}",
					difficulty, results.size(), successCount, avgScore);

			breakdown.put(difficulty, avgScore);
		});

		return breakdown;
	}

	private PlanTestResult runSingleTest(
			PlanSupplierFactory planSupplierFactory,
			PlanTestCase testCase,
			String experimentName,
			LlmTuningConfig config,
			int runNumber
	) {
		Instant startTime = Instant.now();
		try {
			PlanSupplier planSupplier = planSupplierFactory.getPlanSupplier(config);
			ExecutablePlan plan = planSupplier.get();

			PlanQualityScore score = qualityEvaluator.evaluate(testCase.userInput(),plan);

			Duration executionTime = Duration.between(startTime, Instant.now());

			// Log for analysis
			logger.logTestResult(experimentName, config, testCase, score, executionTime, runNumber);

			return new PlanTestResult(testCase, score, executionTime, null);
		} catch (Exception e) {
			Duration executionTime = Duration.between(startTime, Instant.now());
			logger.logTestFailure(experimentName, config, testCase, e, executionTime, runNumber);

			return new PlanTestResult(testCase, null, executionTime, e);
		}
	}
}
