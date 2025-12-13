package org.javai.springai.sxl.grammar;

/**
 * Override settings for a model.
 */
public record LlmOverrides(
	String style,
	Integer maxExamples,
	Boolean includeConstraints,
	String formatting,
	String guidance
) {
	public <R> R accept(SxlGrammarVisitor<R> visitor) {
		return visitor.visitLlmOverrides(this);
	}
}

