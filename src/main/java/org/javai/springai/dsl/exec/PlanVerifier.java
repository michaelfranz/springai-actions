package org.javai.springai.dsl.exec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.javai.springai.dsl.act.ActionDescriptor;
import org.javai.springai.dsl.act.ActionRegistry;
import org.javai.springai.dsl.plan.Plan;
import org.javai.springai.dsl.plan.PlanStep;

/**
 * Validates a parsed plan against the registered actions (existence + arity).
 * Unknown actions or arity mismatches are converted to ErrorStep instances.
 */
public final class PlanVerifier {

	private final Map<String, ActionDescriptor> descriptorsById;

	public PlanVerifier(ActionRegistry registry) {
		Objects.requireNonNull(registry, "registry must not be null");
		this.descriptorsById = registry.getActionDescriptors().stream()
				.collect(Collectors.toMap(ActionDescriptor::id, Function.identity(), (a, b) -> a));
	}

	public Plan verify(Plan plan) {
		if (plan == null) {
			return new Plan("", List.of());
		}
		List<PlanStep> steps = plan.planSteps();
		if (steps == null || steps.isEmpty()) {
			return plan;
		}

		List<PlanStep> verified = new ArrayList<>(steps.size());
		for (PlanStep step : steps) {
			if (step instanceof PlanStep.ActionStep action) {
				verified.add(verifyActionStep(action));
			}
			else {
				// Pending/Error remain unchanged
				verified.add(step);
			}
		}
		return new Plan(plan.assistantMessage(), verified);
	}

	private PlanStep verifyActionStep(PlanStep.ActionStep action) {
		String actionId = action.actionId();
		ActionDescriptor descriptor = descriptorsById.get(actionId);
		if (descriptor == null) {
			return new PlanStep.ErrorStep("Unknown action id: " + actionId);
		}
		int expected = descriptor.actionParameterSpecs().size();
		int actual = action.actionArguments() != null ? action.actionArguments().length : 0;
		if (expected != actual) {
			return new PlanStep.ErrorStep("Arity mismatch for action " + actionId + ": expected " + expected + " but got " + actual);
		}
		return action;
	}
}

