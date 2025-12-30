/**
 * JSON parsing types for LLM responses.
 * <p>
 * Contains {@code RawPlan} and {@code RawPlanStep} which are DTOs for
 * deserializing JSON from the LLM. These are internal types; use
 * {@link org.javai.springai.actions.Plan} and {@link org.javai.springai.actions.plan.PlanStep}
 * for application code.
 */
@org.springframework.lang.NonNullApi
package org.javai.springai.actions.internal.parse;

