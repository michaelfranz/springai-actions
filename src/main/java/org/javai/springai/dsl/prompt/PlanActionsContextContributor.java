package org.javai.springai.dsl.prompt;

import java.util.List;
import java.util.Optional;
import org.javai.springai.dsl.act.ActionDescriptor;
import org.javai.springai.dsl.act.ActionParameterDescriptor;

/**
 * Default contributor that appends the action catalog to the system prompt.
 * Generates JSON-oriented format guidance since plans use JSON structure.
 */
public final class PlanActionsContextContributor implements DslContextContributor {

	@Override
	public String dslId() {
		// No DSL - plans use JSON, not S-expressions
		return null;
	}

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
		
		StringBuilder actions = new StringBuilder("AVAILABLE ACTIONS:\n");
		for (ActionDescriptor descriptor : descriptors) {
			actions.append("- ").append(descriptor.id()).append(": ").append(descriptor.description()).append("\n");
			// Include parameter format guidance
			if (descriptor.actionParameterSpecs() != null && !descriptor.actionParameterSpecs().isEmpty()) {
				actions.append("  Parameters:\n");
				for (ActionParameterDescriptor param : descriptor.actionParameterSpecs()) {
					actions.append("    - ").append(param.name()).append(": ").append(param.typeId());
					
					// Show DSL format guidance for s-expression-backed parameters
					if (param.dslId() != null && !param.dslId().isBlank()) {
						actions.append(" (S-expression string)");
					}
					actions.append("\n");
					
					// Show explicit examples if provided
					if (param.examples() != null && param.examples().length > 0) {
						actions.append("      Example: ").append(param.examples()[0]).append("\n");
					}
				}
			}
		}
		
		return Optional.of(actions.toString());
	}
}

