package org.javai.springai.dsl.prompt;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import org.javai.springai.dsl.act.ActionPromptEmitter;
import org.javai.springai.dsl.act.ActionRegistry;
import org.javai.springai.dsl.act.ActionSpec;
import org.javai.springai.dsl.act.ActionSpecFilter;
import org.javai.springai.sxl.grammar.SxlGrammarJsonSchemaEmitter;

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
		return build(registry, filter, guidanceProvider, mode, null, null);
	}

	/**
	 * Build a system prompt with provider/model-specific guidance if available.
	 */
	public static String build(ActionRegistry registry,
			ActionSpecFilter filter,
			DslGuidanceProvider guidanceProvider,
			Mode mode,
			String providerId,
			String modelId) {
		if (filter == null) {
			filter = ActionSpecFilter.ALL;
		}
		if (guidanceProvider == null) {
			guidanceProvider = DslGuidanceProvider.NONE;
		}

		String actionsSection = ActionPromptEmitter.emit(registry,
				mode == Mode.SXL ? ActionPromptEmitter.Mode.SXL : ActionPromptEmitter.Mode.JSON,
				filter);

		Set<String> dslIds = collectDslIds(registry, filter, guidanceProvider);
		List<GuidanceEntry> dslGuidance = buildDslSection(dslIds, guidanceProvider, providerId, modelId, mode);
		List<ObjectNode> dslSchemas = collectDslSchemas(dslIds, guidanceProvider);

		if (mode == Mode.JSON) {
			return buildJson(actionsSection, dslGuidance, dslSchemas);
		}

		String dslSection = dslGuidance.stream()
				.map(g -> "DSL " + g.dslId() + ":\n" + g.text())
				.collect(Collectors.joining("\n\n"));
		return "DSL GUIDANCE:\n" + dslSection + "\n\nACTIONS:\n" + actionsSection;
	}

	private static Set<String> collectDslIds(ActionRegistry registry, ActionSpecFilter filter, DslGuidanceProvider guidanceProvider) {
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
		// Always include plan DSL if available via grammar source
		if (guidanceProvider instanceof DslGrammarSource source) {
			if (source.grammarFor("sxl-plan").isPresent()) {
				ids.add("sxl-plan");
			}
		}
		return ids;
	}

	private static List<GuidanceEntry> buildDslSection(Set<String> dslIds, DslGuidanceProvider provider, String providerId, String modelId, Mode mode) {
		List<GuidanceEntry> entries = new ArrayList<>();
		for (String id : dslIds) {
			String guidance = provider.guidanceFor(id, providerId, modelId).orElse("(no guidance available)");
			if (mode == Mode.JSON) {
				guidance = "Use JSON structures (not S-expressions) adhering to the provided schemas. DSL id: " + id + ". Guidance: " + guidance;
			} else if (mode == Mode.SXL && provider instanceof DslGrammarSource source) {
				guidance = GrammarPromptSummarizer.summarize(source.grammarFor(id).orElse(null));
			}
			entries.add(new GuidanceEntry(id, guidance));
		}
		return entries;
	}

	private static List<ObjectNode> collectDslSchemas(Set<String> dslIds, DslGuidanceProvider provider) {
		if (!(provider instanceof DslGrammarSource source)) {
			return List.of();
		}
		List<ObjectNode> schemas = new ArrayList<>();
		for (String id : dslIds) {
			source.grammarFor(id).ifPresent(grammar -> schemas.add(SxlGrammarJsonSchemaEmitter.emit(grammar)));
		}
		return schemas;
	}

	private static String buildJson(String actionsJsonArray, List<GuidanceEntry> dslGuidance, List<ObjectNode> dslSchemas) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			ObjectNode root = mapper.createObjectNode();
			// Actions as parsed JSON array
			root.set("actions", mapper.readTree(actionsJsonArray));
			// DSL guidance as array of objects
			ArrayNode guidanceArray = root.putArray("dslGuidance");
			for (GuidanceEntry gEntry : dslGuidance) {
				ObjectNode g = guidanceArray.addObject();
				g.put("dslId", gEntry.dslId());
				g.put("guidance", gEntry.text());
			}
			if (dslSchemas != null && !dslSchemas.isEmpty()) {
				ArrayNode schemas = root.putArray("dslSchemas");
				dslSchemas.forEach(schemas::add);
			}
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to build JSON system prompt", e);
		}
	}

	private record GuidanceEntry(String dslId, String text) {}
}
