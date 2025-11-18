package org.javai.springai.actions.execution;

import org.javai.springai.actions.planning.PlanStep;

public abstract class AbstractExecutableAction implements ExecutableAction {

	private final PlanStep step;

	public AbstractExecutableAction(PlanStep step) {
		this.step = step;
	}

	public PlanStep getStep() {
		return step;
	}
}
