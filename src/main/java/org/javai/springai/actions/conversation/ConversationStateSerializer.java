package org.javai.springai.actions.conversation;

/**
 * Serializes and deserializes {@link ConversationState} to/from opaque blobs.
 * 
 * <p>The blob format includes:</p>
 * <ul>
 *   <li>Schema version for migration</li>
 *   <li>Integrity hash for tamper detection</li>
 *   <li>Compressed JSON payload</li>
 * </ul>
 * 
 * <p>Applications store the blob and pass it back on subsequent turns.
 * The framework handles versioning and migration transparently.</p>
 * 
 * @see ConversationManager#converse(String, byte[])
 */
public interface ConversationStateSerializer {

	/**
	 * Serializes a conversation state to an opaque blob.
	 * 
	 * @param state the state to serialize
	 * @param typeRegistry registry for payload type resolution
	 * @return the serialized blob
	 */
	byte[] serialize(ConversationState state, PayloadTypeRegistry typeRegistry);

	/**
	 * Deserializes a blob back to conversation state.
	 * 
	 * <p>Performs integrity verification and schema migration if needed.</p>
	 * 
	 * @param blob the serialized blob
	 * @param typeRegistry registry for payload type resolution
	 * @return the deserialized state
	 * @throws IntegrityException if the blob has been tampered with
	 * @throws MigrationException if migration fails
	 */
	ConversationState deserialize(byte[] blob, PayloadTypeRegistry typeRegistry);

	/**
	 * Converts a blob to human-readable JSON for debugging.
	 * 
	 * @param blob the serialized blob
	 * @return pretty-printed JSON
	 */
	String toReadableJson(byte[] blob);

	/**
	 * Thrown when blob integrity verification fails.
	 */
	class IntegrityException extends RuntimeException {
		public IntegrityException(String message) {
			super(message);
		}
	}

	/**
	 * Thrown when schema migration fails.
	 */
	class MigrationException extends RuntimeException {
		public MigrationException(String message) {
			super(message);
		}

		public MigrationException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}

