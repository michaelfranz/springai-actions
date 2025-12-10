package org.javai.springai.sxl.grammar;

/**
 * Named profile for LLM specification generation.
 */
public record LlmProfile(
	String style,
	Boolean includeConstraints,
	Integer maxExamples
) {
	public <R> R accept(SxlGrammarVisitor<R> visitor) {
		return visitor.visitLlmProfile(this);
	}
}

