package org.javai.springai.actions.sxl.meta;

import java.util.List;

/**
 * Rule for a literal type (either regex or values).
 */
public record LiteralRule(
	String regex,  // regex pattern (for string, number)
	List<Object> values  // allowed values (for boolean, null)
) {
}

