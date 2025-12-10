package org.javai.springai.actions.dsl.sql;

import org.javai.springai.actions.sxl.SxlNode;

public record Query(SxlNode queryNode) {

	String sqlString() {
		return SqlNodeVisitor.generate(queryNode);
	}

	String postgreSqlString() {
		return PostgreSqlNodeVisitor.generate(queryNode);
	}

}
