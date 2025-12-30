package org.javai.springai.actions.internal.bind;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/**
 * Utility to convert {@link ActionDescriptor} into JSON suitable for LLM prompts or diagnostics.
 */
public final class ActionDescriptorJsonMapper {

	private static final ObjectMapper mapper = new ObjectMapper();

	private ActionDescriptorJsonMapper() {
	}

	public static ObjectNode toJson(ActionDescriptor descriptor) {
		ObjectNode node = mapper.createObjectNode();
		node.put("id", descriptor.id());
		node.put("description", descriptor.description());
		ArrayNode params = node.putArray("parameters");
		for (ActionParameterDescriptor param : descriptor.actionParameterSpecs()) {
			ObjectNode p = params.addObject();
			p.put("name", param.name());
			p.put("type", param.typeName());
			p.put("typeId", param.typeId());
			p.put("description", param.description());
			if (param.dslId() != null) {
				p.put("dslId", param.dslId());
			}
		}
		return node;
	}

	public static ArrayNode toJsonArray(List<ActionDescriptor> descriptors) {
		ArrayNode array = mapper.createArrayNode();
		for (ActionDescriptor spec : descriptors) {
			array.add(toJson(spec));
		}
		return array;
	}
}

