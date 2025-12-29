package org.javai.springai.actions.plan;

import java.util.Map;

public sealed interface PlanStep {

	record ActionStep(String assistantMessage, String actionId, Object[] actionArguments)
			implements PlanStep {
	}

	/**
	 * Pending step retains both the pending requirements and the map of provided params (name->value).
	 */
	record PendingActionStep(String assistantMessage, String actionId, PendingParam[] pendingParams,
			Map<String, Object> providedParams)
			implements PlanStep {
	}

	record ErrorStep(String assistantMessage) implements PlanStep {
	}

	record PendingParam(String name, String message) {
	}

}
