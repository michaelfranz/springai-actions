package org.javai.springai.actions.sxl.meta;

/**
 * Global identifier rule.
 */
public record IdentifierRule(
	String description,
	String pattern  // regex pattern
) {
}

