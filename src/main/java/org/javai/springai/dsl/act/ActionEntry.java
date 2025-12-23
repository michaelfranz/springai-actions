package org.javai.springai.dsl.act;

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
		Method method
) {
	public ActionDescriptor descriptor() {
		return new ActionDescriptor(id, description, parameters, example);
	}

	public ActionBinding binding() {
		return new ActionBinding(id, description, bean, method, parameters);
	}
}

