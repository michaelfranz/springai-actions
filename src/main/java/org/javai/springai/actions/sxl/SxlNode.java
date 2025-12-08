package org.javai.springai.actions.sxl;

import java.util.Collections;
import java.util.List;

/**
 * Represents a node in the parsed s-expression AST.
 * 
 * A node can be either:
 * - A literal value (string, number) - has a literalValue but no symbol
 * - A symbol/function call - has a symbol and optional args
 */
public record SxlNode(String symbol, List<SxlNode> args, String literalValue) {

	/**
	 * Creates a symbol node (function call, identifier with children).
	 */
	public static SxlNode symbol(String symbol, List<SxlNode> args) {
		return new SxlNode(symbol, args, null);
	}

	/**
	 * Creates a literal node (string, number).
	 */
	public static SxlNode literal(String value) {
		return new SxlNode(null, Collections.emptyList(), value);
	}

	/**
	 * Creates a symbol node with no arguments.
	 */
	public static SxlNode symbol(String symbol) {
		return new SxlNode(symbol, Collections.emptyList(), null);
	}

	/**
	 * Checks if this node represents a literal value.
	 */
	public boolean isLiteral() {
		return literalValue != null;
	}
}

