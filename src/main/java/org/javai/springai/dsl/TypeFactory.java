package org.javai.springai.dsl;

import org.javai.springai.sxl.SxlNode;

public interface TypeFactory<T> {

	T create(SxlNode rootNode);

}
