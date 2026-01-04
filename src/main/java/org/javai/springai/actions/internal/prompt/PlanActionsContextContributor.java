package org.javai.springai.actions.internal.prompt;

import java.util.Optional;
import org.javai.springai.actions.PromptContributor;
import org.javai.springai.actions.api.TypeHandlerRegistry;
import org.javai.springai.actions.internal.bind.ActionPromptContributor;

/**
 * Contributor that provides the action catalog for the system prompt.
 * 
 * <p>This contributor uses JSON Schema to define the complete output contract,
 * with "hardcoded complete action shapes" where each action is a self-contained
 * definition with its actionId bound via {@code const} and parameters inlined.
 * This eliminates any ambiguity about which parameters apply to which action.</p>
 * 
 * <p>The output includes:
 * <ul>
 *   <li>A JSON Schema defining valid Plan structures with per-action ActionStep variants</li>
 *   <li>A compact example showing correct parameter usage</li>
 * </ul>
 */
public final class PlanActionsContextContributor implements PromptContributor {

	@Override
	public Optional<String> contribute(SystemPromptContext context) {
		if (context == null || context.registry() == null) {
			return Optional.empty();
		}
		
		if (context.registry().getActionDescriptors().isEmpty()) {
			return Optional.empty();
		}
		
		// Get type handler registry from context if available
		TypeHandlerRegistry typeRegistry = null;
		if (context.dslContext() != null) {
			Object registry = context.dslContext().get("typeHandlerRegistry");
			if (registry instanceof TypeHandlerRegistry thr) {
				typeRegistry = thr;
			}
		}
		
		// Generate JSON Schema with hardcoded action shapes + example
		String contribution = ActionPromptContributor.emitExemplar(
				context.registry(),
				context.filter()
		);
		
		return Optional.of(contribution);
	}
}

