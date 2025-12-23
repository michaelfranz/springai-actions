package org.javai.springai.dsl.plan;

public sealed interface PlanStep {

	record ActionStep(String assistantMessage, String actionId, Object[] actionArguments)
			implements PlanStep {
	}

	record PendingActionStep(String assistantMessage, String actionId, PendingParam[] pendingParams, Object[] providedArguments)
			implements PlanStep {
	}

	record ErrorStep(String assistantMessage) implements PlanStep {
	}

	record PendingParam(String name, String message) {
	}

}
