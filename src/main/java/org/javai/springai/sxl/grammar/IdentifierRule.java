package org.javai.springai.sxl.grammar;

/**
 * Global identifier rule.
 */
public record IdentifierRule(
	String description,
	String pattern  // regex pattern
) {
	public <R> R accept(SxlGrammarVisitor<R> visitor) {
		return visitor.visitIdentifierRule(this);
	}
}

