package org.javai.springai.actions.execution;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Simple holder for the values that can be substituted into annotation
 * templates. The bindings are derived from the JSON arguments of a plan step.
 */
final class TemplateBindings {

	private final Map<String, String> values;

	private TemplateBindings(Map<String, String> values) {
		this.values = values;
	}

	static TemplateBindings from(JsonNode arguments) {
		Map<String, String> collected = new HashMap<>();
		if (arguments != null && arguments.isObject()) {
			Iterator<String> fieldNames = arguments.fieldNames();
			while (fieldNames.hasNext()) {
				String name = fieldNames.next();
				collect(name, arguments.get(name), collected);
			}
		}
		return new TemplateBindings(collected);
	}

	static TemplateBindings fromMap(Map<String, String> values) {
		return new TemplateBindings(new HashMap<>(values));
	}

	Optional<String> resolve(String key) {
		return Optional.ofNullable(values.get(key));
	}

	private static void collect(String currentPath, JsonNode node, Map<String, String> collector) {
		if (node == null || currentPath == null || currentPath.isBlank()) {
			return;
		}

		if (node.isValueNode()) {
			collector.put(currentPath, node.asText());
			return;
		}

		if (node.isObject()) {
			Iterator<String> fieldNames = node.fieldNames();
			while (fieldNames.hasNext()) {
				String name = fieldNames.next();
				String nextPath = currentPath + "." + name;
				collect(nextPath, node.get(name), collector);
			}
		}
	}
}

