package org.javai.springai.actions.sxl.grammar;

/**
 * Model-specific override settings.
 */
public record LlmModelOverrides(
	LlmOverrides overrides
) {
	public <R> R accept(SxlGrammarVisitor<R> visitor) {
		return visitor.visitLlmModelOverrides(this);
	}
}

