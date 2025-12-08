package org.javai.springai.actions.sxl.meta;

/**
 * A DSL-wide (global) constraint.
 */
public record GlobalConstraint(
	String rule,  // e.g., "must_have_root", "order_requires"
	String target,  // rule-dependent target
	String symbol,  // for must_have_root
	String dependsOn  // for order_requires
) {
}

