package org.javai.springai.actions.internal.resolve;

import org.javai.springai.actions.internal.bind.ActionRegistry;
import org.javai.springai.actions.internal.parse.RawPlan;
import org.javai.springai.actions.Plan;

/**
 * Resolves a parsed JSON plan into a bound Plan ready for execution.
 * <p>
 * Resolution includes:
 * <ul>
 *   <li>Validating action IDs exist in the registry</li>
 *   <li>Checking parameter arity</li>
 *   <li>Binding actions to their implementations</li>
 *   <li>Converting parameter values to target types</li>
 * </ul>
 * <p>
 * Non-resolvable steps are converted to error steps in the returned Plan.
 */
public interface PlanResolver {

	/**
	 * Resolve a JSON plan into a bound Plan.
	 *
	 * @param jsonPlan the parsed JSON plan from the LLM
	 * @param registry the action registry for binding
	 * @return a Plan with bound action steps (or error/pending steps)
	 */
	Plan resolve(RawPlan jsonPlan, ActionRegistry registry);
}
