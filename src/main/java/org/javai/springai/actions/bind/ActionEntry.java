package org.javai.springai.actions.bind;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Single source of truth for a registered action; can produce both the LLM descriptor and runtime binding.
 */
public record ActionEntry(
		String id,
		String description,
		List<ActionParameterDescriptor> parameters,
		String example,
		Object bean,
		Method method,
		String contextKey
) {
	public ActionDescriptor descriptor() {
		return new ActionDescriptor(id, description, parameters, example);
	}

	public ActionBinding binding() {
		return new ActionBinding(id, description, bean, method, parameters, contextKey);
	}
}

