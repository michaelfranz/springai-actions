package org.javai.springai.actions.internal.resolve;

import java.util.Map;
import java.util.Optional;
import org.javai.springai.actions.api.TypeHandlerRegistry;
import org.javai.springai.actions.internal.bind.ActionRegistry;

/**
 * Context for plan resolution, bundling the action registry, type handlers, and optional contextual data.
 * 
 * <p>This allows the resolver to access context-specific information during resolution,
 * such as schema catalogs for validation and custom type resolvers, without coupling
 * the core framework to any specific domain (e.g., SQL).</p>
 * 
 * <p>Context entries are keyed by string and can be retrieved by type:</p>
 * <pre>{@code
 * SqlCatalog catalog = context.get("sql", SqlCatalog.class).orElse(null);
 * }</pre>
 */
public record ResolutionContext(
		ActionRegistry actionRegistry,
		TypeHandlerRegistry typeHandlerRegistry,
		Map<String, Object> context
) {
	/**
	 * Creates a context with just the registry (no type handlers or additional context).
	 */
	public static ResolutionContext of(ActionRegistry actionRegistry) {
		return new ResolutionContext(actionRegistry, null, Map.of());
	}

	/**
	 * Creates a context with registry and context map (no type handlers).
	 */
	public static ResolutionContext of(ActionRegistry actionRegistry, Map<String, Object> context) {
		return new ResolutionContext(actionRegistry, null, context != null ? context : Map.of());
	}

	/**
	 * Creates a context with registry, type handlers, and context map.
	 */
	public static ResolutionContext of(ActionRegistry actionRegistry, TypeHandlerRegistry typeHandlerRegistry, 
			Map<String, Object> context) {
		return new ResolutionContext(actionRegistry, typeHandlerRegistry, context != null ? context : Map.of());
	}

	/**
	 * Returns the type handler registry if present.
	 */
	public Optional<TypeHandlerRegistry> typeRegistry() {
		return Optional.ofNullable(typeHandlerRegistry);
	}

	/**
	 * Retrieves a context value by key and expected type.
	 * 
	 * @param key the context key
	 * @param type the expected type
	 * @return an Optional containing the value if present and of correct type
	 */
	public <T> Optional<T> get(String key, Class<T> type) {
		Object value = context.get(key);
		if (value != null && type.isInstance(value)) {
			return Optional.of(type.cast(value));
		}
		return Optional.empty();
	}
}
