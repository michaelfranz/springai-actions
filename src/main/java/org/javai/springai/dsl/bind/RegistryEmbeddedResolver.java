package org.javai.springai.dsl.bind;

import org.javai.springai.sxl.SxlNode;

/**
 * Registry-backed resolver that enforces the expected type for embedded DSL payloads.
 */
public class RegistryEmbeddedResolver implements EmbeddedResolver {

	@Override
	public <T> T resolve(String dslId, SxlNode node, Class<T> expectedType) {
		return TypeFactoryRegistry.getFactory(dslId, expectedType)
				.orElseThrow(() -> new IllegalStateException("No factory registered for dslId: " + dslId))
				.create(node);
	}
}
