package org.javai.springai.actions.sxl.grammar;

/**
 * An example for a symbol.
 */
public record Example(
	String label,
	String code  // s-expression example code
) {
	public <R> R accept(SxlGrammarVisitor<R> visitor) {
		return visitor.visitExample(this);
	}
}

