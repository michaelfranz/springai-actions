package org.javai.springai.dsl.sql;

import org.javai.springai.sxl.SxlNode;

public record Query(SxlNode queryNode) {

	String sqlString() {
		return SqlNodeVisitor.generate(queryNode);
	}

	String postgreSqlString() {
		return PostgreSqlNodeVisitor.generate(queryNode);
	}

}
