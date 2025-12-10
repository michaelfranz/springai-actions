package org.javai.springai.actions.dsl;

import java.util.HashMap;
import java.util.Map;

public class TypeFactoryRegistry {

	private static final Map<String, TypeFactory<?>> factories = new HashMap<>();

	public void register(String dslId, TypeFactory<?> factory) {
		if (factories.put(dslId, factory) != null) {
			throw new IllegalArgumentException("Duplicate dslId detected: " + dslId);
		}
	}

	public TypeFactory<?> getFactory(String dslId) {
		return factories.get(dslId);
	}

}
