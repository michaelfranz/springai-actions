package org.javai.springai.sxl.grammar;

import java.util.List;

/**
 * A constraint rule for a symbol.
 */
public record SymbolConstraint(
	String rule,  // e.g., "requires", "must_have_root", "disallowed_together"
	String target,  // rule-dependent target
	String symbol,  // for must_have_root or similar
	List<String> items,  // list for disallowed_together
	String when  // optional predicate expression
) {
	public <R> R accept(SxlGrammarVisitor<R> visitor) {
		return visitor.visitSymbolConstraint(this);
	}
}

