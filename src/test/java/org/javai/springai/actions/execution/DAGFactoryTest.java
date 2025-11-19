package org.javai.springai.actions.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.api.Mutability;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DAGFactoryTest {

	private final DAGFactory factory = new DAGFactory();

	@Nested
	class SingleActionScenario {

		@Test
		void buildsDagWithSingleNode() {
			ExecutionDAG dag = factory.create(List.of(action("step1", builder -> builder
					.actionName("fetchTime")
					.mutability(Mutability.READ_ONLY))));

			assertThat(dag.nodes()).hasSize(1);
			assertThat(dag.describe()).contains("step1", "mutability=READ_ONLY");
		}
	}

	@Nested
	class CustomStrategyScenario {

		@Test
		void allowsPluggableOrdering() {
			ExecutionOrderStrategy reverseStrategy = steps -> {
				List<Map.Entry<String, ExecutionStep>> entries = steps.entrySet()
						.stream()
						.sorted(Map.Entry.<String, ExecutionStep>comparingByKey(Comparator.reverseOrder()))
						.toList();
				List<ExecutionDAG.Node> nodes = new ArrayList<>();
				int index = 1;
				for (Map.Entry<String, ExecutionStep> entry : entries) {
					nodes.add(new ExecutionDAG.Node(
							entry.getKey(),
							entry.getValue().metadata(),
							entry.getValue().dependencies(),
							index++));
				}
				return nodes;
			};

			DAGFactory customFactory = new DAGFactory(reverseStrategy);
			ExecutionDAG dag = customFactory.create(List.of(
					action("a", builder -> {
					}),
					action("b", builder -> {
					})
			));

			assertThat(dag.nodes())
					.extracting(ExecutionDAG.Node::stepId)
					.containsExactly("b", "a");
		}
	}

	@Nested
	class DependencyScenario {

		@Test
		void ordersNodesBasedOnDependencies() {
			ExecutionDAG dag = factory.create(List.of(
					action("stepA", builder -> builder.actionName("loadProfile")),
					action("stepB", builder -> builder
							.actionName("sendEmail")
							.addDependency("stepA"))
			));

			assertThat(dag.nodes())
					.extracting(ExecutionDAG.Node::stepId)
					.containsExactly("stepA", "stepB");
			assertThat(dag.describe()).contains("dependsOn=[stepA(explicit)]");
		}
	}

	@Nested
	class AffinityAndMutabilityScenario {

		@Test
		void documentsAffinitiesAndMutabilityInDescription() {
			ExecutionDAG dag = factory.create(List.of(
					action("fetch", builder -> builder
							.actionName("fetchOrders")
							.mutability(Mutability.READ_ONLY)
							.addAffinityId("customer:123")),
					action("update", builder -> builder
							.actionName("updateOrder")
							.mutability(Mutability.MUTATE)
							.addAffinityId("order:555")
							.addDependency("fetch"))
			));

			String description = dag.describe();
			assertThat(description).contains("affinities=[customer:123]");
			assertThat(description).contains("affinities=[order:555]");
			assertThat(description).contains("mutability=MUTATE");
		}
	}

	@Nested
	class InvalidScenarios {

		@Test
		void missingDependencyFailsFast() {
			assertThatThrownBy(() -> factory.create(List.of(
					action("lonely", builder -> builder
							.actionName("mutate")
							.addDependency("missing"))
			))).isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("depends on unknown step");
		}

		@Test
		void cyclesAreRejected() {
			assertThatThrownBy(() -> factory.create(List.of(
					action("a", builder -> builder.addDependency("b")),
					action("b", builder -> builder.addDependency("a"))
			))).isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("Cycle detected");
		}
	}

	@Nested
	class ContextDependencyScenario {

		@Test
		void infersDependenciesFromContextFlow() {
			ExecutionDAG dag = factory.create(List.of(
					action("prepare", builder -> builder
							.actionName("prepareEmail")
							.addProducesContext("emailText")),
					action("send", builder -> builder
							.actionName("sendEmail")
							.addRequiresContext("emailText"))
			));

			assertThat(dag.nodes())
					.extracting(ExecutionDAG.Node::stepId)
					.containsExactly("prepare", "send");
			assertThat(dag.describe())
					.contains("dependsOn=[prepare(context:emailText)]");
		}

		@Test
		void detectsContradictoryExplicitDependencies() {
			assertThatThrownBy(() -> factory.create(List.of(
					action("prepare", builder -> builder
							.actionName("prepareEmail")
							.addProducesContext("emailText")
							.addDependency("send")),
					action("send", builder -> builder
							.actionName("sendEmail")
							.addRequiresContext("emailText"))
			))).isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("contradicts context flow");
		}
	}

	private ExecutableAction action(String stepId, java.util.function.Consumer<ActionMetadata.Builder> customizer) {
		ActionMetadata.Builder builder = ActionMetadata.builder()
				.stepId(stepId)
				.actionName(stepId);
		customizer.accept(builder);
		ActionMetadata metadata = builder.build();

		return new ExecutableAction() {
			@Override
			public void perform(ActionContext ctx) {
				throw new UnsupportedOperationException("Not needed for DAG tests");
			}

			@Override
			public ActionMetadata metadata() {
				return metadata;
			}
		};
	}
}

