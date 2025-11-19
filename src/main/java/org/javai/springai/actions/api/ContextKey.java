package org.javai.springai.actions.api;

import java.util.Objects;

/**
 * Type-safe handle for entries stored in {@link ActionContext}. Using context
 * keys avoids stringly-typed access and centralises key definitions.
 *
 * @param <T> value type associated with the key
 */
public final class ContextKey<T> {

	private final String name;
	private final Class<T> type;

	private ContextKey(String name, Class<T> type) {
		this.name = name;
		this.type = type;
	}

	public static <T> ContextKey<T> of(String name, Class<T> type) {
		Objects.requireNonNull(name, "name must not be null");
		Objects.requireNonNull(type, "type must not be null");
		return new ContextKey<>(name, type);
	}

	public String name() {
		return name;
	}

	public Class<T> type() {
		return type;
	}

	@Override
	public String toString() {
		return "ContextKey[" + name + ":" + type.getSimpleName() + "]";
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof ContextKey<?> that)) return false;
		return name.equals(that.name);
	}

	@Override
		public int hashCode() {
		return name.hashCode();
	}
}

