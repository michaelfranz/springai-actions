package org.javai.springai.dsl.prompt;

import java.util.Optional;
import org.javai.springai.dsl.act.ActionDescriptorFilter;
import org.javai.springai.dsl.act.ActionPromptContributor;

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
		if (context == null || context.registry() == null) {
			return Optional.empty();
		}
		String actions = ActionPromptContributor.emit(
				context.registry(),
				ActionPromptContributor.Mode.SXL,
				context.filter() != null ? context.filter() : ActionDescriptorFilter.ALL);
		if (actions == null || actions.isBlank()) {
			return Optional.empty();
		}
		return Optional.of("ACTIONS:\n" + actions);
	}
}

