package org.javai.springai.dsl.act;

/**
 * Strategy to select which action specs to emit (e.g., user-driven relevance).
 */
@FunctionalInterface
public interface ActionSpecFilter {
	boolean include(ActionSpec spec);

	ActionSpecFilter ALL = spec -> true;
}
