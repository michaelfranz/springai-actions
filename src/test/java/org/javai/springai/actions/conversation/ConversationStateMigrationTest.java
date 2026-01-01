package org.javai.springai.actions.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Conversation State Migration")
class ConversationStateMigrationTest {

	@Nested
	@DisplayName("DefaultConversationStateMigrationRegistry")
	class RegistryTests {

		@Test
		@DisplayName("registers and applies single migration")
		void registerAndApplySingleMigration() {
			DefaultConversationStateMigrationRegistry registry = new DefaultConversationStateMigrationRegistry(2);
			registry.register(new TestMigrationV1ToV2());

			assertThat(registry.currentVersion()).isEqualTo(2);
			assertThat(registry.canMigrate(1)).isTrue();
			assertThat(registry.canMigrate(2)).isTrue();
			assertThat(registry.migrationCount()).isEqualTo(1);
		}

		@Test
		@DisplayName("applies migration chain")
		void appliesMigrationChain() {
			var registry = new DefaultConversationStateMigrationRegistry(3)
					.register(new TestMigrationV1ToV2())
					.register(new TestMigrationV2ToV3());

			assertThat(registry.canMigrate(1)).isTrue();
			assertThat(registry.canMigrate(2)).isTrue();
			assertThat(registry.canMigrate(3)).isTrue();
		}

		@Test
		@DisplayName("detects incomplete migration chain")
		void detectsIncompleteMigrationChain() {
			var registry = new DefaultConversationStateMigrationRegistry(3)
					.register(new TestMigrationV2ToV3());
			// Missing v1 -> v2

			assertThat(registry.canMigrate(1)).isFalse();
			assertThat(registry.canMigrate(2)).isTrue();
		}

		@Test
		@DisplayName("rejects duplicate migration registration")
		void rejectsDuplicateMigration() {
			var registry = new DefaultConversationStateMigrationRegistry(2)
					.register(new TestMigrationV1ToV2());

			assertThatThrownBy(() -> registry.register(new TestMigrationV1ToV2()))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("already registered");
		}

		@Test
		@DisplayName("rejects migration with invalid version increment")
		void rejectsInvalidVersionIncrement() {
			var registry = new DefaultConversationStateMigrationRegistry(3);

			assertThatThrownBy(() -> registry.register(new InvalidMigration()))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("increment version by 1");
		}

		@Test
		@DisplayName("rejects blob newer than current version")
		void rejectsNewerBlob() {
			var registry = new DefaultConversationStateMigrationRegistry(2);

			assertThatThrownBy(() -> registry.migrateToCurrentVersion(null, 5))
					.isInstanceOf(ConversationStateSerializer.MigrationException.class)
					.hasMessageContaining("newer than current version");
		}
	}

	@Nested
	@DisplayName("JsonConversationStateSerializer with Migrations")
	class SerializerMigrationTests {

		@Test
		@DisplayName("serializes with current schema version")
		void serializesWithCurrentVersion() {
			var registry = new DefaultConversationStateMigrationRegistry(2)
					.register(new TestMigrationV1ToV2());
			var serializer = new JsonConversationStateSerializer(registry);

			assertThat(serializer.schemaVersion()).isEqualTo(2);

			var state = ConversationState.initial("test");
			var typeRegistry = new PayloadTypeRegistry();
			byte[] blob = serializer.serialize(state, typeRegistry);

			// Verify version bytes (bytes 4-5)
			int version = ((blob[4] & 0xFF) << 8) | (blob[5] & 0xFF);
			assertThat(version).isEqualTo(2);
		}

		@Test
		@DisplayName("deserializes current version without migration")
		void deserializesCurrentVersionWithoutMigration() {
			var registry = new DefaultConversationStateMigrationRegistry(1);
			var serializer = new JsonConversationStateSerializer(registry);
			var typeRegistry = new PayloadTypeRegistry();

			var state = ConversationState.initial("test instruction");
			byte[] blob = serializer.serialize(state, typeRegistry);

			var restored = serializer.deserialize(blob, typeRegistry);
			assertThat(restored.originalInstruction()).isEqualTo("test instruction");
		}

		@Test
		@DisplayName("applies migration on deserialization")
		void appliesMigrationOnDeserialization() {
			var typeRegistry = new PayloadTypeRegistry();

			// Create blob with v1 serializer
			var v1Serializer = new JsonConversationStateSerializer(
					new DefaultConversationStateMigrationRegistry(1));
			var state = ConversationState.initial("v1 instruction");
			byte[] v1Blob = v1Serializer.serialize(state, typeRegistry);

			// Deserialize with v2 serializer (should migrate)
			var v2Registry = new DefaultConversationStateMigrationRegistry(2)
					.register(new TestMigrationV1ToV2());
			var v2Serializer = new JsonConversationStateSerializer(v2Registry);

			var restored = v2Serializer.deserialize(v1Blob, typeRegistry);
			// Migration should have run (we're just testing it doesn't throw)
			assertThat(restored.originalInstruction()).isEqualTo("v1 instruction");
		}

