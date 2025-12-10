package org.javai.springai.sxl;

import java.util.List;

/**
 * Utility class for walking SxlNode ASTs with visitors.
 * Provides common traversal patterns for tree operations.
 */
public final class SxlNodeWalker {

	private SxlNodeWalker() {
		// Utility class - no instantiation
	}

	/**
	 * Walks an AST node tree, visiting each node with the provided visitor.
	 * Traverses in pre-order (visits node before children).
	 * 
	 * @param <R> the return type of the visitor
	 * @param node the root node to start traversal from
	 * @param visitor the visitor to apply to each node
	 * @return the result of visiting the root node
	 */
	public static <R> R walkPreOrder(SxlNode node, SxlNodeVisitor<R> visitor) {
		if (node == null) {
			return null;
		}

		R result = node.accept(visitor);
		
		// Visit children if this is a symbol node
		if (!node.isLiteral()) {
			for (SxlNode child : node.args()) {
				walkPreOrder(child, visitor);
			}
		}
		
		return result;
	}

	/**
	 * Walks an AST node tree, visiting each node with the provided visitor.
	 * Traverses in post-order (visits children before node).
	 * 
	 * @param <R> the return type of the visitor
	 * @param node the root node to start traversal from
	 * @param visitor the visitor to apply to each node
	 * @return the result of visiting the root node
	 */
	public static <R> R walkPostOrder(SxlNode node, SxlNodeVisitor<R> visitor) {
		if (node == null) {
			return null;
		}

		// Visit children first if this is a symbol node
		if (!node.isLiteral()) {
			for (SxlNode child : node.args()) {
				walkPostOrder(child, visitor);
			}
		}
		
		return node.accept(visitor);
	}

	/**
	 * Walks a list of AST nodes (top-level expressions).
	 * 
	 * @param <R> the return type of the visitor
	 * @param nodes the list of nodes to visit
	 * @param visitor the visitor to apply to each node
	 */
	public static <R> void walkAll(List<SxlNode> nodes, SxlNodeVisitor<R> visitor) {
		if (nodes == null) {
			return;
		}
		
		for (SxlNode node : nodes) {
			walkPreOrder(node, visitor);
		}
	}
}

