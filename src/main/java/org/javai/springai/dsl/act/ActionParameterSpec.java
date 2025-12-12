package org.javai.springai.dsl.act;

/**
 * A spec communicated to the LLM describing an action parameter.
 *
 * @param typeName the Java type name of the parameter
 * @param description human-friendly description
 * @param dslId optional DSL id if the parameter is supplied via an embedded DSL
 */
public record ActionParameterSpec(String typeName, String description, String dslId) {
	public String toSxl() {
		return null;
	}
}
