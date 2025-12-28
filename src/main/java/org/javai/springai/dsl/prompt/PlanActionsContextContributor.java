package org.javai.springai.dsl.prompt;

import java.util.List;
import java.util.Optional;
import org.javai.springai.dsl.act.ActionDescriptor;
import org.javai.springai.dsl.act.ActionParameterDescriptor;

/**
 * Default contributor that appends the action catalog after the PLAN DSL guidance.
 */
public final class PlanActionsContextContributor implements DslContextContributor {

	@Override
	public String dslId() {
		return "sxl-plan";
	}

	@Override
	public Optional<String> contribute(SystemPromptContext context) {
		// For SXL mode, provide action list with parameter examples to guide format
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
			// Include parameter examples to show expected format
			if (descriptor.actionParameterSpecs() != null && !descriptor.actionParameterSpecs().isEmpty()) {
				for (ActionParameterDescriptor param : descriptor.actionParameterSpecs()) {
					if (param.examples() != null && param.examples().length > 0) {
						String example = param.examples()[0];
						actions.append("  Parameter '").append(param.name()).append("' example: ").append(example).append("\n");
					}
				}
			}
		}
		
		return Optional.of(actions.toString());
	}
}

