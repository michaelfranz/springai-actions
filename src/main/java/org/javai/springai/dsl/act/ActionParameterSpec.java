package org.javai.springai.dsl.act;

/**
 * A spec that is communicated to the LLM, which describes the type of an action
 * parameter
 * @param typeName
 * @param description
 */
public record ActionParameterSpec(String typeName, String description) {
	public String toSxl() {
		return null;
	}
}
