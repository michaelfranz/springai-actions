package org.javai.springai.actions.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ActionContext {
	private final Map<String, Object> data = new ConcurrentHashMap<>();

	public void put(String key, Object value) {
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
}