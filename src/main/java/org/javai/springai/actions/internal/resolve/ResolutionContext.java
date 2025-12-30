package org.javai.springai.actions.internal.resolve;

import java.util.Map;
import java.util.Optional;
import org.javai.springai.actions.internal.bind.ActionRegistry;

/**
 * Context for plan resolution, bundling the action registry and optional contextual data.
 * 
 * <p>This allows the resolver to access context-specific information during resolution,
 * such as schema catalogs for validation, without coupling the core framework to
 * any specific domain (e.g., SQL).</p>
 * 
 * <p>Context entries are keyed by string and can be retrieved by type:</p>
 * <pre>{@code
 * SqlCatalog catalog = context.get("sql", SqlCatalog.class).orElse(null);
 * }</pre>
 */
public record ResolutionContext(
		ActionRegistry registry,
		Map<String, Object> context
) {
	/**
	 * Creates a context with just the registry (no additional context).
	 */
	public static ResolutionContext of(ActionRegistry registry) {
		return new ResolutionContext(registry, Map.of());
	}

	/**
	 * Creates a context with registry and context map.
	 */
	public static ResolutionContext of(ActionRegistry registry, Map<String, Object> context) {
		return new ResolutionContext(registry, context != null ? context : Map.of());
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

