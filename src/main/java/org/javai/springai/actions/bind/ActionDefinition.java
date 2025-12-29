package org.javai.springai.actions.bind;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Represents the application's view of an action.
 * It's what we look up when the LLM tells us this action ought to be invoked
 * @param id of the action - what the LLM uses to identify the action
 * @param description of the action
 * @param bean the object where the action is implemented
 * @param method the method that implements the action
 */
public record ActionDefinition(
		String id,
		String description,
		Object bean,
		Method method,
		List<ActionParameterDescriptor> actionParameterDefinitions
) {
}