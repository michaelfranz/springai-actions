package org.javai.springai.actions.execution;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Immutable representation of the execution dependency graph. Nodes are ordered
 * topologically so schedulers can walk the plan level by level. The DAG can
 * render itself into a human-readable description to aid debugging.
 */
public final class ExecutionDAG {

	private final Map<String, Node> nodes;
	private final List<Node> orderedNodes;

	ExecutionDAG(List<Node> orderedNodes) {
		this.orderedNodes = List.copyOf(Objects.requireNonNull(orderedNodes, "orderedNodes"));
		this.nodes = this.orderedNodes.stream()
				.collect(Collectors.toMap(Node::stepId, n -> n, (a, b) -> a, LinkedHashMap::new));
	}

	public List<Node> nodes() {
		return orderedNodes;
	}

	public Optional<Node> findNode(String stepId) {
		return Optional.ofNullable(nodes.get(stepId));
	}

	public String describe() {
		if (orderedNodes.isEmpty()) {
			return "ExecutionDAG: <empty>";
		}
		StringBuilder sb = new StringBuilder("ExecutionDAG:\n");
		for (Node node : orderedNodes) {
			sb.append(node.orderIndex())
					.append(". ")
					.append(node.stepId())
					.append(" [action=")
					.append(node.metadata().actionName())
					.append(", mutability=")
					.append(node.metadata().mutability());

			if (!node.metadata().affinityIds().isEmpty()) {
				sb.append(", affinities=").append(node.metadata().affinityIds());
			}
			if (!node.dependencies().isEmpty()) {
				String deps = node.dependencies().stream()
						.map(DependencyEdge::describe)
						.collect(Collectors.joining(", ", "[", "]"));
				sb.append(", dependsOn=").append(deps);
			}
			sb.append("]\n");
		}
		return sb.toString().trim();
	}

	@Override
	public String toString() {
		return describe();
	}

	public record Node(
			String stepId,
			ActionMetadata metadata,
			List<DependencyEdge> dependencies,
			int orderIndex
	) {

		public Node {
			Objects.requireNonNull(stepId, "stepId must not be null");
			Objects.requireNonNull(metadata, "metadata must not be null");
			dependencies = dependencies == null ? Collections.emptyList() : List.copyOf(dependencies);
		}
	}

	public record DependencyEdge(
			String stepId,
			List<String> reasons
	) {

		public DependencyEdge {
			Objects.requireNonNull(stepId, "stepId must not be null");
			reasons = reasons == null || reasons.isEmpty()
					? List.of()
					: List.copyOf(reasons);
		}

		public String describe() {
			if (reasons.isEmpty()) {
				return stepId;
			}
			return stepId + "(" + String.join(",", reasons) + ")";
		}
	}
}

