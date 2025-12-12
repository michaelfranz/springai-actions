package org.javai.springai.dsl.sql;

import org.javai.springai.dsl.bind.TypeFactory;
import org.javai.springai.sxl.SxlNode;

public class SqlTypeFactory implements TypeFactory<Query> {

	@Override
	public Class<Query> getType() { return Query.class; }


	@Override
	public Query create(SxlNode rootNode) {
		return Query.of(rootNode);
	}
}
