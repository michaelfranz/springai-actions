package org.javai.springai.actions.conversation;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for mapping context type identifiers to payload classes.
 * 
 * <p>Used during deserialization of {@link WorkingContext} to determine
 * the correct class for the payload field. Domain modules register their
 * payload types at application startup.</p>
 * 
 * <h2>Usage</h2>
 * <pre>{@code
 * PayloadTypeRegistry registry = new PayloadTypeRegistry();
 * registry.register("sql.query", Query.class);
 * registry.register("shopping.basket", BasketSummary.class);
 * 
 * // Later, during deserialization:
 * Class<?> payloadClass = registry.getPayloadClass("sql.query")
 *         .orElseThrow(() -> new IllegalStateException("Unknown context type"));
 * }</pre>
 * 
 * <h2>Thread Safety</h2>
 * <p>This registry is thread-safe for concurrent registration and lookup.</p>
 */
public class PayloadTypeRegistry {

	private final Map<String, Class<?>> typeMap = new ConcurrentHashMap<>();

	/**
	 * Registers a payload class for a context type.
	 * 
	 * @param contextType the context type identifier (e.g., "sql.query")
	 * @param payloadClass the class of the payload
	 * @throws IllegalArgumentException if contextType is null or blank
	 * @throws IllegalArgumentException if payloadClass is null
	 */
	public void register(String contextType, Class<?> payloadClass) {
		if (contextType == null || contextType.isBlank()) {
			throw new IllegalArgumentException("contextType must not be null or blank");
		}
		if (payloadClass == null) {
			throw new IllegalArgumentException("payloadClass must not be null");
		}
		typeMap.put(contextType, payloadClass);
	}

	/**
	 * Gets the payload class for a context type.
	 * 
	 * @param contextType the context type identifier
	 * @return the payload class, or empty if not registered
	 */
	public Optional<Class<?>> getPayloadClass(String contextType) {
		if (contextType == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(typeMap.get(contextType));
	}

	/**
	 * Checks if a context type is registered.
	 * 
	 * @param contextType the context type identifier
	 * @return true if registered
	 */
	public boolean isRegistered(String contextType) {
		return contextType != null && typeMap.containsKey(contextType);
	}

	/**
	 * Gets all registered context types.
	 * 
	 * @return unmodifiable map of context types to payload classes
	 */
	public Map<String, Class<?>> getAllRegistrations() {
		return Map.copyOf(typeMap);
	}

	/**
	 * Removes a registration.
	 * 
	 * @param contextType the context type to unregister
	 * @return true if the type was registered and removed
	 */
	public boolean unregister(String contextType) {
		return typeMap.remove(contextType) != null;
	}

	/**
	 * Clears all registrations.
	 */
	public void clear() {
		typeMap.clear();
	}
}

