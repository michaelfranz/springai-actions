package org.javai.springai.dsl.sql;

import org.javai.springai.sxl.SxlNode;

public record Query(SxlNode queryNode) {

	public Query {
		if (queryNode.isLiteral()) {
			throw new IllegalArgumentException("Cannot create a query from a literal");
		}
		if (!queryNode.symbol().equals("Q")) {
			throw new IllegalArgumentException("Cannot create a query from a node that is not a query");
		}
	}

	enum Dialect {
		ANSI, POSTGRES
	}

	String sqlString() {
		return SqlNodeVisitor.generate(queryNode);
	}

	String sqlString(Dialect dialect) {
		return switch (dialect) {
			case ANSI -> SqlNodeVisitor.generate(queryNode);
			case POSTGRES -> PostgreSqlNodeVisitor.generate(queryNode);
		};
	}

}
