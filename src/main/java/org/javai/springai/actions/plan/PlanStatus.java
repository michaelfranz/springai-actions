package org.javai.springai.actions.plan;

/**
 * Status of a plan after formulation.
 * READY   - no pending or error steps; safe to resolve/execute.
 * PENDING - one or more pending steps requiring user input.
 * ERROR   - an error step prevents resolution/execution.
 */
public enum PlanStatus {
	READY,
	PENDING,
	ERROR
}

