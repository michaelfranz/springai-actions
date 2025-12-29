package org.javai.springai.dsl.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.javai.springai.dsl.act.ActionDescriptor;
import org.javai.springai.dsl.act.ActionDescriptorFilter;
import org.javai.springai.dsl.act.ActionParameterDescriptor;
import org.javai.springai.dsl.act.ActionPromptContributor;
import org.javai.springai.dsl.act.ActionRegistry;

/**
 * Builds system prompts for the LLM containing action specifications.
 * 
 * <p>Plans use JSON format exclusively. SQL queries use standard ANSI SQL.</p>
 */
public final class SystemPromptBuilder {

	private SystemPromptBuilder() {
	}

	/**
	 * Build a system prompt for the given actions.
	 * 
	 * @param registry action registry containing all available actions
	 * @param filter selection filter to limit actions for this prompt
	 * @return JSON system prompt containing action specifications
	 */
	public static String build(ActionRegistry registry, ActionDescriptorFilter filter) {
		return build(registry, filter, List.of(), Map.of());
	}

	/**
	 * Build a system prompt with prompt contributors and context.
	 * 
	 * @param registry action registry containing all available actions
	 * @param filter selection filter to limit actions for this prompt
	 * @param contributors prompt contributors that add dynamic context
	 * @param context context data accessible to contributors
	 * @return JSON system prompt containing action specifications
	 */
	public static String build(ActionRegistry registry,
			ActionDescriptorFilter filter,
			List<PromptContributor> contributors,
			Map<String, Object> context) {
		if (filter == null) {
			filter = ActionDescriptorFilter.ALL;
		}
		if (contributors == null) {
			contributors = List.of();
		}
		if (context == null) {
			context = Map.of();
		}

		String actionsSection = buildJsonActions(registry, filter);
		return buildJson(actionsSection);
	}

	// ========== Private helpers ==========

	private static String buildJson(String actionsJsonArray) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			ObjectNode root = mapper.createObjectNode();
			root.set("actions", mapper.readTree(actionsJsonArray));
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

	/**
	 * Generate an example JSON plan from action descriptors with example values.
	 */
	public static String generateExamplePlan(List<ActionDescriptor> descriptors) {
		if (descriptors == null || descriptors.isEmpty()) {
			return "";
		}

		StringBuilder example = new StringBuilder("EXAMPLE PLAN (JSON format):\n");
		example.append("{\n");
		example.append("  \"message\": \"Example plan description\",\n");
		example.append("  \"steps\": [\n");

		int count = Math.min(3, descriptors.size());
		for (int i = 0; i < count; i++) {
			ActionDescriptor action = descriptors.get(i);
			example.append("    {\n");
			example.append("      \"actionId\": \"").append(action.id()).append("\",\n");
			example.append("      \"description\": \"").append(escapeJson(action.description())).append("\",\n");
			example.append("      \"parameters\": {");

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

	private static String getExampleValue(ActionParameterDescriptor param) {
		if (param.examples() != null && param.examples().length > 0) {
			String ex = param.examples()[0];
			if (ex.trim().startsWith("{") || ex.trim().startsWith("[")) {
				return ex;
			}
			return "\"" + escapeJson(ex) + "\"";
		}

		String dslId = param.dslId();
		if (dslId != null && !dslId.isBlank()) {
			if ("sql-query".equalsIgnoreCase(dslId)) {
				return "\"SELECT column_name FROM table_name WHERE condition = 'value'\"";
			}
			return "\"<" + param.name() + ">\"";
		}

		String typeId = param.typeId();
		if (typeId != null && (typeId.contains(".") || Character.isUpperCase(typeId.charAt(0)))) {
			return "{ \"...\": \"...\" }";
		}

		return "\"<" + param.name() + ">\"";
	}

	private static String escapeJson(String s) {
		if (s == null) return "";
		return s.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\n", "\\n")
				.replace("\r", "\\r")
				.replace("\t", "\\t");
	}
}
