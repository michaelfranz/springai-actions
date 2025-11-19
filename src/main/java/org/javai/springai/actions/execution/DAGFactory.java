package org.javai.springai.actions.execution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Builds {@link ExecutionDAG} instances from the metadata exposed by
 * {@link ExecutableAction}s. The factory performs validation, detects cycles,
 * and documents the resulting order.
 */
public class DAGFactory {

	private final ExecutionOrderStrategy orderStrategy;

	public DAGFactory() {
		this(new TopologicalExecutionOrderStrategy());
	}

	public DAGFactory(ExecutionOrderStrategy orderStrategy) {
		this.orderStrategy = Objects.requireNonNull(orderStrategy, "orderStrategy must not be null");
	}

	public ExecutionDAG create(List<? extends ExecutableAction> actions) {
		Objects.requireNonNull(actions, "actions must not be null");
		if (actions.isEmpty()) {
			return new ExecutionDAG(List.of());
		}

		Map<String, ActionMetadata> metadataByStep = new LinkedHashMap<>();

		for (ExecutableAction action : actions) {
			ActionMetadata metadata = action.metadata();
			if (metadata == null) {
				throw new IllegalArgumentException("ExecutableAction returned null metadata");
			}
			String stepId = requireStepId(metadata);
			if (metadataByStep.put(stepId, metadata) != null) {
				throw new IllegalArgumentException("Duplicate stepId detected: " + stepId);
			}
		}

		Map<String, ExecutionStep> steps = resolveSteps(metadataByStep);
		List<ExecutionDAG.Node> ordered = orderStrategy.order(steps);
		return new ExecutionDAG(ordered);
	}

	private String requireStepId(ActionMetadata metadata) {
		String stepId = metadata.stepId();
		if (stepId == null || stepId.isBlank() || "unspecified".equals(stepId)) {
			throw new IllegalArgumentException("ActionMetadata is missing a stepId: " + metadata);
		}
		return stepId;
	}

	private Map<String, ExecutionStep> resolveSteps(Map<String, ActionMetadata> metadataByStep) {
		Map<String, Set<String>> contextProducers = new HashMap<>();
		metadataByStep.forEach((stepId, metadata) -> metadata.producesContext()
				.forEach(key -> contextProducers.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(stepId)));

		Map<String, ExecutionStep> steps = new LinkedHashMap<>();

		metadataByStep.forEach((stepId, metadata) -> {
			Map<String, List<String>> dependencyReasons = new LinkedHashMap<>();
			addExplicitDependencies(stepId, metadata, metadataByStep, dependencyReasons);
			addContextDependencies(stepId, metadata, contextProducers, dependencyReasons);
			List<ExecutionDAG.DependencyEdge> edges = dependencyReasons.entrySet().stream()
					.map(entry -> new ExecutionDAG.DependencyEdge(entry.getKey(), entry.getValue()))
					.toList();
			steps.put(stepId, new ExecutionStep(metadata, edges));
		});

		return steps;
	}

	private void addExplicitDependencies(String stepId,
			ActionMetadata metadata,
			Map<String, ActionMetadata> metadataByStep,
			Map<String, List<String>> dependencyReasons) {
		for (String dependency : metadata.dependsOn()) {
			ActionMetadata dependencyMetadata = metadataByStep.get(dependency);
			if (dependencyMetadata == null) {
				throw new IllegalArgumentException("Step '%s' depends on unknown step '%s'"
						.formatted(stepId, dependency));
			}
			if (stepId.equals(dependency)) {
				throw new IllegalArgumentException("Step '%s' cannot depend on itself".formatted(stepId));
			}
			ensureNoContextContradiction(stepId, metadata, dependency, dependencyMetadata);
			addReason(dependencyReasons, dependency, "explicit");
		}
	}

	private void addContextDependencies(String stepId,
			ActionMetadata metadata,
			Map<String, Set<String>> contextProducers,
			Map<String, List<String>> dependencyReasons) {

		for (String contextKey : metadata.requiresContext()) {
			Set<String> producers = contextProducers.get(contextKey);
			if (producers == null || producers.isEmpty()) {
				continue;
			}
			for (String producer : producers) {
				if (producer.equals(stepId)) {
					continue;
				}
				addReason(dependencyReasons, producer, "context:" + contextKey);
			}
		}
	}

	private void ensureNoContextContradiction(String stepId,
			ActionMetadata metadata,
			String dependency,
			ActionMetadata dependencyMetadata) {

		if (metadata.producesContext().isEmpty() || dependencyMetadata.requiresContext().isEmpty()) {
			return;
		}
		Set<String> produced = new java.util.HashSet<>(metadata.producesContext());
		produced.retainAll(dependencyMetadata.requiresContext());
		if (!produced.isEmpty()) {
			throw new IllegalStateException(
					"Explicit dependency '%s' -> '%s' contradicts context flow %s"
							.formatted(stepId, dependency, produced));
		}
	}

	private void addReason(Map<String, List<String>> dependencyReasons, String dependency, String reason) {
		dependencyReasons.computeIfAbsent(dependency, k -> new ArrayList<>());
		List<String> reasons = dependencyReasons.get(dependency);
		if (!reasons.contains(reason)) {
			reasons.add(reason);
		}
	}
}

