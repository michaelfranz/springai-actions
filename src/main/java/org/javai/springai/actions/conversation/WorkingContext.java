package org.javai.springai.actions.conversation;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * The current working object in a multi-turn conversation.
 * 
 * <p>Domain-agnostic container that holds a typed payload being refined
 * across conversation turns. Examples include:</p>
 * <ul>
 *   <li>SQL scenario: {@code WorkingContext<Query>} - a query being refined</li>
 *   <li>Shopping scenario: {@code WorkingContext<BasketSummary>} - basket state</li>
 * </ul>
 * 
 * <p>The payload is serialized as part of the conversation state blob.
 * Payload types must be registered with {@link PayloadTypeRegistry} for
 * proper deserialization.</p>
 * 
 * @param <T> the type of the working object
 * @param contextType identifies the type of context (e.g., "sql.query", "shopping.basket")
 * @param payload the working object itself
 * @param lastModified when this context was last updated
 * @param metadata additional domain-specific data
 */
public record WorkingContext<T>(
		String contextType,
		T payload,
		Instant lastModified,
		Map<String, Object> metadata
) {

	public WorkingContext {
		Objects.requireNonNull(contextType, "contextType must not be null");
		Objects.requireNonNull(payload, "payload must not be null");
		lastModified = lastModified != null ? lastModified : Instant.now();
		metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
	}

	/**
	 * Creates a new WorkingContext with the current timestamp and no metadata.
	 * 
	 * @param contextType the context type identifier
	 * @param payload the working object
	 * @param <T> the payload type
	 * @return a new WorkingContext
	 */
	public static <T> WorkingContext<T> of(String contextType, T payload) {
		return new WorkingContext<>(contextType, payload, Instant.now(), Map.of());
	}

	/**
	 * Creates a new WorkingContext with metadata.
	 * 
	 * @param contextType the context type identifier
	 * @param payload the working object
	 * @param metadata additional data
	 * @param <T> the payload type
	 * @return a new WorkingContext
	 */
	public static <T> WorkingContext<T> of(String contextType, T payload, Map<String, Object> metadata) {
		return new WorkingContext<>(contextType, payload, Instant.now(), metadata);
	}

	/**
	 * Creates a copy with an updated payload, refreshing the lastModified timestamp.
	 * 
	 * @param newPayload the new payload
	 * @return a new WorkingContext with the updated payload
	 */
	public WorkingContext<T> withPayload(T newPayload) {
		return new WorkingContext<>(contextType, newPayload, Instant.now(), metadata);
	}

	/**
	 * Creates a copy with additional metadata merged in.
	 * 
	 * @param additionalMetadata metadata to merge
	 * @return a new WorkingContext with merged metadata
	 */
	public WorkingContext<T> withMetadata(Map<String, Object> additionalMetadata) {
		if (additionalMetadata == null || additionalMetadata.isEmpty()) {
			return this;
		}
		var merged = new java.util.HashMap<>(this.metadata);
		merged.putAll(additionalMetadata);
		return new WorkingContext<>(contextType, payload, lastModified, Map.copyOf(merged));
	}
}

