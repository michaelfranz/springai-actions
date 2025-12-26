package org.javai.springai.actions.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class ActionContext {
	private final Map<String, Object> data = new ConcurrentHashMap<>();
	private final AtomicReference<Boolean> frozen = new AtomicReference<>(false);

	public void put(String key, Object value) {
		ensureMutable();
		data.put(key, value);
	}

	public <T> void put(ContextKey<T> key, T value) {
		put(key.name(), value);
	}

	@SuppressWarnings("unchecked")
	public <T> T get(String key, Class<T> type) {
		Object o = data.get(key);
		if (o == null) {
			throw new IllegalStateException("No value for context key: " + key);
		}
		assert type.isAssignableFrom(o.getClass())
				: "Given type '%s' is not assignable from stored value '%s'".formatted(type, o.getClass());
		return (T) o;
	}

	public <T> T get(ContextKey<T> key) {
		return get(key.name(), key.type());
	}

	public boolean contains(String key) {
		return data.containsKey(key);
	}

	public boolean contains(ContextKey<?> key) {
		return contains(key.name());
	}

	public Object get(String key) {
		if (!data.containsKey(key)) {
			throw new IllegalStateException("No value for context key: " + key);
		}
		return data.get(key);
	}

	/**
	 * Freeze the context to prevent further mutation (thread-safety / hand-off).
	 * Subsequent put attempts will throw.
	 */
	public void freeze() {
		frozen.set(true);
	}

	public boolean isFrozen() {
		return Boolean.TRUE.equals(frozen.get());
	}

	private void ensureMutable() {
		if (isFrozen()) {
			throw new IllegalStateException("ActionContext is frozen and cannot be modified");
		}
	}
}