package org.javai.springai.actions.tuning;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PlanQualityScoreTest {

	@Test
	void overallScoreUsesWeightedAverage() {
		PlanQualityScore score = new PlanQualityScore(0.2, 0.4, 0.6, 0.8, Map.of("note", 1.0));

		double expected = (0.25 * 0.2) + (0.40 * 0.4) + (0.20 * 0.6) + (0.15 * 0.8);

		assertThat(score.overallScore()).isEqualTo(expected);
	}
}


