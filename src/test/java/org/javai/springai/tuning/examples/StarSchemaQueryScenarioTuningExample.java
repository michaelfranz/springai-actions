package org.javai.springai.tuning.examples;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.javai.springai.actions.execution.ExecutablePlan;
import org.javai.springai.actions.tuning.ConfigPerformance;
import org.javai.springai.actions.tuning.DefaultTuningExecutor;
import org.javai.springai.actions.tuning.DifficultyLevel;
import org.javai.springai.actions.tuning.LlmTuningConfig;
import org.javai.springai.actions.tuning.PlanQualityEvaluator;
import org.javai.springai.actions.tuning.PlanQualityScore;
import org.javai.springai.actions.tuning.PlanTestCase;
import org.javai.springai.actions.tuning.ScenarioPlanSupplier;
import org.javai.springai.actions.tuning.TuningExecutor;
import org.javai.springai.actions.tuning.TuningExperiment;
import org.javai.springai.actions.tuning.TuningExperimentResult;
import org.javai.springai.actions.tuning.TuningLogger;
import org.javai.springai.actions.tuning.TuningReportGenerator;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Example demonstrating the LLM tuning framework using the StarSchemaQueryTest scenario.
 * 
 * This example runs as a Spring Boot CommandLineRunner and performs a tuning experiment
 * that evaluates multiple LLM configurations (varying system prompts and parameters)
 * on the star schema query scenario. The experiment produces a comprehensive report
 * with recommendations for optimal configuration.
 * 
 * To run this example:
 *   ./gradlew bootRun --args='--spring.profiles.active=tuning-example'
 * 
 * Reports are written to: build/tuning/star-schema-query-tuning/
 */
@Component
@ConditionalOnProperty(name = "app.tuning-example.enabled", havingValue = "true")
public class StarSchemaQueryScenarioTuningExample implements CommandLineRunner {

	private static final TuningLogger logger = new TuningLogger(DefaultTuningExecutor.class);

	private final ScenarioPlanSupplier scenario;

	public StarSchemaQueryScenarioTuningExample(ScenarioPlanSupplier scenario) {
		this.scenario = scenario;
	}

	@Override
	public void run(String... args) throws Exception {
		logger.debug("Starting StarSchemaQueryScenarioTuningExample");
		
		try {
			// Define test cases for the scenario
			List<PlanTestCase> testCases = List.of(
					new PlanTestCase(
							"test-1",
							"Find the total order value of orders placed in germany in 2001.",
							"Find total order value by country and year",
							null,
							DifficultyLevel.MEDIUM
					),
					new PlanTestCase(
							"test-2",
							"What was the total revenue from product type 'Electronics' in the year 2000?",
							"Find total revenue by product type and year",
							null,
							DifficultyLevel.HARD
					),
					new PlanTestCase(
							"test-3",
							"List all orders from customers in Switzerland.",
							"List all orders from a specific country",
							null,
							DifficultyLevel.EASY
					)
			);

			// Define configurations to test
			// This could include various combinations of system prompts, temperature, topP
			LlmTuningConfig baselineConfig = scenario.defaultConfig();
			
			List<LlmTuningConfig> configsToTest = List.of(
					// Baseline
					baselineConfig,
					// Variant 1: Lower temperature (more deterministic)
					new LlmTuningConfig(
							baselineConfig.systemPrompt(),
							0.1,
							0.95,
							baselineConfig.model(),
							baselineConfig.modelVersion()
					),
					// Variant 2: Higher temperature (more creative)
					new LlmTuningConfig(
							baselineConfig.systemPrompt(),
							0.5,
							0.95,
							baselineConfig.model(),
							baselineConfig.modelVersion()
					),
					// Variant 3: Lower topP (nucleus sampling)
					new LlmTuningConfig(
							baselineConfig.systemPrompt(),
							0.2,
							0.7,
							baselineConfig.model(),
							baselineConfig.modelVersion()
					)
			);

			// Create a simple quality evaluator
			// In production, this would evaluate semantic correctness, efficiency, safety, etc.
			PlanQualityEvaluator evaluator = new BasicStarSchemaQualityEvaluator();

			// Build the tuning experiment
			TuningExperiment experiment = new TuningExperiment(
					"star-schema-query-tuning",
					configsToTest,
					testCases,
					2  // 2 runs per config for averaging
			);

			// Execute the tuning experiment
			logger.debug("Executing tuning experiment: {}", experiment.name());
			// Wrap ScenarioPlanSupplier as PlanSupplierFactory
			org.javai.springai.actions.tuning.PlanSupplierFactory factory = config -> scenario.planSupplier(config);
			TuningExecutor executor = new DefaultTuningExecutor(evaluator, factory);
			TuningExperimentResult result = executor.execute(experiment);

			// Generate reports
			Path reportOutputDir = Paths.get("build/tuning/star-schema-query-tuning");
			java.nio.file.Files.createDirectories(reportOutputDir);
			
			logger.debug("Generating reports to: {}", reportOutputDir);
			TuningReportGenerator reportGenerator = new TuningReportGenerator();
			reportGenerator.generateReport(result, reportOutputDir);

			// Print summary to console
			printSummary(result);

			logger.debug("Tuning experiment completed successfully");
			System.out.println("\n✅ Tuning experiment completed!");
			System.out.println("📊 Reports generated to: " + reportOutputDir.toAbsolutePath());

		} catch (Exception e) {
			logger.debug("Tuning experiment failed: {}", e.getMessage());
			System.err.println("❌ Tuning experiment failed: " + e.getMessage());
			e.printStackTrace();
			throw e;
		}
	}

