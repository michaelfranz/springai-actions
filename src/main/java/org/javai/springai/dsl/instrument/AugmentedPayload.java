package org.javai.springai.dsl.instrument;

import java.util.Map;

public record AugmentedPayload(String content, Map<String, Object> metadata) {

	public AugmentedPayload {
		content = content != null ? content : "";
		metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
	}

	public static AugmentedPayload of(String content) {
		return new AugmentedPayload(content, Map.of());
	}
}

