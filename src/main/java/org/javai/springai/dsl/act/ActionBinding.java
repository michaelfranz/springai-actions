package org.javai.springai.dsl.act;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Runtime binding for executing an action plan step.
 */
public record ActionBinding(
		String id,
		String description,
		Object bean,
		Method method,
		List<ActionParameterSpec> parameters
) {
}

