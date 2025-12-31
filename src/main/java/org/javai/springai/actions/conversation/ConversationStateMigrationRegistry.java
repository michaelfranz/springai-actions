package org.javai.springai.actions.conversation;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Registry for managing and applying schema migrations.
 * 
 * <p>When deserializing a blob with an older schema version, the registry
 * applies all necessary migrations to bring it up to the current version.</p>
 * 
 * <h2>Usage</h2>
 * <pre>{@code
 * ConversationStateMigrationRegistry registry = new DefaultConversationStateMigrationRegistry()
 *     .register(new V1ToV2Migration())
 *     .register(new V2ToV3Migration());
 * 
 * // Apply all migrations from version 1 to current (3)
 * registry.migrateToCurrentVersion(json, 1);
 * }</pre>
 * 
 * @see ConversationStateMigration
 */
public interface ConversationStateMigrationRegistry {

	/**
	 * Registers a migration.
	 * 
	 * @param migration the migration to register
	 * @return this registry for chaining
	 * @throws IllegalArgumentException if a migration for this version already exists
	 */
	ConversationStateMigrationRegistry register(ConversationStateMigration migration);

	/**
	 * Applies all necessary migrations to bring JSON from the given version
	 * to the current schema version.
	 * 
	 * <p>Migrations are applied in sequence: if blob is v1 and current is v3,
	 * applies v1→v2, then v2→v3.</p>
	 * 
	 * @param json the JSON to migrate (modified in place)
	 * @param fromVersion the version of the blob being migrated
	 * @throws ConversationStateSerializer.MigrationException if any migration fails
	 *         or if there's a gap in the migration chain
	 */
	void migrateToCurrentVersion(ObjectNode json, int fromVersion);

	/**
	 * Gets the current schema version.
	 * 
	 * <p>This is the version that new blobs are serialized with,
	 * and the target version for migrations.</p>
	 * 
	 * @return the current schema version
	 */
	int currentVersion();

	/**
	 * Checks if the registry can migrate from the given version to current.
	 * 
	 * @param fromVersion the source version
	 * @return true if all necessary migrations are registered
	 */
	boolean canMigrate(int fromVersion);
}

