package org.javai.springai.dsl.bind;

import org.javai.springai.sxl.SxlNode;

/**
 * Resolves an embedded DSL payload into a typed Java object.
 */
public interface EmbeddedResolver {

	/**
	 * Resolve the given node using the factory registered under the DSL id, enforcing the expected type.
	 * @param dslId identifier of the embedded DSL
	 * @param node parsed S-expression root node
	 * @param expectedType required target type
	 * @return typed deserialized object
	 */
	<T> T resolve(String dslId, SxlNode node, Class<T> expectedType);
}
