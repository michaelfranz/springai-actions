package org.javai.springai.actions.api;

import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for custom type handlers (spec providers and resolvers).
 * 
 * <p>This registry allows the framework to be extended with custom type handling
 * without modifying core code. Types like Query can register their own spec
 * providers (for schema generation) and resolvers (for deserialization).</p>
 * 
 * <p>The recommended way to create a registry is using {@link #discover()} which
 * automatically loads all registered handlers via Java's ServiceLoader (SPI):</p>
 * 
 * <pre>{@code
 * TypeHandlerRegistry registry = TypeHandlerRegistry.discover();
 * }</pre>
 * 
 * <p>Custom handlers can be registered by creating files in META-INF/services:</p>
 * <ul>
 *   <li>{@code META-INF/services/org.javai.springai.actions.api.TypeSpecProvider}</li>
 *   <li>{@code META-INF/services/org.javai.springai.actions.api.TypeResolver}</li>
 * </ul>
 * 
 * <p>Manual registration is also supported:</p>
 * <pre>{@code
 * TypeHandlerRegistry registry = new TypeHandlerRegistry();
 * registry.register(new MySpecProvider());
 * registry.register(new MyResolver());
 * }</pre>
 */
public final class TypeHandlerRegistry {

	private final Map<Class<?>, TypeSpecProvider> specProviders = new ConcurrentHashMap<>();
	private final Map<Class<?>, TypeResolver> resolvers = new ConcurrentHashMap<>();

	/**
	 * Registers a type spec provider.
	 */
	public TypeHandlerRegistry register(TypeSpecProvider provider) {
		if (provider != null) {
			specProviders.put(provider.supportedType(), provider);
		}
		return this;
	}

	/**
	 * Registers a type resolver.
	 */
	public TypeHandlerRegistry register(TypeResolver resolver) {
		if (resolver != null) {
			resolvers.put(resolver.supportedType(), resolver);
		}
		return this;
	}

	/**
	 * Looks up a spec provider for the given type.
	 */
	public Optional<TypeSpecProvider> specProvider(Class<?> type) {
		TypeSpecProvider provider = specProviders.get(type);
		if (provider != null) {
			return Optional.of(provider);
		}
		// Check for assignable types (e.g., subclasses)
		for (var entry : specProviders.entrySet()) {
			if (entry.getKey().isAssignableFrom(type)) {
				return Optional.of(entry.getValue());
			}
		}
		return Optional.empty();
	}

	/**
	 * Looks up a resolver for the given type.
	 */
	public Optional<TypeResolver> resolver(Class<?> type) {
		TypeResolver resolver = resolvers.get(type);
		if (resolver != null) {
			return Optional.of(resolver);
		}
		// Check for assignable types (e.g., subclasses)
		for (var entry : resolvers.entrySet()) {
			if (entry.getKey().isAssignableFrom(type)) {
				return Optional.of(entry.getValue());
			}
		}
		return Optional.empty();
	}

	/**
	 * Creates a registry by discovering all registered handlers via Java's ServiceLoader.
	 * 
	 * <p>This loads all implementations of {@link TypeSpecProvider} and {@link TypeResolver}
	 * that are registered in META-INF/services files.</p>
	 * 
	 * @return a registry populated with all discovered handlers
	 */
	public static TypeHandlerRegistry discover() {
		TypeHandlerRegistry registry = new TypeHandlerRegistry();
		
		// Discover and register all TypeSpecProviders
		ServiceLoader<TypeSpecProvider> specProviders = ServiceLoader.load(TypeSpecProvider.class);
		for (TypeSpecProvider provider : specProviders) {
			registry.register(provider);
		}
		
		// Discover and register all TypeResolvers
		ServiceLoader<TypeResolver> resolvers = ServiceLoader.load(TypeResolver.class);
		for (TypeResolver resolver : resolvers) {
			registry.register(resolver);
		}
		
		return registry;
	}

	/**
	 * Creates a registry pre-populated with SQL type handlers.
	 * 
	 * @deprecated Use {@link #discover()} instead, which auto-discovers all handlers via SPI
	 */
	@Deprecated(forRemoval = true)
	public static TypeHandlerRegistry withSqlSupport() {
		return new TypeHandlerRegistry()
				.register(new org.javai.springai.actions.sql.QuerySpecProvider())
				.register(new org.javai.springai.actions.sql.QueryResolver());
	}
}

