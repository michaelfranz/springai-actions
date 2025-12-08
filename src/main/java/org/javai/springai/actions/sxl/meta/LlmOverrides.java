package org.javai.springai.actions.sxl.meta;

/**
 * Override settings for a model.
 */
public record LlmOverrides(
	String style,
	Integer maxExamples,
	Boolean includeConstraints,
	String formatting
) {
}

