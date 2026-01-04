package org.javai.springai.actions.internal.bind;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
 * 
 * <p>This class generates JSON Schema output contracts for LLM plan generation.
 * The schema uses "hardcoded complete action shapes" where each action is a 
 * self-contained definition with its actionId bound via {@code const} and 
 * parameters inlined. This eliminates ambiguity about which parameters apply 
 * to which action.</p>
 */
public final class ActionPromptContributor {

	private static final ObjectMapper mapper = new ObjectMapper();
	private static final ObjectMapper prettyMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
	
	private static final String SCHEMA_HEADER = """
			════════════════════════════════════════════════════════════════════════════════
			OUTPUT SCHEMA (JSON Schema Draft 2020-12)
			Your response MUST conform to this schema. Do not add fields not in the schema.
			════════════════════════════════════════════════════════════════════════════════
			""";
	
	private static final String EXAMPLE_HEADER = """
			════════════════════════════════════════════════════════════════════════════════
			EXAMPLE
			════════════════════════════════════════════════════════════════════════════════
			""";

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

	// ═══════════════════════════════════════════════════════════════════════════════
	// NEW: Output Schema Generation (Hardcoded Complete Action Shapes)
	// ═══════════════════════════════════════════════════════════════════════════════

	/**
	 * Emits a complete JSON Schema for the Plan output structure.
	 * 
	 * <p>The schema uses "hardcoded complete action shapes" where each action gets
	 * its own definition with {@code actionId} bound via {@code const} and parameters
	 * inlined. This eliminates any ambiguity about which parameters apply to which action.</p>
	 * 
	 * <p>PendingStep, NoActionStep, and ErrorStep are generic format documentation.</p>
	 *
	 * @param registry the action registry containing all available actions
	 * @param filter optional filter to limit which actions are included
	 * @param typeRegistry optional type registry for custom type handling
	 * @return JSON Schema as a string
	 */
	public static String emitOutputSchema(ActionRegistry registry, ActionDescriptorFilter filter,
			TypeHandlerRegistry typeRegistry) {
		ObjectNode schema = buildOutputSchema(registry, filter, typeRegistry);
		try {
			return prettyMapper.writeValueAsString(schema);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize output schema", e);
		}
	}

	/**
	 * Emits combined schema and exemplars for use in system prompts.
	 *
	 * @param registry the action registry
	 * @param filter optional filter to limit which actions are included
	 * @return formatted prompt contribution with schema and example
	 */
	public static String emitExemplar(ActionRegistry registry, ActionDescriptorFilter filter) {
		List<ActionDescriptor> descriptors = getFilteredDescriptors(registry, filter);
		// Use the first action for the example
		ActionDescriptor first = descriptors.getFirst();
		return EXAMPLE_HEADER + buildExemplar(first) + "\n";
	}

	/**
	 * Builds the complete JSON Schema object.
	 */
	private static ObjectNode buildOutputSchema(ActionRegistry registry, ActionDescriptorFilter filter,
			TypeHandlerRegistry typeRegistry) {
		List<ActionDescriptor> descriptors = getFilteredDescriptors(registry, filter);
		
		ObjectNode schema = mapper.createObjectNode();
		schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
		schema.put("type", "object");
		schema.put("additionalProperties", false);
		
		// Required fields
		ArrayNode required = schema.putArray("required");
		required.add("message");
		required.add("steps");
		
		// Properties
		ObjectNode properties = schema.putObject("properties");
		
		// message property
		ObjectNode messageProp = properties.putObject("message");
		messageProp.put("type", "string");
		messageProp.put("minLength", 1);
		messageProp.put("maxLength", 160);
		messageProp.put("description", "Short UI summary of the plan");
		
		// steps property
		ObjectNode stepsProp = properties.putObject("steps");
		stepsProp.put("type", "array");
		stepsProp.put("minItems", 1);
		stepsProp.putObject("items").put("$ref", "#/$defs/Step");
		
		// $defs
		ObjectNode defs = schema.putObject("$defs");
		
		// Step oneOf
		ObjectNode stepDef = defs.putObject("Step");
		ArrayNode oneOf = stepDef.putArray("oneOf");
		
		// Add per-action ActionStep variants
		for (ActionDescriptor descriptor : descriptors) {
			String defName = toPascalCase(descriptor.id()) + "Action";
			oneOf.addObject().put("$ref", "#/$defs/" + defName);
		}
		
		// Add generic step types
		oneOf.addObject().put("$ref", "#/$defs/PendingStep");
		oneOf.addObject().put("$ref", "#/$defs/NoActionStep");
		oneOf.addObject().put("$ref", "#/$defs/ErrorStep");
		
		// Generate per-action ActionStep definitions
		for (ActionDescriptor descriptor : descriptors) {
			String defName = toPascalCase(descriptor.id()) + "Action";
			defs.set(defName, buildActionStepDef(descriptor, typeRegistry));
		}
		
		// Add generic PendingStep
		defs.set("PendingStep", buildPendingStepDef());
		
		// Add NoActionStep
		defs.set("NoActionStep", buildNoActionStepDef());
		
		// Add ErrorStep
		defs.set("ErrorStep", buildErrorStepDef());
		
		return schema;
	}

