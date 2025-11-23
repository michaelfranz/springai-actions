package org.javai.springai.actions.tuning;

import java.util.Map;

public record PlanQualityScore(
		double syntacticCorrectness,  // 0.0-1.0: Does it compile?
		double semanticRelevance,      // 0.0-1.0: Does it match user intent?
		double efficiency,             // 0.0-1.0: Is the query performant? (heuristic)
		double safety,                 // 0.0-1.0: No SQL injection risks?
		Map<String, Double> metadata   // extensible for custom metrics
) {
	public double overallScore() {
		// Weighted average (adjustable)
		return 0.25 * syntacticCorrectness
				+ 0.40 * semanticRelevance
				+ 0.20 * efficiency
				+ 0.15 * safety;
	}
}
