package org.javai.springai.actions.exec;

/**
 * Resolved argument ready for invocation.
 */
public record ResolvedArgument(
		String name,
		Object value,
		Class<?> targetType
) {
}

