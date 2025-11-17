package org.javai.springai.actions.definition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.javai.springai.actions.api.Action;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;

public final class ActionDefinitionFactory {

	private static final ObjectMapper mapper = new ObjectMapper();

	public static List<ActionDefinition> from(Object bean) {
		List<ActionDefinition> actions = new ArrayList<>();

		for (Method method : bean.getClass().getMethods()) {
			Action action = method.getAnnotation(Action.class);
			if (action == null) continue;

			String name = method.getName();
			String description = !action.description().isBlank()
					? action.description()
					: "Action " + name;

			JsonNode schema = buildSchemaFor(method);

			actions.add(new ActionDefinition(
					name,
					description,
					schema,
					bean,
					method
			));
		}

		return actions;
	}

	private static JsonNode buildSchemaFor(Method method) {
		try {
			String schemaJson = JsonSchemaGenerator.generateForMethodInput(method);
			return mapper.readTree(schemaJson);
		} catch (Exception e) {
			throw new RuntimeException("Failed to build schema for method: " + method, e);
		}
	}
}