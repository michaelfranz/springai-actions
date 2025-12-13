package org.javai.springai.actions.definition;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.Method;

/**
 * @deprecated Legacy view of an action for LLMs. Prefer {@link org.javai.springai.dsl.act.ActionSpec}.
 */
@Deprecated
public record ActionDefinition(
		String name,
		String description,
		JsonNode argumentSchema,
		Object bean,
		Method method
) {}