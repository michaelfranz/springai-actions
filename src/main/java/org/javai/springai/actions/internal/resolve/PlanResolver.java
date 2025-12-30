package org.javai.springai.actions.internal.resolve;

import org.javai.springai.actions.Plan;
import org.javai.springai.actions.api.TypeHandlerRegistry;
import org.javai.springai.actions.internal.bind.ActionRegistry;
import org.javai.springai.actions.internal.parse.RawPlan;

/**
 * Resolves a parsed JSON plan into a bound Plan ready for execution.
 * <p>
 * Resolution includes:
 * <ul>
 *   <li>Validating action IDs exist in the registry</li>
 *   <li>Checking parameter arity</li>
 *   <li>Binding actions to their implementations</li>
 *   <li>Converting parameter values to target types</li>
 *   <li>Validating SQL queries against the schema catalog (if provided)</li>
 * </ul>
 * <p>
 * Non-resolvable steps are converted to error steps in the returned Plan.
 */
public interface PlanResolver {

	/**
	 * Resolve a JSON plan into a bound Plan.
	 *
	 * @param jsonPlan the parsed JSON plan from the LLM
	 * @param context the resolution context containing registry and optional SQL catalog
	 * @return a Plan with bound action steps (or error/pending steps)
	 */
	Plan resolve(RawPlan jsonPlan, ResolutionContext context);

	/**
	 * Resolve a JSON plan into a bound Plan (convenience method without SQL catalog).
	 * Auto-discovers type handlers via SPI.
	 *
	 * @param jsonPlan the parsed JSON plan from the LLM
	 * @param registry the action registry for binding
	 * @return a Plan with bound action steps (or error/pending steps)
	 * @deprecated Use {@link #resolve(RawPlan, ResolutionContext)} instead
	 */
	@Deprecated(forRemoval = true)
	default Plan resolve(RawPlan jsonPlan, ActionRegistry registry) {
		return resolve(jsonPlan, ResolutionContext.of(registry, TypeHandlerRegistry.discover(), null));
	}
}
