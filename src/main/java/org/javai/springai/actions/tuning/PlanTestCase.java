package org.javai.springai.actions.tuning;

import org.javai.springai.actions.execution.ExecutablePlan;

public record PlanTestCase(
		String id,
		String userInput,
		String description,
		ExecutablePlan expectedPlan,  // Reference implementation
		DifficultyLevel difficulty  // EASY, MEDIUM, HARD
) {}