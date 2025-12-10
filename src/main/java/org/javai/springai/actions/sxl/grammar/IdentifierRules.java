package org.javai.springai.actions.sxl.grammar;

/**
 * Optional identifier rules for a parameter.
 */
public record IdentifierRules(
	String pattern  // regex pattern
) {
	public <R> R accept(SxlGrammarVisitor<R> visitor) {
		return visitor.visitIdentifierRules(this);
	}
}

