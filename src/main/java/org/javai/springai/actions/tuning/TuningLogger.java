package org.javai.springai.actions.tuning;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TuningLogger {

	private final Logger logger;

	public TuningLogger(Class<DefaultTuningExecutor> tuningExecutorClass) {
		this.logger = LoggerFactory.getLogger(tuningExecutorClass);
	}

	public void logTestFailure(String experimentName, LlmTuningConfig config, PlanTestCase testCase, Exception e,
			Duration executionTime, int runNumber) {
		if (!logger.isWarnEnabled()) {
			return;
		}

		logger.warn(
				"[{}] config={} run #{} test='{}' difficulty={} failed after {} ms (prompt='{}'): {}",
				experimentName,
				describeConfig(config),
				runNumber,
				testCase.id(),
				testCase.difficulty(),
				toMillis(executionTime),
				summarize(testCase.userInput()),
				e.toString(),
				e
		);
	}

	public void logTestResult(String experimentName, LlmTuningConfig config, PlanTestCase testCase,
			PlanQualityScore score, Duration executionTime, int runNumber) {
		if (!logger.isInfoEnabled()) {
			return;
		}

		logger.info(
				"[{}] config={} run #{} test='{}' difficulty={} score={} (syntactic={}, semantic={}, efficiency={}, safety={}) {} ms metadata={}",
				experimentName,
				describeConfig(config),
				runNumber,
				testCase.id(),
				testCase.difficulty(),
				formatScore(score.overallScore()),
				formatScore(score.syntacticCorrectness()),
				formatScore(score.semanticRelevance()),
				formatScore(score.efficiency()),
				formatScore(score.safety()),
				toMillis(executionTime),
				score.metadata()
		);
	}

	public void debug(String message, String difficulty, int size, long successCount, double avgScore) {
		if (!logger.isDebugEnabled()) {
			return;
		}
		logger.debug(formatDebugMessage(message, difficulty, size, successCount, avgScore));
	}

	/**
	 * Flexible debug logging for arbitrary messages and arguments.
	 * Uses SLF4J's {} placeholder format.
	 */
	public void debug(String format, Object... args) {
		if (!logger.isDebugEnabled()) {
			return;
		}
		logger.debug(format, args);
	}

	private String describeConfig(LlmTuningConfig config) {
		if (config == null) {
			return "n/a";
		}
		return String.format("temp=%s, topP=%s, prompt='%s'",
				formatScore(config.temperature()),
				formatScore(config.topP()),
				summarize(config.systemPrompt())
		);
	}

	private long toMillis(Duration duration) {
		return duration == null ? -1 : duration.toMillis();
	}

	private String formatScore(Double value) {
		if (value == null || value.isNaN()) {
			return "n/a";
		}
		return String.format("%.4f", value);
	}

	private String formatDebugMessage(String template, String difficulty, int size, long successCount, double avgScore) {
		try {
			String normalized = template.replace("{:.2f}", "%s").replace("{}", "%s");
			return String.format(
					normalized,
					difficulty,
					size,
					successCount,
					String.format("%.2f", avgScore)
			);
		}
		catch (Exception ex) {
			return String.format(
					"%s difficulty=%s total=%d successful=%d avg=%.2f",
					template,
					difficulty,
					size,
					successCount,
					avgScore
			);
		}
	}

	private String summarize(String text) {
		if (text == null || text.isBlank()) {
			return "";
		}
		String normalized = text.replaceAll("\\s+", " ").trim();
		int maxLength = 64;
		return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength - 3) + "...";
	}
}
