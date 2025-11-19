package org.javai.springai.actions.execution;

import java.util.List;
import java.util.Objects;

record ExecutionStep(ActionMetadata metadata, List<ExecutionDAG.DependencyEdge> dependencies) {

	ExecutionStep(ActionMetadata metadata, List<ExecutionDAG.DependencyEdge> dependencies) {
		this.metadata = Objects.requireNonNull(metadata, "metadata must not be null");
		this.dependencies = dependencies == null || dependencies.isEmpty()
				? List.of()
				: List.copyOf(dependencies);
	}
}

