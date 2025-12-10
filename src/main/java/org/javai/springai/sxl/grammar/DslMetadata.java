package org.javai.springai.sxl.grammar;

/**
 * DSL metadata (id, description, version).
 */
public record DslMetadata(
	String id,
	String description,
	String version
) {
	public <R> R accept(SxlGrammarVisitor<R> visitor) {
		return visitor.visitDslMetadata(this);
	}
}

