package org.javai.springai.dsl.bind;

import org.javai.springai.sxl.SxlNode;

public interface TypeFactory<T> {

	Class<T> getType();

	T create(SxlNode rootNode);

}
