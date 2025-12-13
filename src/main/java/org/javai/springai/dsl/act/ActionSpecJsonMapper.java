package org.javai.springai.dsl.act;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/**
 * Utility to convert {@link ActionSpec} into JSON suitable for LLM prompts or diagnostics.
 */
public final class ActionSpecJsonMapper {

	private static final ObjectMapper mapper = new ObjectMapper();

	private ActionSpecJsonMapper() {
	}

	public static ObjectNode toJson(ActionSpec spec) {
		ObjectNode node = mapper.createObjectNode();
		node.put("id", spec.id());
		node.put("description", spec.description());
		ArrayNode params = node.putArray("parameters");
		for (ActionParameterSpec p : spec.actionParameterSpecs()) {
			ObjectNode pNode = params.addObject();
			pNode.put("name", p.name());
			pNode.put("type", p.typeName());
			pNode.put("typeId", p.typeId());
			pNode.put("description", p.description());
			if (p.dslId() != null) {
				pNode.put("dslId", p.dslId());
			}
		}
		return node;
	}

	public static ArrayNode toJsonArray(List<ActionSpec> specs) {
		ArrayNode array = mapper.createArrayNode();
		for (ActionSpec spec : specs) {
			array.add(toJson(spec));
		}
		return array;
	}
}
