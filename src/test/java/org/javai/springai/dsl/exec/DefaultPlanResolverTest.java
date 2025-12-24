package org.javai.springai.dsl.exec;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.Map;
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

		ResolvedPlan result = resolver.resolve(plan, registry);

		assertThat(result.steps()).hasSize(1);
		assertThat(result.steps().getFirst()).isInstanceOf(ResolvedStep.ActionStep.class);
		ResolvedStep.ActionStep step = (ResolvedStep.ActionStep) result.steps().getFirst();
		assertThat(step.binding().id()).isEqualTo("greet");
		assertThat(step.arguments()).hasSize(2);
		assertThat(step.arguments().get(0).value()).isEqualTo("Bob");
		assertThat(step.arguments().get(1).value()).isEqualTo(3);
	}

	@Test
	void failsOnUnknownAction() {
		Plan plan = new Plan(
				"",
				List.of(new PlanStep.ActionStep("", "unknownAction", new Object[] {}))
		);

		ResolvedPlan result = resolver.resolve(plan, registry);
		assertThat(result.status()).isEqualTo(org.javai.springai.dsl.plan.PlanStatus.ERROR);
		assertThat(result.steps()).hasSize(1);
		assertThat(result.steps().getFirst()).isInstanceOf(ResolvedStep.ErrorStep.class);
	}

	@Test
	void failsOnArityMismatch() {
		Plan plan = new Plan(
				"",
				List.of(new PlanStep.ActionStep("", "greet", new Object[] { "Bob" }))
		);

		ResolvedPlan result = resolver.resolve(plan, registry);
		assertThat(result.status()).isEqualTo(org.javai.springai.dsl.plan.PlanStatus.ERROR);
	}

	@Test
	void failsOnTypeConversionError() {
		Plan plan = new Plan(
				"",
				List.of(new PlanStep.ActionStep("", "greet", new Object[] { "Bob", "not-a-number" }))
		);

		ResolvedPlan result = resolver.resolve(plan, registry);
		assertThat(result.status()).isEqualTo(org.javai.springai.dsl.plan.PlanStatus.ERROR);
	}

	@Test
	void resolvesListParameterValue() {
		Plan plan = new Plan(
				"",
				List.of(new PlanStep.ActionStep("", "handleList", new Object[] { List.of("A12345", "A3145") }))
		);

		ResolvedPlan result = resolver.resolve(plan, registry);

		ResolvedStep.ActionStep step = (ResolvedStep.ActionStep) result.steps().getFirst();
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

		ResolvedPlan result = resolver.resolve(plan, registry);

		ResolvedStep.ActionStep step = (ResolvedStep.ActionStep) result.steps().getFirst();
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
						Map.of()))
		);

		ResolvedPlan result = resolver.resolve(plan, registry);
		assertThat(result.status()).isEqualTo(org.javai.springai.dsl.plan.PlanStatus.ERROR);
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

