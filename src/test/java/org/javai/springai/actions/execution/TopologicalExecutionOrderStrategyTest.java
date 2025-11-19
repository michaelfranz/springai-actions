package org.javai.springai.actions.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.javai.springai.actions.api.Mutability;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TopologicalExecutionOrderStrategyTest {

	private final TopologicalExecutionOrderStrategy strategy = new TopologicalExecutionOrderStrategy();

	@Nested
	class NoDependencies {

		@Test
		void preservesInputOrderWhenNoEdgesExist() {
			Map<String, ExecutionStep> steps = new LinkedHashMap<>();
			steps.put("A", step("A"));
			steps.put("B", step("B"));
			steps.put("C", step("C"));

			List<ExecutionDAG.Node> ordered = strategy.order(steps);

			assertThat(ordered).extracting(ExecutionDAG.Node::stepId)
					.containsExactly("A", "B", "C");
		}
	}

	@Nested
	class ContextDependencies {

		@Test
		void respectsEdgesDerivedFromContextFlow() {
			Map<String, ExecutionStep> steps = Map.of(
					"produce", step("produce"),
					"consume", step("consume", edge("produce", "context:foo")));

			List<ExecutionDAG.Node> ordered = strategy.order(steps);

			assertThat(ordered).extracting(ExecutionDAG.Node::stepId)
					.containsExactly("produce", "consume");
			assertThat(ordered.get(1).dependencies()).extracting(ExecutionDAG.DependencyEdge::describe)
					.containsExactly("produce(context:foo)");
		}
	}

	@Nested
	class ExplicitDependencies {

		@Test
		void honoursExplicitDependencyEdges() {
			Map<String, ExecutionStep> steps = Map.of(
					"a", step("a"),
					"b", step("b", edge("a", "explicit")));

			List<ExecutionDAG.Node> ordered = strategy.order(steps);

			assertThat(ordered).extracting(ExecutionDAG.Node::stepId)
					.containsExactly("a", "b");
			assertThat(ordered.get(1).dependencies()).extracting(ExecutionDAG.DependencyEdge::describe)
					.containsExactly("a(explicit)");
		}
	}

	@Nested
	class MixedDependencies {

		@Test
		void mergesExplicitAndImplicitEdges() {
			Map<String, ExecutionStep> steps = Map.of(
					"loadProfile", step("loadProfile"),
					"scoreProfile", step("scoreProfile",
							edge("loadProfile", "explicit")),
					"notify", step("notify", edge("scoreProfile", "context:profile", "explicit")));

			List<ExecutionDAG.Node> ordered = strategy.order(steps);

			assertThat(ordered).extracting(ExecutionDAG.Node::stepId)
					.containsExactly("loadProfile", "scoreProfile", "notify");

			assertThat(ordered.get(1).dependencies()).extracting(ExecutionDAG.DependencyEdge::describe)
					.containsExactly("loadProfile(explicit)");
			assertThat(ordered.get(2).dependencies()).extracting(ExecutionDAG.DependencyEdge::describe)
					.containsExactly("scoreProfile(context:profile,explicit)");
		}
	}

	@Nested
	class ComplexGraph {

		@Test
		void ordersComplexMixOfEdges() {
			Map<String, ExecutionStep> steps = Map.of(
					"fetchCustomer", step("fetchCustomer"),
					"fetchOrders", step("fetchOrders"),
					"summarizeOrders", step("summarizeOrders", edge("fetchOrders", "context:orders")),
					"prepareEmail", step("prepareEmail",
							edge("fetchCustomer", "context:customer"),
							edge("summarizeOrders", "context:orderSummary")),
					"sendEmail", step("sendEmail",
							edge("prepareEmail", "context:emailText"),
							edge("fetchCustomer", "explicit")));

			List<ExecutionDAG.Node> ordered = strategy.order(steps);

			assertThat(ordered.subList(0, 2)).extracting(ExecutionDAG.Node::stepId)
					.containsExactlyInAnyOrder("fetchCustomer", "fetchOrders");
			assertThat(ordered.subList(2, 5)).extracting(ExecutionDAG.Node::stepId)
					.containsExactly(
							"summarizeOrders",
							"prepareEmail",
							"sendEmail");

			assertThat(ordered.get(3).dependencies()).extracting(ExecutionDAG.DependencyEdge::describe)
					.containsExactlyInAnyOrder("fetchCustomer(context:customer)", "summarizeOrders(context:orderSummary)");
			assertThat(ordered.get(4).dependencies()).extracting(ExecutionDAG.DependencyEdge::describe)
					.containsExactlyInAnyOrder("fetchCustomer(explicit)", "prepareEmail(context:emailText)");
		}

		@Test
		void detectsCycles() {
			Map<String, ExecutionStep> steps = Map.of(
					"a", step("a", edge("b", "explicit")),
					"b", step("b", edge("a", "explicit")));

			assertThatThrownBy(() -> strategy.order(steps))
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("Cycle detected");
		}
	}

	private ExecutionStep step(String id, ExecutionDAG.DependencyEdge... edges) {
		return step(id, builder -> {
		}, edges);
	}

	private ExecutionStep step(String id,
			java.util.function.Consumer<ActionMetadata.Builder> customiser,
			ExecutionDAG.DependencyEdge... edges) {
		ActionMetadata.Builder builder = ActionMetadata.builder()
				.stepId(id)
				.actionName(id)
				.mutability(Mutability.READ_ONLY);
		customiser.accept(builder);
		ActionMetadata metadata = builder.build();
		return new ExecutionStep(metadata, List.of(edges));
	}

	private ExecutionDAG.DependencyEdge edge(String stepId, String... reasons) {
		return new ExecutionDAG.DependencyEdge(stepId, List.of(reasons));
	}
}