	/**
	 * Print a summary of the tuning results to the console.
	 */
	private void printSummary(TuningExperimentResult result) {
		System.out.println("\n" + "=".repeat(70));
		System.out.println("TUNING EXPERIMENT RESULTS: " + result.experimentName());
		System.out.println("=".repeat(70));

		ConfigPerformance best = result.getBestPerformance();
		System.out.println("\n📈 Best Configuration:");
		System.out.println("   Average Score: " + formatScore(best.averageScore()));
		System.out.println("   Temperature: " + best.config().temperature());
		System.out.println("   Top P: " + best.config().topP());
		System.out.println("   System Prompt (snippet): " + snippet(best.config().systemPrompt(), 60));

		if (best.breakdown() != null && !best.breakdown().isEmpty()) {
			System.out.println("\n   Score Breakdown:");
			best.breakdown().forEach((difficulty, score) ->
					System.out.println("      " + capitalize(difficulty) + ": " + formatScore(score))
			);
		}

		System.out.println("\n📋 All Configurations:");
		for (ConfigPerformance perf : result.configPerformances()) {
			String marker = perf == best ? "✓" : " ";
			System.out.printf("   [%s] Avg Score: %.3f | Temp: %.2f | TopP: %.2f%n",
					marker,
					perf.averageScore(),
					perf.config().temperature(),
					perf.config().topP());
		}

		System.out.println("\n" + "=".repeat(70));
	}

	private String snippet(String text, int maxLength) {
		if (text == null) return "(null)";
		if (text.length() <= maxLength) return text;
		return text.substring(0, maxLength) + "...";
	}

	private String formatScore(Double score) {
		if (score == null) return "N/A";
		return String.format("%.3f", score);
	}

	private String capitalize(String s) {
		if (s == null || s.isEmpty()) return s;
		return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
	}

	/**
	 * Basic quality evaluator for the star schema query scenario.
	 * Evaluates syntactic correctness, semantic relevance, and efficiency.
	 */
	private static class BasicStarSchemaQualityEvaluator implements PlanQualityEvaluator {

		@Override
		public PlanQualityScore evaluate(String userInput, ExecutablePlan generatedPlan) {
			// In a real implementation, this would:
			// 1. Execute the plan
			// 2. Validate that the SQL query is syntactically correct
			// 3. Check that the query uses appropriate metadata
			// 4. Evaluate the semantic correctness of the query
			// 5. Assess query efficiency (e.g., proper joins, no cartesian products)

			// For this example, we'll use a simple heuristic-based score
			double syntacticCorrectness = evaluateSyntacticCorrectness(generatedPlan);
			double semanticRelevance = evaluateSemanticRelevance(generatedPlan, userInput);
			double efficiency = evaluateEfficiency(generatedPlan);
			double safety = 0.8; // Placeholder

			return new PlanQualityScore(
					syntacticCorrectness,
					semanticRelevance,
					efficiency,
					safety,
					new java.util.HashMap<>()  // empty metadata
			);
		}

		private double evaluateSyntacticCorrectness(ExecutablePlan plan) {
			// Heuristic: if the plan has executable actions, assume basic syntactic correctness
			if (plan != null && !plan.executables().isEmpty()) {
				return 0.8;
			}
			return 0.3;
		}

		private double evaluateSemanticRelevance(ExecutablePlan plan, String userInput) {
			// Heuristic: very basic check
			// In production, use NLP/embeddings to check semantic similarity
			if (plan != null && !plan.executables().isEmpty()) {
				return 0.75;
			}
			return 0.4;
		}

		private double evaluateEfficiency(ExecutablePlan plan) {
			// Heuristic: if the plan is compact, assume good efficiency
			if (plan != null && plan.executables().size() <= 3) {
				return 0.85;
			}
			return 0.6;
		}
	}
}

