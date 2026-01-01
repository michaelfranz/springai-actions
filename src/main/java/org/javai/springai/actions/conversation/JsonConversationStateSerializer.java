package org.javai.springai.actions.conversation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * JSON-based implementation of {@link ConversationStateSerializer}.
 * 
 * <p>Serializes conversation state to compressed JSON with schema versioning
 * and integrity protection. The blob format is:</p>
 * <ul>
 *   <li>4 bytes: Magic number "CVST"</li>
 *   <li>2 bytes: Schema version</li>
 *   <li>32 bytes: SHA-256 hash of the compressed data (integrity check)</li>
 *   <li>Remaining: gzip-compressed JSON</li>
 * </ul>
 * 
 * <h2>Integrity Protection</h2>
 * <p>The SHA-256 hash ensures that any tampering with the blob is detected
 * during deserialization, throwing an {@link IntegrityException}.</p>
 * 
 * <h2>Schema Migrations</h2>
 * <p>When deserializing older blobs, registered migrations are applied
 * automatically to bring the JSON structure to the current version.</p>
 * 
 * <h2>Example with Migrations</h2>
 * <pre>{@code
 * var registry = new DefaultConversationStateMigrationRegistry(2)
 *     .register(new V1ToV2Migration());
 * var serializer = new JsonConversationStateSerializer(registry);
 * }</pre>
 */
public class JsonConversationStateSerializer implements ConversationStateSerializer {

	private static final byte[] MAGIC = "CVST".getBytes(StandardCharsets.UTF_8);
	private static final int HASH_LENGTH = 32; // SHA-256 produces 32 bytes
	private static final int HEADER_LENGTH = 4 + 2 + HASH_LENGTH; // magic + version + hash
	
	/** Current schema version for new blobs */
	public static final int CURRENT_SCHEMA_VERSION = 1;

	private final ObjectMapper mapper;
	private final ConversationStateMigrationRegistry migrationRegistry;
	private final int schemaVersion;

	/**
	 * Creates a serializer without migration support.
	 * 
	 * <p>Use this for simple cases where schema evolution isn't needed.</p>
	 */
	public JsonConversationStateSerializer() {
		this(null);
	}

	/**
	 * Creates a serializer with migration support.
	 * 
	 * @param migrationRegistry the registry for schema migrations (may be null)
	 */
	public JsonConversationStateSerializer(ConversationStateMigrationRegistry migrationRegistry) {
		this.mapper = new ObjectMapper()
				.registerModule(new JavaTimeModule())
				.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
		this.migrationRegistry = migrationRegistry;
		this.schemaVersion = migrationRegistry != null 
				? migrationRegistry.currentVersion() 
				: CURRENT_SCHEMA_VERSION;
	}

	@Override
	public byte[] serialize(ConversationState state, PayloadTypeRegistry typeRegistry) {
		try {
			ObjectNode json = stateToJson(state);
			String jsonString = mapper.writeValueAsString(json);
			byte[] compressed = compress(jsonString.getBytes(StandardCharsets.UTF_8));
			byte[] hash = computeHash(compressed);

			// Build blob: magic + version + hash + compressed data
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			bos.write(MAGIC);
			bos.write((schemaVersion >> 8) & 0xFF);
			bos.write(schemaVersion & 0xFF);
			bos.write(hash);
			bos.write(compressed);
			return bos.toByteArray();

		} catch (Exception e) {
			throw new RuntimeException("Failed to serialize conversation state", e);
		}
	}