	/**
	 * Builds an ActionStep definition for a specific action.
	 * The actionId is bound via const, and parameters are inlined.
	 */
	private static ObjectNode buildActionStepDef(ActionDescriptor descriptor, TypeHandlerRegistry typeRegistry) {
		ObjectNode def = mapper.createObjectNode();
		def.put("type", "object");
		def.put("additionalProperties", false);
		
		ArrayNode required = def.putArray("required");
		required.add("actionId");
		required.add("description");
		required.add("parameters");
		
		ObjectNode properties = def.putObject("properties");
		
		// actionId bound via const
		properties.putObject("actionId").put("const", descriptor.id());
		
		// description
		ObjectNode descProp = properties.putObject("description");
		descProp.put("type", "string");
		descProp.put("minLength", 1);
		descProp.put("maxLength", 200);
		
		// parameters - inlined with full constraints
		properties.set("parameters", buildParametersSchema(descriptor, typeRegistry));
		
		return def;
	}

	/**
	 * Builds the parameters schema for an action, with full constraints from @ActionParam.
	 */
	private static ObjectNode buildParametersSchema(ActionDescriptor descriptor, TypeHandlerRegistry typeRegistry) {
		ObjectNode paramSchema = mapper.createObjectNode();
		paramSchema.put("type", "object");
		paramSchema.put("additionalProperties", false);
		
		ArrayNode required = paramSchema.putArray("required");
		ObjectNode properties = paramSchema.putObject("properties");
		
		for (ActionParameterDescriptor param : descriptor.actionParameterSpecs()) {
			required.add(param.name());
			properties.set(param.name(), buildParameterPropertySchema(param, typeRegistry));
		}
		
		return paramSchema;
	}

	/**
	 * Builds the schema for a single parameter property.
	 */
	private static ObjectNode buildParameterPropertySchema(ActionParameterDescriptor param, 
			TypeHandlerRegistry typeRegistry) {
		ObjectNode propSchema = mapper.createObjectNode();
		
		// Check for custom type handler first
		if (typeRegistry != null) {
			try {
				Class<?> paramType = Class.forName(param.typeName());
				Optional<TypeSpecProvider> provider = typeRegistry.specProvider(paramType);
				if (provider.isPresent()) {
					return provider.get().schema().deepCopy();
				}
			} catch (ClassNotFoundException e) {
				// Fall through to default handling
			}
		}
		
		// Map Java type to JSON Schema type
		String jsonType = mapToJsonSchemaType(param.typeId());
		propSchema.put("type", jsonType);
		
		// Add enum constraint if allowedValues present
		if (param.allowedValues() != null && param.allowedValues().length > 0) {
			ArrayNode enumNode = propSchema.putArray("enum");
			for (String value : param.allowedValues()) {
				enumNode.add(value);
			}
		}
		
		// Add pattern constraint if allowedRegex present
		if (param.allowedRegex() != null && !param.allowedRegex().isEmpty()) {
			propSchema.put("pattern", param.allowedRegex());
		}
		
		// Add description
		if (param.description() != null && !param.description().isEmpty()) {
			propSchema.put("description", param.description());
		}
		
		// Add examples if present
		if (param.examples() != null && param.examples().length > 0) {
			ArrayNode examplesNode = propSchema.putArray("examples");
			for (String example : param.examples()) {
				examplesNode.add(example);
			}
		}
		
		return propSchema;
	}

