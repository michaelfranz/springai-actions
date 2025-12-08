package org.javai.springai.actions.sxl.meta;

/**
 * Named profile for LLM specification generation.
 */
public record LlmProfile(
	String style,
	Boolean includeConstraints,
	Integer maxExamples
) {
}

