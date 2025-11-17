package org.javai.springai.actions.api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ActionContext {
	private final Map<String, Object> data = new ConcurrentHashMap<>();

	public <T> void put(String key, T value) {
		data.put(key, value);
	}

	public <T> T get(String key, Class<T> type) {
		Object v = data.get(key);
		if (v == null) {
			throw new IllegalStateException("No value for context key: " + key);
		}
		return (T) v;
	}

	public boolean contains(String key) {
		return data.containsKey(key);
	}
}