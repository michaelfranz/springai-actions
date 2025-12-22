package org.javai.springai.dsl.act;

/**
 * LLM-facing description of an action parameter.
 *
 * @param name the parameter name
 * @param typeName the Java type name of the parameter
 * @param typeId short, LLM-facing type identifier (communicated to the LLM)
 * @param description human-friendly description
 * @param dslId optional DSL id if the parameter is supplied via an embedded DSL
 */
public record ActionParameterDescriptor(String name, String typeName, String typeId, String description, String dslId) {
	public String toSxl() {
		StringBuilder sb = new StringBuilder();
		sb.append("(").append(name).append(" ");
		if (dslId != null && !dslId.isBlank()) {
			sb.append("(EMBED ").append(dslId).append(" ...)");
		} else {
			sb.append(typeId);
		}
		sb.append(")");
		return sb.toString();
	}
}

