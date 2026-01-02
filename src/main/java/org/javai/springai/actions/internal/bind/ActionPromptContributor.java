package org.javai.springai.actions.internal.bind;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.javai.springai.actions.api.TypeHandlerRegistry;
import org.javai.springai.actions.api.TypeSpecProvider;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;

/**
 * Emits action specs for inclusion in system prompts.
 */
public final class ActionPromptContributor {

	private static final ObjectMapper mapper = new ObjectMapper();

	private ActionPromptContributor() {
	}

	public static String emit(ActionRegistry registry, ActionDescriptorFilter filter) {
		return emit(registry, filter, null);
	}

	public static String emit(ActionRegistry registry, ActionDescriptorFilter filter,
			TypeHandlerRegistry typeRegistry) {
		if (filter == null) {
			filter = ActionDescriptorFilter.ALL;
		}
		List<ActionDescriptor> selectedDescriptors = registry.getActionDescriptors().stream()
				.filter(filter::include)
				.toList();
		Set<String> selectedIds = selectedDescriptors.stream().map(ActionDescriptor::id).collect(Collectors.toSet());
		List<ActionBinding> selectedBindings = registry.getActionBindings().stream()
				.filter(binding -> selectedIds.contains(binding.id()))
				.collect(Collectors.toList());
		Map<String, ActionDescriptor> descriptorById = selectedDescriptors.stream()
				.collect(Collectors.toMap(ActionDescriptor::id, d -> d));

		return emitJson(selectedBindings, descriptorById, typeRegistry);
	}

	/**
	 * Collects guidance text from type spec providers for any special types used in actions.
	 */
	public static String collectTypeGuidance(ActionRegistry registry, TypeHandlerRegistry typeRegistry) {
		if (typeRegistry == null) {
			return "";
		}
		Set<String> guidanceTexts = new HashSet<>();
		for (ActionDescriptor descriptor : registry.getActionDescriptors()) {
			for (ActionParameterDescriptor param : descriptor.actionParameterSpecs()) {
				try {
					Class<?> paramType = Class.forName(param.typeName());
					Optional<TypeSpecProvider> provider = typeRegistry.specProvider(paramType);
					provider.ifPresent(p -> {
						String guidance = p.guidance();
						if (guidance != null && !guidance.isBlank()) {
							guidanceTexts.add(guidance.trim());
						}
					});
				} catch (ClassNotFoundException e) {
					// Ignore - type not found
				}
			}
		}
		return String.join("\n\n", guidanceTexts);
	}


	private static String emitJson(List<ActionBinding> bindings, Map<String, ActionDescriptor> descriptors,
			TypeHandlerRegistry typeRegistry) {
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
			// Generate schema using type registry for special types, or default for regular types
			generateSchema(binding, descriptor, typeRegistry)
					.ifPresent(schemaNode -> node.set("schema", schemaNode));
			array.add(node);
		}
		return array.toPrettyString();
	}

	/**
	 * Generates a JSON schema for the action binding.
	 * Uses TypeHandlerRegistry to get custom schemas for special types.
	 */
	private static Optional<ObjectNode> generateSchema(ActionBinding binding, ActionDescriptor descriptor,
			TypeHandlerRegistry typeRegistry) {
		if (descriptor == null) {
			return generateDefaultSchema(binding);
		}

		// Check if any parameter has a custom spec provider
		boolean hasCustomTypes = typeRegistry != null && descriptor.actionParameterSpecs().stream()
				.anyMatch(param -> {
					try {
						Class<?> paramType = Class.forName(param.typeName());
						return typeRegistry.specProvider(paramType).isPresent();
					} catch (ClassNotFoundException e) {
						return false;
					}
				});

		if (hasCustomTypes) {
			return Optional.of(generateSchemaWithCustomTypes(descriptor, typeRegistry));
		}

		return generateDefaultSchema(binding);
	}

	/**
	 * Generates schema using custom TypeSpecProviders for special types.
	 */
	private static ObjectNode generateSchemaWithCustomTypes(ActionDescriptor descriptor, 
			TypeHandlerRegistry typeRegistry) {
		ObjectNode schema = mapper.createObjectNode();
		schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");
		ArrayNode required = schema.putArray("required");

		for (ActionParameterDescriptor param : descriptor.actionParameterSpecs()) {
			ObjectNode paramSchema;
			
			try {
				Class<?> paramType = Class.forName(param.typeName());
				Optional<TypeSpecProvider> provider = typeRegistry.specProvider(paramType);
				
				if (provider.isPresent()) {
					// Use custom schema from provider
					paramSchema = provider.get().schema().deepCopy();
				} else {
					// Default: use generic object schema
					paramSchema = mapper.createObjectNode();
					paramSchema.put("type", "object");
					if (!param.description().isBlank()) {
						paramSchema.put("description", param.description());
					}
				}
			} catch (ClassNotFoundException e) {
				paramSchema = mapper.createObjectNode();
				paramSchema.put("type", "object");
			}
			
			properties.set(param.name(), paramSchema);
			required.add(param.name());
		}

		schema.put("additionalProperties", false);
		return schema;
	}

	/**
	 * Generates default schema using Spring AI's schema generator.
	 */
	private static Optional<ObjectNode> generateDefaultSchema(ActionBinding binding) {
		return generateSchemaSafely(binding)
				.map(schemaJson -> {
					try {
						return (ObjectNode) mapper.readTree(schemaJson);
					} catch (Exception e) {
						throw new IllegalStateException("Failed to parse schema for action: " + binding.id(), e);
					}
				});
	}

	private static Optional<String> generateSchemaSafely(ActionBinding binding) {
		Method method = binding.method();
		try {
			return Optional.ofNullable(JsonSchemaGenerator.generateForMethodInput(method));
		}
		catch (Exception ex) {
			// Propagate as runtime to surface prompt-generation failures early
			throw new IllegalStateException("Failed to generate schema for action: " + binding.id(), ex);
		}
	}
}
