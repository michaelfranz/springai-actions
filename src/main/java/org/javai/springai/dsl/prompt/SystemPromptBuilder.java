package org.javai.springai.dsl.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.javai.springai.dsl.act.ActionDescriptor;
import org.javai.springai.dsl.act.ActionDescriptorFilter;
import org.javai.springai.dsl.act.ActionParameterDescriptor;
import org.javai.springai.dsl.act.ActionPromptContributor;
import org.javai.springai.dsl.act.ActionRegistry;
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
			ActionDescriptorFilter filter,
			DslGuidanceProvider guidanceProvider,
			Mode mode) {
		return build(registry, filter, guidanceProvider, mode, List.of(new PlanActionsContextContributor()), Map.of(), null, null);
	}

	/**
	 * Build a system prompt with provider/model-specific guidance if available.
	 */
	public static String build(ActionRegistry registry,
			ActionDescriptorFilter filter,
			DslGuidanceProvider guidanceProvider,
			Mode mode,
			String providerId,
			String modelId) {
		return build(registry, filter, guidanceProvider, mode, List.of(new PlanActionsContextContributor()), Map.of(), providerId, modelId);
	}

	/**
	 * Build a system prompt with optional DSL context contributors and per-DSL context.
	 */
	public static String build(ActionRegistry registry,
			ActionDescriptorFilter filter,
			DslGuidanceProvider guidanceProvider,
			Mode mode,
			List<DslContextContributor> contributors,
			Map<String, Object> dslContext,
			String providerId,
			String modelId) {
		if (filter == null) {
			filter = ActionDescriptorFilter.ALL;
		}
		if (guidanceProvider == null) {
			guidanceProvider = DslGuidanceProvider.NONE;
		}
		if (contributors == null) {
			contributors = List.of();
		}
		if (dslContext == null) {
			dslContext = Map.of();
		}

		Set<String> dslIds = collectDslIds(registry, filter, guidanceProvider);
		for (DslContextContributor contributor : contributors) {
			if (contributor != null && contributor.dslId() != null && !contributor.dslId().isBlank()) {
				dslIds.add(contributor.dslId());
			}
		}
		List<String> orderedDslIds = sortDslIds(dslIds);
		List<GuidanceEntry> dslGuidance = buildDslSection(orderedDslIds, guidanceProvider, providerId, modelId, mode);
		List<ObjectNode> dslSchemas = collectDslSchemas(orderedDslIds, guidanceProvider);

		List<ActionDescriptor> selectedDescriptors = registry.getActionDescriptors().stream()
				.filter(filter::include)
				.toList();
		SystemPromptContext ctx = new SystemPromptContext(registry, selectedDescriptors, filter, dslContext);

		if (mode == Mode.JSON) {
			String actionsSection = buildJsonActions(registry, filter);
			return buildJson(actionsSection, dslGuidance, dslSchemas);
		}

		final List<DslContextContributor> ctxContributors = contributors;
		
		// Generate example plan to insert after sxl-plan section (keeps format examples salient)
		String examplePlan = generateExamplePlan(selectedDescriptors);
		
		String dslSection = dslGuidance.stream()
				.map(g -> {
					StringBuilder section = new StringBuilder("DSL " + g.dslId() + ":\n" + g.text());
					for (DslContextContributor contributor : ctxContributors) {
						if (contributor != null && g.dslId().equals(contributor.dslId())) {
							contributor.contribute(ctx).ifPresent(text -> section.append("\n\n").append(text));
						}
					}
					// Insert EXAMPLE PLAN right after sxl-plan (before other DSLs like sxl-sql)
					if ("sxl-plan".equals(g.dslId()) && !examplePlan.isBlank()) {
						section.append("\n\n").append(examplePlan);
					}
					return section.toString();
				})
				.collect(Collectors.joining("\n\n"));
		
		String systemPrompt = "DSL GUIDANCE:\n" + dslSection;
		
		return systemPrompt;
	}

	private static Set<String> collectDslIds(ActionRegistry registry, ActionDescriptorFilter filter, DslGuidanceProvider guidanceProvider) {
		Set<String> ids = new LinkedHashSet<>();
		List<ActionDescriptor> selected = registry.getActionDescriptors().stream()
				.filter(filter::include)
				.toList();
		for (ActionDescriptor descriptor : selected) {
			descriptor.actionParameterSpecs().forEach(p -> {
				if (p.dslId() != null && !p.dslId().isBlank()) {
					ids.add(p.dslId());
				}
			});
		}
		// Always include universal/plan DSLs if available via grammar source
		if (guidanceProvider instanceof DslGrammarSource source) {
			if (source.grammarFor("sxl-universal").isPresent()) {
				ids.add("sxl-universal");
			}
			if (source.grammarFor("sxl-plan").isPresent()) {
				ids.add("sxl-plan");
			}
		}
		return ids;
	}

	private static List<GuidanceEntry> buildDslSection(List<String> dslIds, DslGuidanceProvider provider, String providerId, String modelId, Mode mode) {
		List<GuidanceEntry> entries = new ArrayList<>();
		for (String id : dslIds) {
			String guidance = provider.guidanceFor(id, providerId, modelId).orElse("(no guidance available)");
			if (mode == Mode.JSON) {
				guidance = "Use JSON structures (not S-expressions) adhering to the provided schemas. DSL id: " + id + ". Guidance: " + guidance;
			} else if (mode == Mode.SXL && provider instanceof DslGrammarSource source) {
				// In SXL mode, prepend the grammar summary to the provider guidance
				String summary = GrammarPromptSummarizer.summarize(Objects.requireNonNull(source.grammarFor(id).orElse(null)));
				// Combine: provider guidance (high-signal) + grammar summary (reference)
				if (!guidance.isBlank() && !"(no guidance available)".equals(guidance)) {
					guidance = guidance + "\n\n" + summary;
				} else {
					guidance = summary;
				}
			}
			// Add compact, DSL-specific scaffolding to reduce hallucinations.
			entries.add(new GuidanceEntry(id, guidance));
		}
		return entries;
	}

	private static List<ObjectNode> collectDslSchemas(List<String> dslIds, DslGuidanceProvider provider) {
		if (!(provider instanceof DslGrammarSource source)) {
			return List.of();
		}
		List<ObjectNode> schemas = new ArrayList<>();
		for (String id : dslIds) {
			source.grammarFor(id).ifPresent(grammar -> schemas.add(SxlGrammarJsonSchemaEmitter.emit(grammar)));
		}
		return schemas;
	}

	private static List<String> sortDslIds(Set<String> dslIds) {
		List<String> ids = new ArrayList<>(dslIds);
		Comparator<String> comparator = Comparator
				.comparingInt((String id) -> {
					if ("sxl-universal".equals(id)) return 0;
					if ("sxl-plan".equals(id)) return 1;
					return 2;
				})
				.thenComparing(Comparator.naturalOrder());
		ids.sort(comparator);
		return ids;
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

	private static String buildJsonActions(ActionRegistry registry, ActionDescriptorFilter filter) {
		return ActionPromptContributor.emit(registry,
				ActionPromptContributor.Mode.JSON,
				filter != null ? filter : ActionDescriptorFilter.ALL);
	}

	private record GuidanceEntry(String dslId, String text) {}

	/**
	 * Generate an example JSON plan from action descriptors with example values.
	 * Takes the first 2-3 actions and the first example from each parameter to build a
	 * concrete plan structure for the LLM to follow.
	 */
	private static String generateExamplePlan(List<ActionDescriptor> descriptors) {
		if (descriptors.isEmpty()) {
			return "";
		}
		
		StringBuilder example = new StringBuilder("EXAMPLE PLAN (JSON format):\n");
		example.append("{\n");
		example.append("  \"message\": \"Example plan description\",\n");
		example.append("  \"steps\": [\n");
		
		// Use first 2-3 actions as examples to keep it concise
		int count = Math.min(3, descriptors.size());
		for (int i = 0; i < count; i++) {
			ActionDescriptor action = descriptors.get(i);
			example.append("    {\n");
			example.append("      \"actionId\": \"").append(action.id()).append("\",\n");
			example.append("      \"description\": \"").append(escapeJson(action.description())).append("\",\n");
			example.append("      \"parameters\": {");
			
			// Add parameters with examples
			List<ActionParameterDescriptor> params = action.actionParameterSpecs();
			if (params != null && !params.isEmpty()) {
				example.append("\n");
				for (int j = 0; j < params.size(); j++) {
					ActionParameterDescriptor param = params.get(j);
					String exampleValue = getExampleValue(param);
					example.append("        \"").append(param.name()).append("\": ").append(exampleValue);
					if (j < params.size() - 1) {
						example.append(",");
					}
					example.append("\n");
				}
				example.append("      ");
			}
			example.append("}\n");
			example.append("    }");
			if (i < count - 1) {
				example.append(",");
			}
			example.append("\n");
		}
		
		example.append("  ]\n");
		example.append("}\n");
		return example.toString();
	}

	/**
	 * Get an example value for a parameter in JSON format.
	 * Uses explicit examples if provided, otherwise generates an automatic example.
	 */
	private static String getExampleValue(ActionParameterDescriptor param) {
		// Use explicit example if provided
		if (param.examples() != null && param.examples().length > 0) {
			String ex = param.examples()[0];
			// If it looks like a JSON object/array, return as-is
			if (ex.trim().startsWith("{") || ex.trim().startsWith("[")) {
				return ex;
			}
			// If it's an S-expression, wrap in quotes
			if (ex.trim().startsWith("(")) {
				return "\"" + escapeJson(ex) + "\"";
			}
			// For plain strings, quote them
			return "\"" + escapeJson(ex) + "\"";
		}
		
		// Generate automatic example for DSL-typed parameters (Query)
		String dslId = param.dslId();
		if (dslId != null && !dslId.isBlank()) {
			if ("sxl-sql".equalsIgnoreCase(dslId)) {
				// Generate a canonical SQL DSL example as a string
				return "\"(Q (F table_name t) (S t.column_name))\"";
			}
			// Generic embedded DSL example
			return "\"(<" + param.name() + "-expression>)\"";
		}
		
		// For complex types (objects), show JSON structure placeholder
		String typeId = param.typeId();
		if (typeId != null && (typeId.contains(".") || Character.isUpperCase(typeId.charAt(0)))) {
			return "{ \"...\": \"...\" }";
		}
		
		// Fallback placeholder for simple types
		return "\"<" + param.name() + ">\"";
	}

	/**
	 * Escape special characters for JSON strings.
	 */
	private static String escapeJson(String s) {
		if (s == null) return "";
		return s.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\n", "\\n")
				.replace("\r", "\\r")
				.replace("\t", "\\t");
	}
}
