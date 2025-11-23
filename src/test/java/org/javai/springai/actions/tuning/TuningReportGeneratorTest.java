package org.javai.springai.actions.tuning;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TuningReportGeneratorTest {

	@Test
	void generateReportWritesAllArtifacts() throws IOException {
		LlmTuningConfig config = new LlmTuningConfig("report prompt", 0.25, 0.85);
		PlanTestCase testCase = new PlanTestCase("tc-1", "user prompt", "desc", null, DifficultyLevel.MEDIUM);
		PlanQualityScore score = new PlanQualityScore(0.9, 0.8, 0.7, 0.6, Map.of("latencyMs", 120.0));
		PlanTestResult testResult = new PlanTestResult(testCase, score, Duration.ofMillis(120), null);

		ConfigPerformance performance = new ConfigPerformance(
				config,
				List.of(testResult),
				score.overallScore(),
				Map.of("medium", score.overallScore())
		);
		TuningExperimentResult result = new TuningExperimentResult(
				"reporting",
				Instant.parse("2024-02-01T00:00:00Z"),
				List.of(performance)
		);

		Path outputDir = Files.createTempDirectory("tuning-report");
		try {
			new TuningReportGenerator().generateReport(result, outputDir);

			Path csv = outputDir.resolve("summary.csv");
			Path json = outputDir.resolve("detailed_results.json");
			Path html = outputDir.resolve("report.html");
			Path recommendation = outputDir.resolve("recommendation.txt");

			assertThat(csv).exists();
			assertThat(json).exists();
			assertThat(html).exists();
			assertThat(recommendation).exists();

			String csvContent = Files.readString(csv);
			assertThat(csvContent)
					.contains("system_prompt_snippet")
					.contains("report prompt")
					.contains("average_score");

			String jsonContent = Files.readString(json);
			assertThat(jsonContent)
					.contains("\"experimentName\": \"reporting\"")
					.contains("\"testCase\"")
					.contains("\"executionTimeMillis\": 120");

			String htmlContent = Files.readString(html);
			assertThat(htmlContent)
					.contains("Tuning Experiment: reporting")
					.contains("Recommended Configuration");

			String recommendationContent = Files.readString(recommendation);
			assertThat(recommendationContent)
					.contains("RECOMMENDED CONFIGURATION")
					.contains("Temperature: 0.25")
					.contains("TopP: 0.85");
		}
		finally {
			deleteRecursively(outputDir);
		}
	}

	private void deleteRecursively(Path directory) throws IOException {
		Files.walk(directory)
				.sorted(Comparator.reverseOrder())
				.forEach(path -> {
					try {
						Files.deleteIfExists(path);
					}
					catch (IOException e) {
						throw new RuntimeException(e);
					}
				});
	}
}

