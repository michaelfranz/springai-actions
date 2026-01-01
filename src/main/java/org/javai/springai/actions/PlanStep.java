package org.javai.springai.actions;

import java.util.List;
import java.util.Map;
import org.javai.springai.actions.internal.bind.ActionBinding;
import org.javai.springai.actions.internal.plan.PlanArgument;

/**
 * Represents a single step in a plan. Sealed to ensure all step types are known.
 * <p>
 * Steps can be:
 * <ul>
 *   <li>{@link ActionStep} - A fully bound action ready for execution</li>
 *   <li>{@link PendingActionStep} - An action missing required parameters</li>
 *   <li>{@link ErrorStep} - An error encountered during parsing or validation</li>
 *   <li>{@link NoActionStep} - The assistant could not identify an appropriate action</li>
 * </ul>
 */
public sealed interface PlanStep {

	/**
	 * A fully bound action step, ready for execution.
	 * The binding contains the bean/method to invoke.
	 *
	 * @param binding the action binding with method and bean
	 * @param arguments the typed arguments for invocation
	 */
	record ActionStep(ActionBinding binding, List<PlanArgument> arguments) implements PlanStep {
		public ActionStep {
			arguments = arguments != null ? List.copyOf(arguments) : List.of();
		}

		/**
		 * Convenience: get action ID from binding.
		 */
		public String actionId() {
			return binding != null ? binding.id() : null;
		}

		/**
		 * Convenience: get description from binding.
		 */
		public String description() {
			return binding != null ? binding.description() : null;
		}
	}

	/**
	 * A step where required parameters are missing.
	 * The actionId is retained because we cannot fully bind without complete params.
	 *
	 * @param assistantMessage LLM-generated message about this step
	 * @param actionId the action that would be executed
	 * @param pendingParams parameters that need to be provided
	 * @param providedParams parameters that were already provided
	 */
	record PendingActionStep(
			String assistantMessage,
			String actionId,
			PendingParam[] pendingParams,
			Map<String, Object> providedParams
	) implements PlanStep {
	}

	/**
	 * An error encountered during parsing or validation.
	 *
	 * @param reason description of what went wrong
	 */
	record ErrorStep(String reason) implements PlanStep {
	}

	/**
	 * Indicates the assistant could not identify an appropriate action for the user's request.
	 * <p>
	 * This step should be used when:
	 * <ul>
	 *   <li>The user's request is outside the assistant's capabilities</li>
	 *   <li>No registered action matches the user's intent</li>
	 *   <li>The assistant needs to politely decline and explain what it CAN help with</li>
	 * </ul>
	 * <p>
	 * The message should explain why no action was taken and suggest alternatives.
	 *
	 * @param message explanation for the user about why no action was taken
	 */
	record NoActionStep(String message) implements PlanStep {
	}

	/**
	 * A parameter that is pending user input.
	 *
	 * @param name the parameter name
	 * @param message a prompt to show the user
	 */
	record PendingParam(String name, String message) {
	}
}
