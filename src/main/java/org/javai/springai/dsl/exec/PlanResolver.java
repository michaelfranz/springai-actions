package org.javai.springai.dsl.exec;

import org.javai.springai.dsl.act.ActionRegistry;
import org.javai.springai.dsl.plan.Plan;

/**
 * Resolves a parsed Plan into executable steps (bindings + arguments).
 * Non-ready plans are mapped into resolved plans that may contain error steps.
 */
public interface PlanResolver {

	ResolvedPlan resolve(Plan plan, ActionRegistry registry);
}

