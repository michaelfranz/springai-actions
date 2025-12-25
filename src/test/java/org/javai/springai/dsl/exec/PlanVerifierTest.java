package org.javai.springai.dsl.exec;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionParam;
import org.javai.springai.dsl.act.ActionRegistry;
import org.javai.springai.dsl.plan.Plan;
import org.javai.springai.dsl.plan.PlanStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlanVerifierTest {

	private ActionRegistry registry;
	private PlanVerifier verifier;

	@BeforeEach
	void setUp() {
		registry = new ActionRegistry();
		registry.registerActions(new DemoActions());
		verifier = new PlanVerifier(registry);
	}

	@Test
	void passesKnownActionWithMatchingArity() {
		Plan plan = new Plan("", List.of(
				new PlanStep.ActionStep("", "demoAction", new Object[] { "a", "b" })
		));

		Plan verified = verifier.verify(plan);
		assertThat(verified.planSteps()).hasSize(1);
		assertThat(verified.planSteps().getFirst()).isInstanceOf(PlanStep.ActionStep.class);
	}

	@Test
	void convertsUnknownActionToError() {
		Plan plan = new Plan("", List.of(
				new PlanStep.ActionStep("", "unknownAction", new Object[] { "a" })
		));

		Plan verified = verifier.verify(plan);
		assertThat(verified.planSteps()).hasSize(1);
		assertThat(verified.planSteps().getFirst()).isInstanceOf(PlanStep.ErrorStep.class);
		PlanStep.ErrorStep error = (PlanStep.ErrorStep) verified.planSteps().getFirst();
		assertThat(error.assistantMessage()).contains("Unknown action id");
	}

	@Test
	void convertsArityMismatchToError() {
		Plan plan = new Plan("", List.of(
				new PlanStep.ActionStep("", "demoAction", new Object[] { "onlyOne" })
		));

		Plan verified = verifier.verify(plan);
		assertThat(verified.planSteps()).hasSize(1);
		assertThat(verified.planSteps().getFirst()).isInstanceOf(PlanStep.ErrorStep.class);
		PlanStep.ErrorStep error = (PlanStep.ErrorStep) verified.planSteps().getFirst();
		assertThat(error.assistantMessage()).contains("Arity mismatch");
	}

	@Test
	void convertsDisallowedValueToError() {
		ActionRegistry constrainedRegistry = new ActionRegistry();
		constrainedRegistry.registerActions(new ConstrainedActions());
		PlanVerifier constrainedVerifier = new PlanVerifier(constrainedRegistry);

		Plan plan = new Plan("", List.of(
				new PlanStep.ActionStep("", "constrainedAction", new Object[] { "BLUE", "A123" })
		));

		Plan verified = constrainedVerifier.verify(plan);
		assertThat(verified.planSteps().getFirst()).isInstanceOf(PlanStep.ErrorStep.class);
		PlanStep.ErrorStep error = (PlanStep.ErrorStep) verified.planSteps().getFirst();
		assertThat(error.assistantMessage()).contains("allowed value");
	}

	@Test
	void convertsRegexMismatchToError() {
		ActionRegistry constrainedRegistry = new ActionRegistry();
		constrainedRegistry.registerActions(new ConstrainedActions());
		PlanVerifier constrainedVerifier = new PlanVerifier(constrainedRegistry);

		Plan plan = new Plan("", List.of(
				new PlanStep.ActionStep("", "constrainedAction", new Object[] { "RED", "B999" })
		));

		Plan verified = constrainedVerifier.verify(plan);
		assertThat(verified.planSteps().getFirst()).isInstanceOf(PlanStep.ErrorStep.class);
		PlanStep.ErrorStep error = (PlanStep.ErrorStep) verified.planSteps().getFirst();
		assertThat(error.assistantMessage()).contains("regex");
	}

	@Test
	void passesAllowedValuesAndRegex() {
		ActionRegistry constrainedRegistry = new ActionRegistry();
		constrainedRegistry.registerActions(new ConstrainedActions());
		PlanVerifier constrainedVerifier = new PlanVerifier(constrainedRegistry);

		Plan plan = new Plan("", List.of(
				new PlanStep.ActionStep("", "constrainedAction", new Object[] { "RED", "A123" })
		));

		Plan verified = constrainedVerifier.verify(plan);
		assertThat(verified.planSteps().getFirst()).isInstanceOf(PlanStep.ActionStep.class);
	}

	private static class DemoActions {
		@Action
		public void demoAction(
				@ActionParam String first,
				@ActionParam String second) {
			// no-op
		}
	}

	private static class ConstrainedActions {
		@Action
		public void constrainedAction(
				@ActionParam(allowedValues = { "RED", "GREEN" }) String color,
				@ActionParam(allowedRegex = "^A[0-9]{3}$") String code) {
			// no-op
		}
	}
}

