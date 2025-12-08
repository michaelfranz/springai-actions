package org.javai.springai.actions.sxl.meta;

/**
 * Definitions for literal types.
 */
public record LiteralDefinitions(
	LiteralRule string,
	LiteralRule number,
	LiteralRule boolean_,
	LiteralRule null_
) {
}

