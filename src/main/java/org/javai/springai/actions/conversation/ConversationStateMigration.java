package org.javai.springai.actions.conversation;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Migrates conversation state JSON from one schema version to the next.
 * 
 * <p>Migrations are applied sequentially when deserializing blobs with
 * older schema versions. Each migration transforms the JSON structure
 * from version N to version N+1.</p>
 * 
 * <h2>Example Migration</h2>
 * <pre>{@code
 * public class V1ToV2Migration implements ConversationStateMigration {
 *     @Override
 *     public int fromVersion() { return 1; }
 *     
 *     @Override
 *     public int toVersion() { return 2; }
 *     
 *     @Override
 *     public void migrate(ObjectNode json) {
 *         // Rename field "oldName" to "newName"
 *         if (json.has("oldName")) {
 *             json.set("newName", json.remove("oldName"));
 *         }
 *     }
 * }
 * }</pre>
 * 
 * <h2>Migration Chain</h2>
 * <p>If a blob is at version 1 and current version is 3, the registry
 * applies: V1→V2, then V2→V3.</p>
 * 
 * @see ConversationStateMigrationRegistry
 */
public interface ConversationStateMigration {

	/**
	 * The schema version this migration upgrades FROM.
	 * 
	 * @return the source version number
	 */
	int fromVersion();

	/**
	 * The schema version this migration upgrades TO.
	 * Must be exactly {@code fromVersion() + 1}.
	 * 
	 * @return the target version number
	 */
	int toVersion();

	/**
	 * Transforms the JSON structure in place.
	 * 
	 * <p>Implementations should handle missing fields gracefully,
	 * as older versions may not have all fields.</p>
	 * 
	 * @param json the JSON object to transform (mutable)
	 */
	void migrate(ObjectNode json);

	/**
	 * Optional description of what this migration does.
	 * Used for logging and debugging.
	 * 
	 * @return human-readable description
	 */
	default String description() {
		return "Migrate from v" + fromVersion() + " to v" + toVersion();
	}
}

