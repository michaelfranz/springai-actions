package org.javai.springai.actions.tuning;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.javai.springai.testsupport.LogCaptorAppender;
import org.junit.jupiter.api.Test;

class TuningLoggerTest {

	private static final LlmTuningConfig CONFIG = new LlmTuningConfig("prompt", 0.3, 0.9);
	private static final PlanTestCase TEST_CASE = new PlanTestCase(
			"case-1",
			"select * from orders",
			"simple query",
			null,
			DifficultyLevel.MEDIUM
	);

	@Test
	void logTestResultEmitsInfoEvent() {
		try (LogCaptorAppender appender = LogCaptorAppender.create(DefaultTuningExecutor.class, Level.INFO)) {
			TuningLogger logger = new TuningLogger(DefaultTuningExecutor.class);
			PlanQualityScore score = new PlanQualityScore(0.8, 0.8, 0.8, 0.8, Map.of("runs", 1.0));

			logger.logTestResult("exp-1", CONFIG, TEST_CASE, score, Duration.ofMillis(120), 1);

			assertThat(appender.events())
					.singleElement()
					.satisfies(event -> {
						assertThat(event.getLevel()).isEqualTo(Level.INFO);
						assertThat(event.getMessage().getFormattedMessage())
								.contains("score=0.8000")
								.contains("run #1");
					});
		}
	}

	@Test
	void logTestFailureEmitsWarningEvent() {
		try (LogCaptorAppender appender = LogCaptorAppender.create(DefaultTuningExecutor.class, Level.WARN)) {
			TuningLogger logger = new TuningLogger(DefaultTuningExecutor.class);
			Exception failure = new IllegalStateException("boom");

			logger.logTestFailure("exp-2", CONFIG, TEST_CASE, failure, Duration.ofMillis(45), 2);

			assertThat(appender.events())
					.singleElement()
					.satisfies(event -> {
						assertThat(event.getLevel()).isEqualTo(Level.WARN);
						assertThat(event.getMessage().getFormattedMessage())
								.contains("failed after 45 ms")
								.contains("boom");
					});
		}
	}

	@Test
	void debugLogsWhenEnabled() {
		try (LogCaptorAppender appender = LogCaptorAppender.create(DefaultTuningExecutor.class, Level.DEBUG)) {
			TuningLogger logger = new TuningLogger(DefaultTuningExecutor.class);

			logger.debug("Difficulty {}: {} results, {} successful, avg score: {:.2f}", "easy", 5, 4, 0.73);

			assertThat(appender.messages())
					.anyMatch(message -> message.contains("Difficulty easy") && message.contains("avg score: 0.73"));
		}
	}

	@Test
	void debugSkipsWhenLevelHigher() {
		try (LogCaptorAppender appender = LogCaptorAppender.create(DefaultTuningExecutor.class, Level.INFO)) {
			TuningLogger logger = new TuningLogger(DefaultTuningExecutor.class);

			logger.debug("Difficulty {}: {} results, {} successful, avg score: {:.2f}", "hard", 2, 1, 0.42);

			assertThat(appender.events()).isEmpty();
		}
	}
}

