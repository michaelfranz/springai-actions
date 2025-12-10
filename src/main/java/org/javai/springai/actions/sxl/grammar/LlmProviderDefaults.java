package org.javai.springai.actions.sxl.grammar;

/**
 * Provider-level default settings.
 */
public record LlmProviderDefaults(
	String style,
	Integer maxExamples
) {
	public <R> R accept(SxlGrammarVisitor<R> visitor) {
		return visitor.visitLlmProviderDefaults(this);
	}
}