		@Test
		@DisplayName("throws when migration needed but not registered")
		void throwsWhenMigrationNeededButNotRegistered() {
			var typeRegistry = new PayloadTypeRegistry();

			// Create blob with v1 serializer
			var v1Registry = new DefaultConversationStateMigrationRegistry(1);
			var v1Serializer = new JsonConversationStateSerializer(v1Registry);
			var state = ConversationState.initial("test");
			byte[] v1Blob = v1Serializer.serialize(state, typeRegistry);

			// Try to deserialize with v2 serializer that has no migrations registered
			var v2Registry = new DefaultConversationStateMigrationRegistry(2);
			// Note: deliberately NOT registering v1->v2 migration
			var v2Serializer = new JsonConversationStateSerializer(v2Registry);
			
			assertThatThrownBy(() -> v2Serializer.deserialize(v1Blob, typeRegistry))
					.isInstanceOf(ConversationStateSerializer.MigrationException.class)
					.hasMessageContaining("No migration registered");
		}
	}

	@Nested
	@DisplayName("Integrity Protection")
	class IntegrityTests {

		@Test
		@DisplayName("rejects tampered blob data")
		void rejectsTamperedBlobData() {
			var serializer = new JsonConversationStateSerializer();
			var typeRegistry = new PayloadTypeRegistry();

			var state = ConversationState.initial("original instruction");
			byte[] blob = serializer.serialize(state, typeRegistry);

			// Tamper with the compressed data portion (after header: 4 magic + 2 version + 32 hash)
			int dataOffset = 4 + 2 + 32;
			if (blob.length > dataOffset + 5) {
				blob[dataOffset + 5] ^= 0xFF; // Flip bits in the compressed data
			}

			assertThatThrownBy(() -> serializer.deserialize(blob, typeRegistry))
					.isInstanceOf(ConversationStateSerializer.IntegrityException.class)
					.hasMessageContaining("integrity check failed");
		}

		@Test
		@DisplayName("rejects blob with corrupted hash")
		void rejectsBlobWithCorruptedHash() {
			var serializer = new JsonConversationStateSerializer();
			var typeRegistry = new PayloadTypeRegistry();

			var state = ConversationState.initial("test instruction");
			byte[] blob = serializer.serialize(state, typeRegistry);

			// Tamper with the hash (bytes 6-37)
			blob[10] ^= 0xFF;

			assertThatThrownBy(() -> serializer.deserialize(blob, typeRegistry))
					.isInstanceOf(ConversationStateSerializer.IntegrityException.class)
					.hasMessageContaining("integrity check failed");
		}

		@Test
		@DisplayName("accepts valid blob unchanged")
		void acceptsValidBlobUnchanged() {
			var serializer = new JsonConversationStateSerializer();
			var typeRegistry = new PayloadTypeRegistry();

			var state = ConversationState.initial("valid instruction");
			byte[] blob = serializer.serialize(state, typeRegistry);

			// Should deserialize without exception
			var restored = serializer.deserialize(blob, typeRegistry);
			assertThat(restored.originalInstruction()).isEqualTo("valid instruction");
		}

		@Test
		@DisplayName("rejects truncated blob")
		void rejectsTruncatedBlob() {
			var serializer = new JsonConversationStateSerializer();
			var typeRegistry = new PayloadTypeRegistry();

			var state = ConversationState.initial("test");
			byte[] blob = serializer.serialize(state, typeRegistry);

			// Truncate to just the header
			byte[] truncated = new byte[38]; // 4 + 2 + 32 = header only
			System.arraycopy(blob, 0, truncated, 0, truncated.length);

			assertThatThrownBy(() -> serializer.deserialize(truncated, typeRegistry))
					.isInstanceOf(RuntimeException.class); // Will fail on decompress or hash
		}

		@Test
		@DisplayName("rejects blob with invalid magic number")
		void rejectsBlobWithInvalidMagicNumber() {
			var serializer = new JsonConversationStateSerializer();
			var typeRegistry = new PayloadTypeRegistry();

			var state = ConversationState.initial("test");
			byte[] blob = serializer.serialize(state, typeRegistry);

			// Corrupt magic number
			blob[0] = 'X';

			assertThatThrownBy(() -> serializer.deserialize(blob, typeRegistry))
					.isInstanceOf(ConversationStateSerializer.IntegrityException.class)
					.hasMessageContaining("Invalid blob magic number");
		}
	}

	// Test migrations

	static class TestMigrationV1ToV2 implements ConversationStateMigration {
		@Override
		public int fromVersion() { return 1; }
		@Override
		public int toVersion() { return 2; }
		@Override
		public void migrate(ObjectNode json) {
			// Simple migration: add a field
			json.put("migratedToV2", true);
		}
		@Override
		public String description() { return "Test migration v1 to v2"; }
	}

	static class TestMigrationV2ToV3 implements ConversationStateMigration {
		@Override
		public int fromVersion() { return 2; }
		@Override
		public int toVersion() { return 3; }
		@Override
		public void migrate(ObjectNode json) {
			json.put("migratedToV3", true);
		}
	}

	static class InvalidMigration implements ConversationStateMigration {
		@Override
		public int fromVersion() { return 1; }
		@Override
		public int toVersion() { return 3; } // Invalid: should be 2
		@Override
		public void migrate(ObjectNode json) {}
	}
}

