package org.javai.springai.actions.exec;

import org.javai.springai.actions.bind.ActionRegistry;
import org.javai.springai.actions.plan.Plan;

/**
 * Resolves a parsed Plan into executable steps (bindings + arguments).
 * Non-ready plans are mapped into resolved plans that may contain error steps.
 */
public interface PlanResolver {

	ResolvedPlan resolve(Plan plan, ActionRegistry registry);
}

