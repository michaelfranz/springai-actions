package org.javai.springai.actions.tuning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class TuningExperimentResultTest {

	@Test
	void bestPerformanceReturnsHighestAverage() {
		ConfigPerformance weaker = new ConfigPerformance(
				new LlmTuningConfig("baseline", 0.2, 0.9),
				List.of(),
				0.55,
				Map.of()
		);
		ConfigPerformance stronger = new ConfigPerformance(
				new LlmTuningConfig("variant", 0.3, 0.8),
				List.of(),
				0.83,
				Map.of()
		);

		TuningExperimentResult result = new TuningExperimentResult(
				"best-config-test",
				Instant.parse("2024-01-01T00:00:00Z"),
				List.of(weaker, stronger)
		);

		assertThat(result.getBestPerformance()).isSameAs(stronger);
	}

	@Test
	void bestPerformanceThrowsWhenEmpty() {
		TuningExperimentResult result = new TuningExperimentResult(
				"empty",
				Instant.parse("2024-01-01T00:00:00Z"),
				List.of()
		);

		assertThatThrownBy(result::getBestPerformance)
				.isInstanceOf(NoSuchElementException.class);
	}
}


