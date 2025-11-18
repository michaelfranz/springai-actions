package org.javai.springai.actions.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ActionContext {
	private final Map<String, Object> data = new ConcurrentHashMap<>();

	public void put(String key, Object value) {
		data.put(key, value);
	}

	public <T> T get(String key, Class<T> type) {
		Object o = data.get(key);
		if (o == null) {
			throw new IllegalStateException("No value for context key: " + key);
		}
		assert type.isAssignableFrom(o.getClass())
				: "Given type '%s' is not assignable from stored value '%s'".formatted(type, o.getClass());
		return (T) o;
	}

	public boolean contains(String key) {
		return data.containsKey(key);
	}
}