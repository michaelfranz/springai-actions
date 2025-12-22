package org.javai.springai.dsl.act;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;

/**
 * Emits action specs for inclusion in system prompts, in either SXL or JSON style.
 */
public final class ActionPromptEmitter {

	public enum Mode {
		SXL, JSON
	}

	private static final ObjectMapper mapper = new ObjectMapper();

	private ActionPromptEmitter() {
	}

	public static String emit(ActionRegistry registry, Mode mode) {
		return emit(registry, mode, ActionDescriptorFilter.ALL);
	}

	public static String emit(ActionRegistry registry, Mode mode, ActionDescriptorFilter filter) {
		if (filter == null) {
			filter = ActionDescriptorFilter.ALL;
		}
		List<ActionDescriptor> selectedDescriptors = registry.getActionDescriptors().stream()
				.filter(filter::include)
				.collect(Collectors.toList());
		Set<String> selectedIds = selectedDescriptors.stream().map(ActionDescriptor::id).collect(Collectors.toSet());
		List<ActionBinding> selectedBindings = registry.getActionBindings().stream()
				.filter(binding -> selectedIds.contains(binding.id()))
				.collect(Collectors.toList());
		Map<String, ActionDescriptor> descriptorById = selectedDescriptors.stream()
				.collect(Collectors.toMap(ActionDescriptor::id, d -> d));

		return switch (mode) {
			case SXL -> emitSxl(selectedDescriptors);
			case JSON -> emitJson(selectedBindings, descriptorById);
		};
	}

	private static String emitSxl(List<ActionDescriptor> specs) {
		return specs.stream()
				.map(ActionDescriptor::toSxl)
				.collect(Collectors.joining("\n"));
	}

	private static String emitJson(List<ActionBinding> bindings, Map<String, ActionDescriptor> descriptors) {
		ArrayNode array = mapper.createArrayNode();
		for (ActionBinding binding : bindings) {
			ObjectNode node = mapper.createObjectNode();
			node.put("id", binding.id());
			node.put("description", binding.description());
			ActionDescriptor descriptor = descriptors.get(binding.id());
			if (descriptor != null) {
				ArrayNode params = ActionDescriptorJsonMapper.toJson(descriptor).withArray("parameters");
				node.set("parameters", params);
			}
			// Add Spring AI-style schema for the method input
			Method method = binding.method();
			if (method == null) {
				throw new IllegalStateException("Action binding missing method for action id: " + binding.id());
			}
			String schemaJson = JsonSchemaGenerator.generateForMethodInput(method);
			try {
				node.set("schema", mapper.readTree(schemaJson));
			} catch (Exception e) {
				throw new IllegalStateException("Failed to parse schema for action: " + binding.id(), e);
			}
			array.add(node);
		}
		return array.toPrettyString();
	}
}
