package org.javai.springai.actions.execution;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.javai.springai.actions.api.ActionContext;

final class ContextExpressionResolver {

	private ContextExpressionResolver() {
	}

	static String resolve(ActionContext ctx, String expression) {
		Objects.requireNonNull(ctx, "ctx must not be null");
		Objects.requireNonNull(expression, "expression must not be null");

		String[] parts = expression.split("\\.");
		if (parts.length == 0) {
			throw new IllegalArgumentException("Invalid context expression: " + expression);
		}

		Object current = ctx.get(parts[0]);
		for (int i = 1; i < parts.length; i++) {
			current = extractProperty(current, parts[i]);
		}

		return Objects.toString(current, null);
	}

	private static Object extractProperty(Object target, String property) {
		if (target == null) {
			throw new IllegalStateException("Cannot resolve property '%s' on null".formatted(property));
		}
		if (target instanceof Map<?, ?> map) {
			if (!map.containsKey(property)) {
				throw new IllegalStateException("Map does not contain key '%s'".formatted(property));
			}
			return map.get(property);
		}

		Class<?> type = target.getClass();
		try {
			Method accessor = type.getMethod(property);
			return accessor.invoke(target);
		} catch (Exception ignored) {
		}

		String getterName = "get" + property.substring(0, 1).toUpperCase(Locale.ROOT) + property.substring(1);
		try {
			Method getter = type.getMethod(getterName);
			return getter.invoke(target);
		} catch (Exception ignored) {
		}

		throw new IllegalStateException("Cannot resolve property '%s' on %s".formatted(property, type.getName()));
	}
}

