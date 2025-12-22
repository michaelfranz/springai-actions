package org.javai.springai.dsl.exec;

/**
 * Resolved argument ready for invocation.
 */
public record ResolvedArgument(
		String name,
		Object value,
		Class<?> targetType
) {
}

