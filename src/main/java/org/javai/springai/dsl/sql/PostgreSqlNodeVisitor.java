package org.javai.springai.dsl.sql;

import org.javai.springai.sxl.SxlNode;

import java.util.List;

/**
 * PostgreSQL-specific SQL visitor.
 * 
 * Extends SqlNodeVisitor and overrides methods where PostgreSQL differs
 * from standard ANSI SQL. Currently handles:
 * - ILIKE operator (PostgreSQL-specific case-insensitive LIKE)
 */
public class PostgreSqlNodeVisitor extends SqlNodeVisitor {

	/**
	 * Generate PostgreSQL SQL from a node AST.
	 */
	public static String generate(SxlNode queryNode) {
		if (queryNode.isLiteral()) {
			throw new IllegalArgumentException("Cannot create a query from a literal");
		}
		if (!queryNode.symbol().equals("Q")) {
			throw new IllegalArgumentException("Cannot create a query from a node that is not a query");
		}
		PostgreSqlNodeVisitor visitor = new PostgreSqlNodeVisitor();
		return queryNode.accept(visitor);
	}

	@Override
	protected SqlNodeVisitor createVisitor() {
		return new PostgreSqlNodeVisitor();
	}

	@Override
	protected void visitIlike(List<SxlNode> args) {
		// PostgreSQL supports ILIKE natively
		if (args.size() == 2) {
			SqlNodeVisitor visitor = createVisitor();
			args.getFirst().accept(visitor);
			append(visitor.sql.toString());
			append(" ILIKE ");
			visitor = createVisitor();
			args.get(1).accept(visitor);
			append(visitor.sql.toString());
		}
	}

	// Additional PostgreSQL-specific overrides can be added here as needed
	// For example, if DATE_TRUNC or EXTRACT need different syntax, or
	// if there are other PostgreSQL-specific functions in the future
}

