package org.javai.springai.actions.execution;

public class PlanExecutionException extends Exception {
	public PlanExecutionException(String message, Exception e) {
		super(message, e);
	}
}
