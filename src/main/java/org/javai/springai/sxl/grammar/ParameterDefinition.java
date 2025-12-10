package org.javai.springai.sxl.grammar;

import java.util.List;

/**
 * Definition of a parameter for a symbol.
 */
public record ParameterDefinition(
	String name,
	String description,
	String type,  // node | literal(X) | identifier | dsl-id | embedded | any
	List<String> allowedSymbols,  // Only for type=node
	Cardinality cardinality,
	Boolean ordered,
	IdentifierRules identifierRules
) {
	public <R> R accept(SxlGrammarVisitor<R> visitor) {
		return visitor.visitParameterDefinition(this);
	}
}

