package org.javai.springai.actions.execution;

import java.util.Objects;
import org.javai.springai.actions.planning.PlanStep;

public abstract class AbstractExecutableAction implements ExecutableAction {

	private final PlanStep step;
	private final ActionMetadata metadata;

	public AbstractExecutableAction(PlanStep step, ActionMetadata metadata) {
		this.step = Objects.requireNonNull(step, "step must not be null");
		this.metadata = Objects.requireNonNull(metadata, "metadata must not be null");
	}

	public PlanStep getStep() {
		return step;
	}

	@Override
	public ActionMetadata metadata() {
		return metadata;
	}
}
