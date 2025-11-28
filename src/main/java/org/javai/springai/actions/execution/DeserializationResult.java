package org.javai.springai.actions.execution;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public sealed interface DeserializationResult<T> {
	record Success<T>(T value) implements DeserializationResult<T> {
	}

	record Failure<T>(List<String> errors, JsonNode rawJson) implements DeserializationResult<T> {
		public String toString() {
			return """
					The following Json object failed to de-serialize:
					%s
					
					With the following errors:
					%s""".formatted(rawJson.toPrettyString(), String.join("\n", errors));
		}

	}
}
