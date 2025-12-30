package org.javai.springai.actions.internal.bind;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;

/**
 * Emits action specs for inclusion in system prompts, in either SXL or JSON style.
 */
public final class ActionPromptContributor {

	public enum Mode {
		SXL, JSON
	}

	private static final ObjectMapper mapper = new ObjectMapper();

	private ActionPromptContributor() {
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
			case SXL -> emitSxl(selectedDescriptors, selectedBindings);
			case JSON -> emitJson(selectedBindings, descriptorById);
		};
	}

	private static String emitSxl(List<ActionDescriptor> specs, List<ActionBinding> bindings) {
		// For SXL mode, emit only the action specs without JSON schemas (which are noise)
		// The example plan generation will provide concrete examples
		return specs.stream()
				.map(descriptor -> {
					String base = descriptor.toSxl();
					String constraints = descriptor.renderConstraints();
					String example = descriptor.example();
					if (example == null || example.isBlank()) {
						example = defaultExample(descriptor);
					}
					String pendingExample = defaultPendingExample(descriptor);
					StringBuilder sb = new StringBuilder(base);
					if (!constraints.isBlank()) {
						sb.append("\nConstraints:\n").append(constraints);
					}
					sb.append("\nExample: ").append(example.trim());
					sb.append("\nPending Example: ").append(pendingExample.trim());
					return sb.toString();
				})
				.collect(Collectors.joining("\n\n"));
	}

	private static String defaultExample(ActionDescriptor d) {
		// Provide a canonical plan-shaped exemplar using PS + PA params (no EMBED unless the action DSL id is specified).
		String params = d.actionParameterSpecs().stream()
				.map(ActionPromptContributor::renderParameterExample)
				.collect(Collectors.joining(" "));
		return "(P \"Example for " + d.id() + "\" (PS " + d.id()
				+ (params.isEmpty() ? "" : " " + params) + "))";
	}

	private static String defaultPendingExample(ActionDescriptor d) {
		String firstParam = d.actionParameterSpecs().isEmpty()
				? "value"
				: d.actionParameterSpecs().getFirst().name();
		String otherParams = d.actionParameterSpecs().stream()
				.skip(1)
				.map(p -> "(PA " + p.name() + " \"<" + p.name() + ">\")")
				.collect(Collectors.joining(" "));
		String pending = "(PENDING " + firstParam + " \"Provide " + firstParam + "\")";
		String params = pending + (otherParams.isEmpty() ? "" : " " + otherParams);
		return "(P \"Example pending for " + d.id() + "\" (PS " + d.id()
				+ (params.isEmpty() ? "" : " " + params) + "))";
	}

	private static String renderParameterExample(ActionParameterDescriptor p) {
		if (isCollectionOrArray(p.typeName())) {
			return "(PA " + p.name() + " \"<" + p.name() + " item1>\" \"<" + p.name() + " item2>\")";
		}
		return "(PA " + p.name() + " \"<" + p.name() + ">\")";
	}

	private static boolean isCollectionOrArray(String typeName) {
		if (typeName == null || typeName.isBlank()) {
			return false;
		}
		if (typeName.startsWith("[")) {
			return true;
		}
		try {
			Class<?> clazz = Class.forName(typeName);
			return Collection.class.isAssignableFrom(clazz);
		}
		catch (ClassNotFoundException ex) {
			return typeName.contains("List") || typeName.contains("Collection");
		}
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
			String schemaJson = generateSchemaSafely(binding);
			try {
				if (schemaJson != null) {
					node.set("schema", mapper.readTree(schemaJson));
				}
			} catch (Exception e) {
				throw new IllegalStateException("Failed to parse schema for action: " + binding.id(), e);
			}
			array.add(node);
		}
		return array.toPrettyString();
	}

	private static String generateSchemaSafely(ActionBinding binding) {
		Method method = binding.method();
		if (method == null) {
			throw new IllegalStateException("Action binding missing method for action id: " + binding.id());
		}
		try {
			return JsonSchemaGenerator.generateForMethodInput(method);
		}
		catch (Exception ex) {
			// Propagate as runtime to surface prompt-generation failures early
			throw new IllegalStateException("Failed to generate schema for action: " + binding.id(), ex);
		}
	}
}
