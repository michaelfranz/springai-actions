package org.javai.springai.actions.conversation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Default implementation of {@link ConversationStateMigrationRegistry}.
 * 
 * <p>Maintains a map of migrations indexed by their source version.
 * Migrations are applied sequentially to bring blobs to the current version.</p>
 * 
 * <h2>Thread Safety</h2>
 * <p>This implementation is thread-safe for concurrent registration and migration.</p>
 */
public class DefaultConversationStateMigrationRegistry implements ConversationStateMigrationRegistry {

	private static final Logger logger = LoggerFactory.getLogger(DefaultConversationStateMigrationRegistry.class);

	private final Map<Integer, ConversationStateMigration> migrations = new ConcurrentHashMap<>();
	private final int currentVersion;

	/**
	 * Creates a registry with the specified current schema version.
	 * 
	 * @param currentVersion the current schema version for new blobs
	 */
	public DefaultConversationStateMigrationRegistry(int currentVersion) {
		if (currentVersion < 1) {
			throw new IllegalArgumentException("currentVersion must be >= 1");
		}
		this.currentVersion = currentVersion;
	}

	@Override
	public ConversationStateMigrationRegistry register(ConversationStateMigration migration) {
		if (migration == null) {
			throw new IllegalArgumentException("migration must not be null");
		}
		if (migration.toVersion() != migration.fromVersion() + 1) {
			throw new IllegalArgumentException(
					"Migration must increment version by 1: " + migration.fromVersion() + " -> " + migration.toVersion());
		}
		if (migrations.containsKey(migration.fromVersion())) {
			throw new IllegalArgumentException(
					"Migration from version " + migration.fromVersion() + " already registered");
		}
		migrations.put(migration.fromVersion(), migration);
		logger.debug("Registered migration: {}", migration.description());
		return this;
	}

	@Override
	public void migrateToCurrentVersion(ObjectNode json, int fromVersion) {
		if (fromVersion > currentVersion) {
			throw new ConversationStateSerializer.MigrationException(
					"Blob version " + fromVersion + " is newer than current version " + currentVersion);
		}

		if (fromVersion == currentVersion) {
			logger.debug("Blob is at current version {}, no migration needed", currentVersion);
			return;
		}

		logger.info("Migrating blob from version {} to {}", fromVersion, currentVersion);

		int version = fromVersion;
		while (version < currentVersion) {
			ConversationStateMigration migration = migrations.get(version);
			if (migration == null) {
				throw new ConversationStateSerializer.MigrationException(
						"No migration registered for version " + version + " -> " + (version + 1));
			}

			try {
				logger.debug("Applying migration: {}", migration.description());
				migration.migrate(json);
				version++;
			} catch (Exception e) {
				throw new ConversationStateSerializer.MigrationException(
						"Migration failed: " + migration.description(), e);
			}
		}

		logger.info("Migration complete: blob is now at version {}", currentVersion);
	}

	@Override
	public int currentVersion() {
		return currentVersion;
	}

	@Override
	public boolean canMigrate(int fromVersion) {
		if (fromVersion > currentVersion) {
			return false;
		}
		if (fromVersion == currentVersion) {
			return true;
		}

		// Check for complete chain
		for (int v = fromVersion; v < currentVersion; v++) {
			if (!migrations.containsKey(v)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Gets the number of registered migrations.
	 * 
	 * @return count of migrations
	 */
	public int migrationCount() {
		return migrations.size();
	}
}

