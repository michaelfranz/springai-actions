package org.javai.springai.actions.sxl.meta;

import java.util.List;

/**
 * Embedding configuration.
 */
public record EmbeddingConfig(
	Boolean enabled,
	String symbol,  // e.g., "EMBED"
	Boolean autoRegisterSymbol,
	List<ParameterDefinition> params
) {
}

