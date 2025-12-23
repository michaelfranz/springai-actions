package org.javai.springai.dsl.exec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.javai.springai.actions.api.Action;
import org.javai.springai.dsl.act.ActionRegistry;
import org.javai.springai.dsl.plan.Plan;
import org.javai.springai.dsl.plan.PlanStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultPlanResolverTest {

	private ActionRegistry registry;
	private DefaultPlanResolver resolver;

	@BeforeEach
	void setup() {
		registry = new ActionRegistry();
		registry.registerActions(new SampleActions());
		resolver = new DefaultPlanResolver();
	}

	@Test
	void resolvesHappyPath() {
		Plan plan = new Plan(
				"",
				List.of(new PlanStep.ActionStep("", "greet", new Object[] { "Bob", 3 }))
		);

		PlanResolutionResult result = resolver.resolve(plan, registry);

		assertThat(result.isSuccess()).isTrue();
		assertThat(result.errors()).isEmpty();
		assertThat(result.resolvedPlan().steps()).hasSize(1);
		assertThat(result.resolvedPlan().steps().getFirst()).isInstanceOf(ResolvedStep.ActionStep.class);
		ResolvedStep.ActionStep step = (ResolvedStep.ActionStep) result.resolvedPlan().steps().getFirst();
		assertThat(step.binding().id()).isEqualTo("greet");
		assertThat(step.arguments()).hasSize(2);
		assertThat(step.arguments().get(0).value()).isEqualTo("Bob");
		assertThat(step.arguments().get(1).value()).isEqualTo(3);
	}

	@Test
	void propagatesPlanErrorStep() {
		Plan plan = new Plan(
				"",
				List.of(new PlanStep.ErrorStep("missing info"))
		);

		PlanResolutionResult result = resolver.resolve(plan, registry);

		assertThat(result.isSuccess()).isTrue();
		assertThat(result.errors()).isEmpty();
		assertThat(result.resolvedPlan().steps()).hasSize(1);
		assertThat(result.resolvedPlan().steps().getFirst()).isInstanceOf(ResolvedStep.ErrorStep.class);
		assertThat(((ResolvedStep.ErrorStep) result.resolvedPlan().steps().getFirst()).reason()).isEqualTo("missing info");
	}

	@Test
	void failsOnUnknownAction() {
		Plan plan = new Plan(
				"",
				List.of(new PlanStep.ActionStep("", "unknownAction", new Object[] {}))
		);

		PlanResolutionResult result = resolver.resolve(plan, registry);

		assertThat(result.isSuccess()).isFalse();
		assertThat(result.errors()).hasSize(1);
		assertThat(result.errors().getFirst().actionId()).isEqualTo("unknownAction");
	}

	@Test
	void failsOnArityMismatch() {
		Plan plan = new Plan(
				"",
				List.of(new PlanStep.ActionStep("", "greet", new Object[] { "Bob" }))
		);

		PlanResolutionResult result = resolver.resolve(plan, registry);

		assertThat(result.isSuccess()).isFalse();
		assertThat(result.errors()).hasSize(1);
		assertThat(result.errors().getFirst().reason()).contains("Argument count mismatch");
	}

	@Test
	void failsOnTypeConversionError() {
		Plan plan = new Plan(
				"",
				List.of(new PlanStep.ActionStep("", "greet", new Object[] { "Bob", "not-a-number" }))
		);

		PlanResolutionResult result = resolver.resolve(plan, registry);

		assertThat(result.isSuccess()).isFalse();
		assertThat(result.errors()).hasSize(1);
		assertThat(result.errors().getFirst().paramName()).isEqualTo("times");
		assertThat(result.errors().getFirst().reason()).contains("Failed to convert");
	}

	@Test
	void resolvesListParameterValue() {
		Plan plan = new Plan(
				"",
				List.of(new PlanStep.ActionStep("", "handleList", new Object[] { List.of("A12345", "A3145") }))
		);

		PlanResolutionResult result = resolver.resolve(plan, registry);

		assertThat(result.isSuccess()).isTrue();
		assertThat(result.errors()).isEmpty();
		ResolvedStep.ActionStep step = (ResolvedStep.ActionStep) result.resolvedPlan().steps().getFirst();
		assertThat(step.arguments()).hasSize(1);
		assertThat(step.arguments().getFirst().value()).isInstanceOf(List.class);
		assertThat(step.arguments().getFirst().value())
				.asInstanceOf(InstanceOfAssertFactories.list(String.class))
				.containsExactly("A12345", "A3145");
	}

	@Test
	void convertsListToArrayParameterValue() {
		Plan plan = new Plan(
				"",
				List.of(new PlanStep.ActionStep("", "handleArray", new Object[] { List.of("A12345", "A3145", "B4323") }))
		);

		PlanResolutionResult result = resolver.resolve(plan, registry);

		assertThat(result.isSuccess()).isTrue();
		assertThat(result.errors()).isEmpty();
		ResolvedStep.ActionStep step = (ResolvedStep.ActionStep) result.resolvedPlan().steps().getFirst();
		Object value = step.arguments().getFirst().value();
		assertThat(value).isInstanceOf(String[].class);
		assertThat((String[]) value).containsExactly("A12345", "A3145", "B4323");
	}

	@Test
	void failsOnPendingStep() {
		Plan plan = new Plan(
				"",
				List.of(new PlanStep.PendingActionStep("", "greet",
						new PlanStep.PendingParam[] { new PlanStep.PendingParam("name", "missing name") },
						new Object[] {}))
		);

		PlanResolutionResult result = resolver.resolve(plan, registry);

		assertThat(result.isSuccess()).isFalse();
		assertThat(result.errors()).hasSize(1);
		assertThat(result.errors().getFirst().paramName()).isEqualTo("name");
		assertThat(result.errors().getFirst().reason()).contains("Pending parameter");
	}

	private static class SampleActions {
		@Action(description = "Say hello")
		public void greet(String name, Integer times) {
		}

		@Action(description = "Handle bundle list")
		public void handleList(List<String> bundleIds) {
		}

		@Action(description = "Handle bundle array")
		public void handleArray(String[] bundleIds) {
		}
	}
}

