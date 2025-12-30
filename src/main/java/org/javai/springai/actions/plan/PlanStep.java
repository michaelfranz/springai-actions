package org.javai.springai.actions.plan;

import java.util.List;
import java.util.Map;
import org.javai.springai.actions.bind.ActionBinding;

/**
 * Represents a single step in a plan. Sealed to ensure all step types are known.
 * <p>
 * Steps can be:
 * <ul>
 *   <li>{@link ActionStep} - A fully bound action ready for execution</li>
 *   <li>{@link PendingActionStep} - An action missing required parameters</li>
 *   <li>{@link ErrorStep} - An error encountered during parsing or validation</li>
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
	 * A parameter that is pending user input.
	 *
	 * @param name the parameter name
	 * @param message a prompt to show the user
	 */
	record PendingParam(String name, String message) {
	}
}
