package org.javai.springai.dsl.act;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
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
		return emit(registry, mode, ActionSpecFilter.ALL);
	}

	public static String emit(ActionRegistry registry, Mode mode, ActionSpecFilter filter) {
		if (filter == null) {
			filter = ActionSpecFilter.ALL;
		}
		List<ActionSpec> selectedSpecs = registry.getActionSpecs().stream()
				.filter(filter::include)
				.collect(Collectors.toList());
		Set<String> selectedIds = selectedSpecs.stream().map(ActionSpec::id).collect(Collectors.toSet());
		List<ActionDefinition> selectedDefs = registry.getActionDefinitions().stream()
				.filter(def -> selectedIds.contains(def.id()))
				.collect(Collectors.toList());

		return switch (mode) {
			case SXL -> emitSxl(selectedSpecs);
			case JSON -> emitJson(selectedDefs);
		};
	}

	private static String emitSxl(List<ActionSpec> specs) {
		return specs.stream()
				.map(ActionSpec::toSxl)
				.collect(Collectors.joining("\n"));
	}

	private static String emitJson(List<ActionDefinition> defs) {
		ArrayNode array = mapper.createArrayNode();
		for (ActionDefinition def : defs) {
			ObjectNode node = mapper.createObjectNode();
			node.put("id", def.id());
			node.put("description", def.description());
			// Parameters via ActionSpecJsonMapper (ensures typeId/dslId included)
			ActionSpec spec = findSpec(def);
			ArrayNode params = ActionSpecJsonMapper.toJson(spec).withArray("parameters");
			node.set("parameters", params);
			// Add Spring AI-style schema for the method input
			String schemaJson = JsonSchemaGenerator.generateForMethodInput(def.method());
			try {
				node.set("schema", mapper.readTree(schemaJson));
			} catch (Exception e) {
				throw new IllegalStateException("Failed to parse schema for action: " + def.id(), e);
			}
			array.add(node);
		}
		return array.toPrettyString();
	}

	private static ActionSpec findSpec(ActionDefinition def) {
		// Rebuild a spec from the definition's parameter specs to ensure alignment
		return new ActionSpec(def.id(), def.description(), def.actionParameterDefinitions());
	}
}
