package org.javai.springai.actions.tuning;

import java.util.List;
import java.util.Map;

public record ConfigPerformance(
		LlmTuningConfig config,
		List<PlanTestResult> queryResults,
		double averageScore,
		Map<String, Double> breakdown  // e.g., "easy" -> 0.92, "medium" -> 0.78, "hard" -> 0.65
) {}