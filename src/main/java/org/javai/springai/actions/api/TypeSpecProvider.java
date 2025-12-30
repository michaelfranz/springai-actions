package org.javai.springai.actions.api;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Provides JSON schema specification for a custom parameter type.
 * 
 * <p>Implementations define how a type should be represented in the LLM prompt,
 * allowing special types (like SQL Query) to have tailored schemas instead of
 * the default reflection-based schema generation.</p>
 * 
 * <p>Example: A Query type might be specified as {@code { "sql": string }} rather
 * than exposing the full internal AST structure.</p>
 */
public interface TypeSpecProvider {

	/**
	 * Returns the Java type this provider handles.
	 */
	Class<?> supportedType();

	/**
	 * Generates the JSON schema for this type.
	 * 
	 * @return JSON schema object node
	 */
	ObjectNode schema();

	/**
	 * Returns optional guidance text to include in the prompt for this type.
	 * This guidance should explain how to construct valid values.
	 * 
	 * @return guidance text, or null if no additional guidance needed
	 */
	default String guidance() {
		return null;
	}
}

