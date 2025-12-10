package org.javai.springai.sxl.grammar;

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
	public <R> R accept(SxlGrammarVisitor<R> visitor) {
		return visitor.visitEmbeddingConfig(this);
	}
}