	/**
	 * Builds the generic PendingStep definition.
	 */
	private static ObjectNode buildPendingStepDef() {
		ObjectNode def = mapper.createObjectNode();
		def.put("type", "object");
		def.put("additionalProperties", false);
		
		ArrayNode required = def.putArray("required");
		required.add("actionId");
		required.add("description");
		required.add("status");
		required.add("pendingParams");
		required.add("providedParams");
		
		ObjectNode properties = def.putObject("properties");
		
		// actionId - generic string (not bound to specific action)
		properties.putObject("actionId").put("type", "string");
		
		// description
		ObjectNode descProp = properties.putObject("description");
		descProp.put("type", "string");
		descProp.put("minLength", 1);
		descProp.put("maxLength", 200);
		
		// status - const "pending"
		properties.putObject("status").put("const", "pending");
		
		// pendingParams
		ObjectNode pendingParamsProp = properties.putObject("pendingParams");
		pendingParamsProp.put("type", "array");
		pendingParamsProp.put("minItems", 1);
		ObjectNode itemSchema = pendingParamsProp.putObject("items");
		itemSchema.put("type", "object");
		itemSchema.put("additionalProperties", false);
		ArrayNode itemRequired = itemSchema.putArray("required");
		itemRequired.add("name");
		itemRequired.add("prompt");
		ObjectNode itemProps = itemSchema.putObject("properties");
		itemProps.putObject("name").put("type", "string");
		ObjectNode promptProp = itemProps.putObject("prompt");
		promptProp.put("type", "string");
		promptProp.put("minLength", 1);
		promptProp.put("maxLength", 200);
		
		// providedParams - generic object
		properties.putObject("providedParams").put("type", "object");
		
		return def;
	}

	/**
	 * Builds the NoActionStep definition.
	 */
	private static ObjectNode buildNoActionStepDef() {
		ObjectNode def = mapper.createObjectNode();
		def.put("type", "object");
		def.put("additionalProperties", false);
		
		ArrayNode required = def.putArray("required");
		required.add("noAction");
		required.add("reason");
		
		ObjectNode properties = def.putObject("properties");
		properties.putObject("noAction").put("const", true);
		ObjectNode reasonProp = properties.putObject("reason");
		reasonProp.put("type", "string");
		reasonProp.put("minLength", 1);
		reasonProp.put("maxLength", 220);
		
		return def;
	}

	/**
	 * Builds the ErrorStep definition.
	 */
	private static ObjectNode buildErrorStepDef() {
		ObjectNode def = mapper.createObjectNode();
		def.put("type", "object");
		def.put("additionalProperties", false);
		
		ArrayNode required = def.putArray("required");
		required.add("error");
		required.add("reason");
		
		ObjectNode properties = def.putObject("properties");
		properties.putObject("error").put("const", true);
		ObjectNode reasonProp = properties.putObject("reason");
		reasonProp.put("type", "string");
		reasonProp.put("minLength", 1);
		reasonProp.put("maxLength", 220);
		
		return def;
	}

	/**
	 * Builds a compact JSON exemplar for the given action.
	 */
	private static String buildExemplar(ActionDescriptor descriptor) {
		ObjectNode example = mapper.createObjectNode();
		example.put("message", truncateDescription(descriptor.description(), 40));
		
		ArrayNode steps = example.putArray("steps");
		ObjectNode step = steps.addObject();
		step.put("actionId", descriptor.id());
		step.put("description", truncateDescription(descriptor.description(), 50));
		
		ObjectNode params = step.putObject("parameters");
		for (ActionParameterDescriptor param : descriptor.actionParameterSpecs()) {
			params.put(param.name(), getExampleValue(param));
		}
		
		try {
			return mapper.writeValueAsString(example);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize exemplar", e);
		}
	}

	/**
	 * Gets an example value for a parameter.
	 */
	private static String getExampleValue(ActionParameterDescriptor param) {
		// Use explicit examples first
		if (param.examples() != null && param.examples().length > 0) {
			return param.examples()[0];
		}
		// Fall back to first allowed value
		if (param.allowedValues() != null && param.allowedValues().length > 0) {
			return param.allowedValues()[0];
		}
		// Generate based on type
		return "<" + param.name() + ">";
	}

	/**
	 * Maps Java type identifiers to JSON Schema types.
	 */
	private static String mapToJsonSchemaType(String typeId) {
		if (typeId == null) return "string";
		return switch (typeId.toLowerCase()) {
			case "int", "integer", "long", "short", "byte" -> "integer";
			case "double", "float", "bigdecimal" -> "number";
			case "boolean" -> "boolean";
			case "list", "array", "collection", "set" -> "array";
			case "map", "object" -> "object";
			default -> "string";
		};
	}

	/**
	 * Converts camelCase to PascalCase.
	 */
	private static String toPascalCase(String s) {
		if (s == null || s.isEmpty()) return s;
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	/**
	 * Truncates a description for use in examples.
	 */
	private static String truncateDescription(String desc, int maxLength) {
		if (desc == null) return "Action";
		String trimmed = desc.trim();
		if (trimmed.length() <= maxLength) return trimmed;
		return trimmed.substring(0, maxLength - 3) + "...";
	}

	/**
	 * Gets filtered action descriptors.
	 */
	private static List<ActionDescriptor> getFilteredDescriptors(ActionRegistry registry, ActionDescriptorFilter filter) {
		if (filter == null) {
			filter = ActionDescriptorFilter.ALL;
		}
		return registry.getActionDescriptors().stream()
				.filter(filter::include)
				.toList();
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
