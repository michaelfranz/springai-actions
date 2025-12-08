package org.javai.springai.actions.sxl;

import java.util.List;

/**
 * Example visitor that pretty-prints an SxlNode AST back to s-expression format.
 * Demonstrates the visitor pattern for code generation.
 */
public class SxlPrettyPrinter implements SxlNodeVisitor<Void> {

	private final StringBuilder output = new StringBuilder();
	private int indentLevel = 0;
	private final boolean pretty;
	private final int indentSize;

	public SxlPrettyPrinter() {
		this(true, 2);
	}

	public SxlPrettyPrinter(boolean pretty, int indentSize) {
		this.pretty = pretty;
		this.indentSize = indentSize;
	}

	@Override
	public Void visitSymbol(String symbol, List<SxlNode> args) {
		output.append('(').append(symbol);
		
		if (args.isEmpty()) {
			output.append(')');
		} else {
			if (pretty && !args.isEmpty()) {
				indentLevel++;
				for (SxlNode arg : args) {
					output.append('\n');
					indent();
					arg.accept(this);
				}
				indentLevel--;
				output.append('\n');
				indent();
			} else {
				for (SxlNode arg : args) {
					output.append(' ');
					arg.accept(this);
				}
			}
			output.append(')');
		}
		
		return null;
	}

	@Override
	public Void visitLiteral(String value) {
		// Try to determine if it's a number or string
		if (isNumeric(value)) {
			output.append(value);
		} else {
			// Assume string literal - use single quotes as per tokenizer
			output.append('\'').append(escapeString(value)).append('\'');
		}
		return null;
	}

	private boolean isNumeric(String value) {
		if (value == null || value.isEmpty()) {
			return false;
		}
		try {
			Double.parseDouble(value);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private String escapeString(String value) {
		return value.replace("\\", "\\\\")
			.replace("'", "\\'")
			.replace("\n", "\\n")
			.replace("\t", "\\t")
			.replace("\r", "\\r");
	}

	private void indent() {
		if (pretty) {
			output.append(" ".repeat(indentLevel * indentSize));
		}
	}

	/**
	 * Returns the pretty-printed output as a string.
	 */
	public String toString() {
		return output.toString();
	}

	/**
	 * Static convenience method to pretty-print a node.
	 */
	public static String print(SxlNode node) {
		SxlPrettyPrinter printer = new SxlPrettyPrinter();
		node.accept(printer);
		return printer.toString();
	}

	/**
	 * Static convenience method to pretty-print a node in compact format.
	 */
	public static String printCompact(SxlNode node) {
		SxlPrettyPrinter printer = new SxlPrettyPrinter(false, 0);
		node.accept(printer);
		return printer.toString();
	}
}

