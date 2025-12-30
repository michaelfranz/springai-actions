package org.javai.springai.actions.internal.prompt;

import java.util.List;
import java.util.Optional;
import org.javai.springai.actions.PromptContributor;
import org.javai.springai.actions.internal.bind.ActionDescriptor;
import org.javai.springai.actions.internal.bind.ActionParameterDescriptor;

/**
 * Contributor that provides the action catalog for the system prompt.
 * The action catalog is included directly in the system prompt to help
 * the LLM understand the available actions and their parameters.
 */
public final class PlanActionsContextContributor implements PromptContributor {

	@Override
	public Optional<String> contribute(SystemPromptContext context) {
		if (context == null || context.registry() == null) {
			return Optional.empty();
		}
		
		List<ActionDescriptor> descriptors = context.registry().getActionDescriptors().stream()
				.filter(d -> context.filter() == null || context.filter().include(d))
				.toList();
		
		if (descriptors.isEmpty()) {
			return Optional.empty();
		}
		
		StringBuilder actions = new StringBuilder();
		actions.append("PLAN STEP OPTIONS:\n");
		actions.append("Valid actionId values (use EXACTLY as shown, case-sensitive):\n\n");
		
		for (ActionDescriptor descriptor : descriptors) {
			actions.append("• actionId: \"").append(descriptor.id()).append("\"\n");
			actions.append("  Purpose: ").append(descriptor.description()).append("\n");
			
			if (descriptor.actionParameterSpecs() != null && !descriptor.actionParameterSpecs().isEmpty()) {
				actions.append("  Parameters:\n");
				for (ActionParameterDescriptor param : descriptor.actionParameterSpecs()) {
					actions.append("    - ").append(param.name()).append(": ").append(param.typeId());
					actions.append("\n");
					
					// Show explicit examples if provided
					if (param.examples() != null && param.examples().length > 0) {
						actions.append("      Example: ").append(param.examples()[0]).append("\n");
					}
				}
			}
			actions.append("\n");
		}
		
		return Optional.of(actions.toString());
	}
}

