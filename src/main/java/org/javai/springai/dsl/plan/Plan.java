package org.javai.springai.dsl.plan;

import java.util.List;
import org.javai.springai.sxl.SxlNode;

/**
 * A typed version of plan that an application can work with
 * @param assistantMessage a model-generated message to accompany the plan
 * @param planSteps are the steps of the plan
 */
public record Plan(String assistantMessage, List<PlanStep> planSteps) {

	public static Plan of(SxlNode planNode) {
		return PlanNodeVisitor.generate(planNode);
	}

	public boolean hasPending() {
		return planSteps.stream().anyMatch(ps -> ps instanceof PlanStep.PendingActionStep);
	}

	public boolean hasError() {
		return planSteps.stream().anyMatch(ps -> ps instanceof PlanStep.ErrorStep);
	}
}
