package org.javai.springai.actions.definition;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.Method;

public record ActionDefinition(
		String name,
		String description,
		JsonNode argumentSchema,
		Object bean,
		Method method
) {}