package org.javai.springai.sxl;

/**
 * Visitor interface for traversing SxlNode ASTs.
 * 
 * This interface enables operations like code generation, analysis, and transformation
 * on s-expression ASTs.
 * 
 * @param <R> the return type of the visitor operations
 */
public interface SxlNodeVisitor<R> {

	/**
	 * Visits a symbol node (function call, identifier with children).
	 * 
	 * @param symbol the symbol name
	 * @param args the child nodes (arguments)
	 * @return the result of visiting this node
	 */
	R visitSymbol(String symbol, java.util.List<SxlNode> args);

	/**
	 * Visits a literal node (string, number, boolean, null).
	 * 
	 * @param value the literal value as a string
	 * @return the result of visiting this node
	 */
	R visitLiteral(String value);
}

