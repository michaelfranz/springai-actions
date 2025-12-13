package org.javai.springai.dsl.prompt;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.javai.springai.dsl.act.ActionPromptEmitter;
import org.javai.springai.dsl.act.ActionRegistry;
import org.javai.springai.dsl.act.ActionSpec;
import org.javai.springai.dsl.act.ActionSpecFilter;

/**
 * Builds full system prompts combining selected action specs with DSL guidance.
 */
public final class SystemPromptBuilder {

	private SystemPromptBuilder() {
	}

	public enum Mode {
		SXL, JSON
	}

	/**
	 * Build a system prompt for the given action selection and mode.
	 * @param registry action registry containing all available actions
	 * @param filter selection filter to limit actions for this prompt
	 * @param guidanceProvider supplies DSL guidance for referenced DSL ids
	 * @param mode emission mode (SXL or JSON)
	 * @return full system prompt text
	 */
	public static String build(ActionRegistry registry,
			ActionSpecFilter filter,
			DslGuidanceProvider guidanceProvider,
			Mode mode) {
		if (filter == null) {
			filter = ActionSpecFilter.ALL;
		}
		if (guidanceProvider == null) {
			guidanceProvider = DslGuidanceProvider.NONE;
		}

		String actionsSection = ActionPromptEmitter.emit(registry,
				mode == Mode.SXL ? ActionPromptEmitter.Mode.SXL : ActionPromptEmitter.Mode.JSON,
				filter);

		Set<String> dslIds = collectDslIds(registry, filter);
		String dslSection = buildDslSection(dslIds, guidanceProvider);

		return "ACTIONS:\n" + actionsSection + "\n\nDSL GUIDANCE:\n" + dslSection;
	}

	private static Set<String> collectDslIds(ActionRegistry registry, ActionSpecFilter filter) {
		Set<String> ids = new LinkedHashSet<>();
		List<ActionSpec> selected = registry.getActionSpecs().stream()
				.filter(filter::include)
				.toList();
		for (ActionSpec spec : selected) {
			spec.actionParameterSpecs().forEach(p -> {
				if (p.dslId() != null && !p.dslId().isBlank()) {
					ids.add(p.dslId());
				}
			});
		}
		return ids;
	}

	private static String buildDslSection(Set<String> dslIds, DslGuidanceProvider provider) {
		return dslIds.stream()
				.map(id -> provider.guidanceFor(id)
						.map(g -> "DSL " + id + ":\n" + g)
						.orElse("DSL " + id + ": (no guidance available)"))
				.collect(Collectors.joining("\n\n"));
	}
}
