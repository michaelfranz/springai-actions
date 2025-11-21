package org.javai.springai.actions.definition;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.Method;

/**
 * Represents a view of an action from the perspective of the LLM model.
 * @param name of the action
 * @param description of the action
 * @param argumentSchema the shape of the arguments that the LLM needs to provide
 * @param bean the object where the action is implemented
 * @param method the method that implements the action
 */
public record ActionDefinition(
		String name,
		String description,
		JsonNode argumentSchema,
		Object bean,
		Method method
) {}