	@Override
	public ConversationState deserialize(byte[] blob, PayloadTypeRegistry typeRegistry) {
		if (blob == null || blob.length < HEADER_LENGTH) {
			throw new ConversationStateSerializer.IntegrityException("Blob is too short or null");
		}

		// Verify magic
		for (int i = 0; i < 4; i++) {
			if (blob[i] != MAGIC[i]) {
				throw new ConversationStateSerializer.IntegrityException("Invalid blob magic number");
			}
		}

		// Read version
		int blobVersion = ((blob[4] & 0xFF) << 8) | (blob[5] & 0xFF);
		if (blobVersion > schemaVersion) {
			throw new ConversationStateSerializer.MigrationException(
					"Blob version " + blobVersion + " is newer than current version " + schemaVersion);
		}

		// Extract stored hash and compressed data
		byte[] storedHash = new byte[HASH_LENGTH];
		System.arraycopy(blob, 6, storedHash, 0, HASH_LENGTH);
		
		byte[] compressed = new byte[blob.length - HEADER_LENGTH];
		System.arraycopy(blob, HEADER_LENGTH, compressed, 0, compressed.length);

		// Verify integrity
		byte[] computedHash = computeHash(compressed);
		if (!Arrays.equals(storedHash, computedHash)) {
			throw new ConversationStateSerializer.IntegrityException("Blob integrity check failed - data may have been tampered with");
		}

		try {
			// Decompress
			byte[] jsonBytes = decompress(compressed);
			String jsonString = new String(jsonBytes, StandardCharsets.UTF_8);

			// Parse
			ObjectNode json = (ObjectNode) mapper.readTree(jsonString);

			// Apply migrations if needed
			if (blobVersion < schemaVersion && migrationRegistry != null) {
				migrationRegistry.migrateToCurrentVersion(json, blobVersion);
			} else if (blobVersion < schemaVersion) {
				throw new ConversationStateSerializer.MigrationException(
						"Blob version " + blobVersion + " requires migration but no registry configured");
			}

			return jsonToState(json, typeRegistry);

		} catch (ConversationStateSerializer.IntegrityException | ConversationStateSerializer.MigrationException e) {
			throw e;
		} catch (Exception e) {
			throw new ConversationStateSerializer.MigrationException("Failed to deserialize conversation state", e);
		}
	}

	/**
	 * Gets the schema version used for new blobs.
	 * 
	 * @return the current schema version
	 */
	public int schemaVersion() {
		return schemaVersion;
	}

