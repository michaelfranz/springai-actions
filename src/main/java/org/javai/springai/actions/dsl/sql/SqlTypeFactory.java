package org.javai.springai.actions.dsl.sql;

import org.javai.springai.actions.dsl.TypeFactory;
import org.javai.springai.actions.sxl.SxlNode;

public class SqlTypeFactory implements TypeFactory<Query> {

	@Override
	public Query create(SxlNode rootNode) {
		if (rootNode.isLiteral()) {
			throw new IllegalArgumentException("Cannot create a query from a literal");
		}
		if (!rootNode.symbol().equals("Q")) {
			throw new IllegalArgumentException("Cannot create a query from a node that is not a query");
		}
		return new Query(rootNode);
	}
}
