package org.javai.springai.actions.tuning;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record TuningExperimentResult(
		String experimentName,
		Instant executedAt,
		List<ConfigPerformance> configPerformances
) {
	public ConfigPerformance getBestPerformance() {
		return configPerformances.stream()
				.max(Comparator.comparingDouble(ConfigPerformance::averageScore))
				.orElseThrow();
	}
}
