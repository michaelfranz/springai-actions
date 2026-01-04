package org.javai.springai.actions.internal.prompt;

import java.util.List;
import java.util.Optional;
import org.javai.springai.actions.PromptContributor;
import org.javai.springai.actions.internal.bind.ActionDescriptor;
import org.javai.springai.actions.internal.bind.ActionParameterDescriptor;

/**
 * Contributor that provides the action catalog for the system prompt.
 * 
 * <p>This contributor lists available actions with their descriptions and parameters.
 * The output format schema is enforced at the API level via OpenAI Structured Outputs,
 * so this contributor focuses on WHAT actions do, not HOW to format the response.</p>
 */
public final class PlanActionsContextContributor implements PromptContributor {

	@Override
	public Optional<String> contribute(SystemPromptContext context) {
		if (context == null || context.registry() == null) {
			return Optional.empty();
		}
		
		List<ActionDescriptor> descriptors = context.registry().getActionDescriptors();
		if (descriptors.isEmpty()) {
			return Optional.empty();
		}
		
		// Filter if needed
		if (context.filter() != null) {
			descriptors = descriptors.stream()
					.filter(context.filter()::include)
					.toList();
		}
		
		if (descriptors.isEmpty()) {
			return Optional.empty();
		}
		
		StringBuilder sb = new StringBuilder();
		sb.append("AVAILABLE ACTIONS:\n\n");
		
		for (ActionDescriptor action : descriptors) {
			sb.append("• ").append(action.id());
			if (action.description() != null && !action.description().isBlank()) {
				sb.append(" - ").append(action.description().trim());
			}
			sb.append("\n");
			
			// List parameters with their constraints
			for (ActionParameterDescriptor param : action.actionParameterSpecs()) {
				sb.append("    ").append(param.name());
				if (param.description() != null && !param.description().isBlank()) {
					sb.append(": ").append(param.description().trim());
				}
				// Show allowed values if constrained
				if (param.allowedValues() != null && param.allowedValues().length > 0) {
					sb.append(" [").append(String.join(", ", param.allowedValues())).append("]");
				}
				sb.append("\n");
			}
			sb.append("\n");
		}
		
		return Optional.of(sb.toString().trim());
	}
}

