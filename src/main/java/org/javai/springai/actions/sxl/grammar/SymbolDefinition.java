package org.javai.springai.actions.sxl.grammar;

import java.util.List;

/**
 * Definition of a symbol in the DSL.
 */
public record SymbolDefinition(
	String description,
	SymbolKind kind,
	List<ParameterDefinition> params,
	List<SymbolConstraint> constraints,
	List<Example> examples
) {
	public <R> R accept(SxlGrammarVisitor<R> visitor) {
		return visitor.visitSymbolDefinition(this);
	}
}

