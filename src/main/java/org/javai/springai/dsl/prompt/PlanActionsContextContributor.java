package org.javai.springai.dsl.prompt;

import java.util.List;
import java.util.Optional;
import org.javai.springai.dsl.act.ActionDescriptor;

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
		// For SXL mode, provide a minimal action list (just IDs and descriptions, no JSON schemas)
		// to give the LLM context about available actions without bloating the prompt
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
		}
		
		return Optional.of(actions.toString());
	}
}

