package org.javai.springai.dsl.exec;

import org.javai.springai.dsl.act.ActionRegistry;
import org.javai.springai.dsl.plan.Plan;

/**
 * Resolves a parsed Plan into executable steps (bindings + arguments) or structured errors.
 */
public interface PlanResolver {

	PlanResolutionResult resolve(Plan plan, ActionRegistry registry);
}