	@Override
	public String toReadableJson(byte[] blob) {
		if (blob == null || blob.length < HEADER_LENGTH) {
			return "{}";
		}

		try {
			byte[] compressed = new byte[blob.length - HEADER_LENGTH];
			System.arraycopy(blob, HEADER_LENGTH, compressed, 0, compressed.length);
			byte[] jsonBytes = decompress(compressed);
			String jsonString = new String(jsonBytes, StandardCharsets.UTF_8);

			// Pretty print
			Object parsed = mapper.readValue(jsonString, Object.class);
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);

		} catch (Exception e) {
			return "{\"error\": \"" + e.getMessage() + "\"}";
		}
	}

	private ObjectNode stateToJson(ConversationState state) {
		ObjectNode json = mapper.createObjectNode();
		json.put("originalInstruction", state.originalInstruction());
		json.put("latestUserMessage", state.latestUserMessage());

		// Pending params
		ArrayNode pendingArray = json.putArray("pendingParams");
		for (var param : state.pendingParams()) {
			ObjectNode p = pendingArray.addObject();
			p.put("name", param.name());
			p.put("message", param.message());
		}

		// Provided params
		ObjectNode providedNode = json.putObject("providedParams");
		for (var entry : state.providedParams().entrySet()) {
			providedNode.put(entry.getKey(), String.valueOf(entry.getValue()));
		}

		// Working context
		if (state.workingContext() != null) {
			json.set("workingContext", workingContextToJson(state.workingContext()));
		}

		// Turn history
		ArrayNode historyArray = json.putArray("turnHistory");
		for (var ctx : state.turnHistory()) {
			historyArray.add(workingContextToJson(ctx));
		}

		return json;
	}

	private ObjectNode workingContextToJson(WorkingContext<?> ctx) {
		ObjectNode json = mapper.createObjectNode();
		json.put("contextType", ctx.contextType());
		json.put("lastModified", ctx.lastModified().toString());

		// Serialize payload as JSON
		try {
			json.set("payload", mapper.valueToTree(ctx.payload()));
		} catch (Exception e) {
			json.put("payload", String.valueOf(ctx.payload()));
		}

		// Metadata
		ObjectNode metaNode = json.putObject("metadata");
		for (var entry : ctx.metadata().entrySet()) {
			metaNode.put(entry.getKey(), String.valueOf(entry.getValue()));
		}

		return json;
	}

	private ConversationState jsonToState(ObjectNode json, PayloadTypeRegistry typeRegistry) 
			throws JsonProcessingException {
		String originalInstruction = json.has("originalInstruction") && !json.get("originalInstruction").isNull()
				? json.get("originalInstruction").asText() : null;
		String latestUserMessage = json.has("latestUserMessage") && !json.get("latestUserMessage").isNull()
				? json.get("latestUserMessage").asText() : null;

		// Pending params
		List<org.javai.springai.actions.PlanStep.PendingParam> pendingParams = new ArrayList<>();
		if (json.has("pendingParams")) {
			for (JsonNode p : json.get("pendingParams")) {
				pendingParams.add(new org.javai.springai.actions.PlanStep.PendingParam(
						p.get("name").asText(),
						p.get("message").asText()));
			}
		}

		// Provided params
		Map<String, Object> providedParams = new HashMap<>();
		if (json.has("providedParams")) {
			json.get("providedParams").fields().forEachRemaining(entry -> 
					providedParams.put(entry.getKey(), entry.getValue().asText()));
		}

		// Working context
		WorkingContext<?> workingContext = null;
		if (json.has("workingContext") && !json.get("workingContext").isNull()) {
			workingContext = jsonToWorkingContext(json.get("workingContext"), typeRegistry);
		}

		// Turn history
		List<WorkingContext<?>> turnHistory = new ArrayList<>();
		if (json.has("turnHistory")) {
			for (JsonNode ctx : json.get("turnHistory")) {
				turnHistory.add(jsonToWorkingContext(ctx, typeRegistry));
			}
		}

		return new ConversationState(
				originalInstruction,
				pendingParams,
				providedParams,
				latestUserMessage,
				workingContext,
				turnHistory);
	}

	private WorkingContext<?> jsonToWorkingContext(JsonNode json, PayloadTypeRegistry typeRegistry) 
			throws JsonProcessingException {
		String contextType = json.get("contextType").asText();
		Instant lastModified = Instant.parse(json.get("lastModified").asText());

		// Resolve payload type
		Class<?> payloadClass = typeRegistry.getPayloadClass(contextType)
				.orElse(Object.class);
		Object payload = mapper.treeToValue(json.get("payload"), payloadClass);

		// Metadata
		Map<String, Object> metadata = new HashMap<>();
		if (json.has("metadata")) {
			json.get("metadata").fields().forEachRemaining(entry -> 
					metadata.put(entry.getKey(), entry.getValue().asText()));
		}

		return new WorkingContext<>(contextType, payload, lastModified, metadata);
	}

	private byte[] compress(byte[] data) throws Exception {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
			gzip.write(data);
		}
		return bos.toByteArray();
	}

	private byte[] decompress(byte[] compressed) throws Exception {
		ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try (GZIPInputStream gzip = new GZIPInputStream(bis)) {
			byte[] buffer = new byte[1024];
			int len;
			while ((len = gzip.read(buffer)) > 0) {
				bos.write(buffer, 0, len);
			}
		}
		return bos.toByteArray();
	}

	private byte[] computeHash(byte[] data) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return digest.digest(data);
		} catch (NoSuchAlgorithmException e) {
			// SHA-256 is guaranteed to be available in all Java implementations
			throw new RuntimeException("SHA-256 algorithm not available", e);
		}
	}
}

