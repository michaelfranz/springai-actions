package org.javai.springai.actions.internal.prompt;

import java.util.List;
import java.util.Optional;
import org.javai.springai.actions.PromptContributor;
import org.javai.springai.actions.internal.bind.ActionDescriptor;
import org.javai.springai.actions.internal.bind.ActionParameterDescriptor;

/**
 * Contributor that provides the action catalog for the system prompt.
 * 
 * <p>This contributor lists available actions with their descriptions and parameters
 * in a JSON-like format that matches the expected output structure. This helps
 * prevent the LLM from inventing parameter names.</p>
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
		sb.append("ACTION CATALOG:\n\n");
		
		// Add mapping instruction FIRST
		sb.append("⚠️ IMPORTANT: Convert user terms to exact allowed values:\n");
		sb.append("   \"displacements\" → \"displacement\"\n");
		sb.append("   \"forces\" → \"force\"\n\n");
		
		for (ActionDescriptor action : descriptors) {
			sb.append("■ ").append(action.id()).append("\n");
			if (action.description() != null && !action.description().isBlank()) {
				sb.append("  ").append(action.description().trim()).append("\n");
			}
			
			// Parameters table
			List<ActionParameterDescriptor> params = action.actionParameterSpecs();
			for (ActionParameterDescriptor param : params) {
				sb.append("  • ").append(param.name());
				if (param.allowedValues() != null && param.allowedValues().length > 0) {
					sb.append(": MUST be ");
					for (int j = 0; j < param.allowedValues().length; j++) {
						sb.append("\"").append(param.allowedValues()[j]).append("\"");
						if (j < param.allowedValues().length - 1) {
							sb.append(" or ");
						}
					}
				} else {
					sb.append(": string");
				}
				if (param.description() != null && !param.description().isBlank()) {
					sb.append(" (").append(param.description().trim()).append(")");
				}
				sb.append("\n");
			}
			sb.append("\n");
		}
		
		return Optional.of(sb.toString().trim());
	}
}

