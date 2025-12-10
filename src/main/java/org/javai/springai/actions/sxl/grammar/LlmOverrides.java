package org.javai.springai.actions.sxl.grammar;

/**
 * Override settings for a model.
 */
public record LlmOverrides(
	String style,
	Integer maxExamples,
	Boolean includeConstraints,
	String formatting
) {
	public <R> R accept(SxlGrammarVisitor<R> visitor) {
		return visitor.visitLlmOverrides(this);
	}
}

