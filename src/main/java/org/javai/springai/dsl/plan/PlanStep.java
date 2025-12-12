package org.javai.springai.dsl.plan;

public sealed interface PlanStep {

	record Action(String assistantMessage, String actionId, Object[] actionArguments)
			implements PlanStep {
	}

	record Error(String assistantMessage) implements PlanStep {
	}

}
