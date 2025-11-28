package org.javai.springai.actions.execution;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

public class SafeDeserializer {

	public static <T> DeserializationResult<T> tryConvert(JsonNode node, Class<T> clazz, ObjectMapper mapper) {
		try {
			T value = mapper.treeToValue(node, clazz);
			return new DeserializationResult.Success<>(value);

		} catch (JsonMappingException ex) {
			// Capture detailed path error information
			List<String> errors = ex.getPath().stream()
					.map(ref -> "At '" + ref.getFieldName() + "': " + ex.getOriginalMessage())
					.toList();

			return new DeserializationResult.Failure<>(errors, node);

		} catch (Exception ex) {
			// Generic fallback
			List<String> errors = List.of(
					"Unexpected error during deserialization: " + ex.getMessage()
			);
			return new DeserializationResult.Failure<>(errors, node);
		}
	}
}