package org.javai.springai.actions.dsl;

import org.javai.springai.actions.sxl.SxlNode;

public interface TypeFactory<T> {

	T create(SxlNode rootNode);

}
