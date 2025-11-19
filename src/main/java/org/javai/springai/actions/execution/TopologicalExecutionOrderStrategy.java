package org.javai.springai.actions.execution;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default ordering that relies solely on static metadata. It performs a
 * topological sort to ensure dependencies are honoured and detects cycles.
 */
public final class TopologicalExecutionOrderStrategy implements ExecutionOrderStrategy {

	@Override
	public List<ExecutionDAG.Node> order(Map<String, ExecutionStep> stepsById) {
		Objects.requireNonNull(stepsById, "stepsById must not be null");
		Map<String, Integer> indegree = new LinkedHashMap<>();
		Map<String, List<String>> adjacency = new HashMap<>();

		// Build adjacency list and record how many prerequisites each node has
		stepsById.forEach((stepId, step) -> {
			Set<String> deps = step.dependencies()
					.stream()
					.map(ExecutionDAG.DependencyEdge::stepId)
					.collect(Collectors.toCollection(LinkedHashSet::new));
			indegree.put(stepId, deps.size());
			for (String dependency : deps) {
				if (!stepsById.containsKey(dependency)) {
					throw new IllegalArgumentException("Step '%s' depends on unknown step '%s'"
							.formatted(stepId, dependency));
				}
				adjacency.computeIfAbsent(dependency, k -> new ArrayList<>()).add(stepId);
			}
		});

		// Seed queue with nodes that have no unmet dependencies
		Queue<String> ready = new ArrayDeque<>();
		indegree.forEach((stepId, degree) -> {
			if (degree == 0) {
				ready.add(stepId);
			}
		});

		List<ExecutionDAG.Node> ordered = new ArrayList<>();
		int orderIndex = 1;

		// Perform Kahn's algorithm: pop ready nodes, decrement indegree of successors
		while (!ready.isEmpty()) {
			String current = ready.remove();
			ExecutionStep step = stepsById.get(current);
			ordered.add(new ExecutionDAG.Node(
					current,
					step.metadata(),
					step.dependencies(),
					orderIndex++
			));

			for (String successor : adjacency.getOrDefault(current, List.of())) {
				int degree = indegree.computeIfPresent(successor, (k, v) -> v - 1);
				if (degree == 0) {
					ready.add(successor);
				}
			}
		}

		// Any leftover nodes imply a cycle
		if (ordered.size() != stepsById.size()) {
			throw new IllegalStateException("Cycle detected while building execution DAG");
		}

		return ordered;
	}
}

