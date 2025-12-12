package org.javai.springai.dsl.bind;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class TypeFactoryRegistry {

	private static final Map<String, TypeFactory<?>> factories = new ConcurrentHashMap<>();
	private static final Map<String, Class<?>> types = new ConcurrentHashMap<>();

	private TypeFactoryRegistry() {
	}

	public static <T> void register(String dslId, Class<T> type, TypeFactory<T> factory) {
		Objects.requireNonNull(type, "type must not be null");
		Objects.requireNonNull(factory, "factory must not be null");
		String normalizedId = normalize(dslId);
		if (factories.putIfAbsent(normalizedId, factory) != null) {
			throw new IllegalArgumentException("Duplicate dslId detected: " + normalizedId);
		}
		types.put(normalizedId, type);
	}

	public static Optional<TypeFactory<?>> getFactory(String dslId) {
		return Optional.ofNullable(factories.get(normalize(dslId)));
	}

	public static <T> Optional<TypeFactory<T>> getFactory(String dslId, Class<T> targetType) {
		String normalizedId = normalize(dslId);
		TypeFactory<?> typeFactory = factories.get(normalizedId);
		if (typeFactory == null) {
			return Optional.empty();
		}
		Class<?> registeredType = types.get(normalizedId);
		if (registeredType == null) {
			throw new IllegalStateException("Registered factory missing type for dslId: " + normalizedId);
		}
		if (!targetType.isAssignableFrom(registeredType)) {
			throw new IllegalArgumentException("Type mismatch: " + targetType + " is not assignable from " + registeredType);
		}
		//noinspection unchecked
		return Optional.of((TypeFactory<T>) typeFactory);
	}

	public static void requireRegistered(String... expectedDslIds) {
		for (String id : expectedDslIds) {
			String normalized = normalize(id);
			if (!factories.containsKey(normalized)) {
				throw new IllegalStateException("Required DSL id not registered: " + normalized);
			}
		}
	}

	private static String normalize(String dslId) {
		if (dslId == null) {
			throw new IllegalArgumentException("dslId must not be null");
		}
		String trimmed = dslId.trim();
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException("dslId must not be blank");
		}
		return trimmed.toLowerCase();
	}

	// Visible for tests
	static void clearAll(Supplier<Boolean> guard) {
		if (guard == null || Boolean.FALSE.equals(guard.get())) {
			throw new IllegalStateException("clearAll guard not satisfied");
		}
		factories.clear();
		types.clear();
	}

}
