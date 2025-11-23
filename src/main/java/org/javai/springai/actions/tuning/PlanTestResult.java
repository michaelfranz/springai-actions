package org.javai.springai.actions.tuning;

import java.time.Duration;

public record PlanTestResult(
		PlanTestCase testCase,
		PlanQualityScore qualityScore,
		Duration executionTime,
		Throwable error  // if generation failed
) {}