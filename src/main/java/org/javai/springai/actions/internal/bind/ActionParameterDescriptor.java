package org.javai.springai.actions.internal.bind;

/**
 * LLM-facing description of an action parameter.
 *
 * @param name            the parameter name
 * @param typeName        the Java type name of the parameter
 * @param typeId          short, LLM-facing type identifier (communicated to the LLM)
 * @param description     human-friendly description
 * @param allowedValues   optional list of whitelisted values (may be derived from enums)
 * @param allowedRegex    optional regex constraint
 * @param caseInsensitive whether allowed value matching is case-insensitive
 * @param examples        optional array of example values for this parameter
 */
public record ActionParameterDescriptor(
		String name,
		String typeName,
		String typeId,
		String description,
		String[] allowedValues,
		String allowedRegex,
		boolean caseInsensitive,
		String[] examples
) {
}

