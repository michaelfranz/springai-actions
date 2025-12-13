package org.javai.springai.dsl.prompt;

import java.util.Map;
import java.util.Optional;

/**
 * Simple map-backed DSL guidance provider.
 */
public class MapBackedDslGuidanceProvider implements DslGuidanceProvider {

	private final Map<String, String> guidance;

	public MapBackedDslGuidanceProvider(Map<String, String> guidance) {
		this.guidance = guidance;
	}

	@Override
	public Optional<String> guidanceFor(String dslId, String providerId, String modelId) {
		return Optional.ofNullable(guidance.get(dslId));
	}
}
