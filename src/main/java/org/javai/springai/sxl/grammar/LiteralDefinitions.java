package org.javai.springai.sxl.grammar;

/**
 * Definitions for literal types.
 */
public record LiteralDefinitions(
	LiteralRule string,
	LiteralRule number,
	LiteralRule boolean_,
	LiteralRule null_
) {
	public <R> R accept(SxlGrammarVisitor<R> visitor) {
		return visitor.visitLiteralDefinitions(this);
	}
}

