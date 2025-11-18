package org.javai.springai.actions.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.api.FromContext;

class ActionArgumentBinder {

	private final ObjectMapper mapper = new ObjectMapper();

	public Object[] bindArguments(Method method,
								  JsonNode argNode,
								  ActionContext ctx) {

		Parameter[] params = method.getParameters();
		Object[] javaArgs = new Object[params.length];

		for (int i = 0; i < params.length; i++) {
			Parameter p = params[i];

			// 1) Direct ActionContext injection
			if (p.getType().equals(ActionContext.class)) {
				javaArgs[i] = ctx;
				continue;
			}

			// 2) Context-driven parameters (via @FromContext)
			FromContext fc = p.getAnnotation(FromContext.class);
			if (fc != null) {
				String key = fc.value();
				Object value = ctx.get(key, p.getType());
				javaArgs[i] = value;
				continue;
			}

			// 3) Standard: parameter comes from LLM JSON
			String name = p.getName();
			JsonNode rawValue = argNode.get(name);

			if (rawValue == null) {
				throw new IllegalArgumentException(
						"Missing required argument '%s' for action '%s'"
								.formatted(name, method.getName()));
			}

			Object converted = mapper.convertValue(rawValue, p.getType());
			javaArgs[i] = converted;
		}

		return javaArgs;
	}
}