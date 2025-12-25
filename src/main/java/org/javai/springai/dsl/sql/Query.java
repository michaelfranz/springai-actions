package org.javai.springai.dsl.sql;

import org.javai.springai.sxl.SExpressionType;
import org.javai.springai.sxl.SxlNode;

public record Query(QueryStringFactory queryStringFactory) implements SExpressionType {

	@Override
	public String dslId() {
		return "sxl-sql";
	}

	private interface QueryStringFactory {
		String create(Query query, Dialect dialect);
	}

	public static Query of(SxlNode queryNode) {
		return new Query((query, dialect) -> switch (dialect) {
			case ANSI -> SqlNodeVisitor.generate(queryNode);
			case POSTGRES -> PostgreSqlNodeVisitor.generate(queryNode);
		});
	}

	public enum Dialect {
		ANSI, POSTGRES
	}

	public String sqlString() {
		return sqlString(Dialect.ANSI);
	}

	public String sqlString(Dialect dialect) {
		return queryStringFactory.create(this, dialect);
	}

}
