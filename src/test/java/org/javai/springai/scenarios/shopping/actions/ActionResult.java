package org.javai.springai.scenarios.shopping.actions;

/**
 * Simple result type for action execution in the shopping scenario.
 * Captures success/failure status and a message.
 */
public record ActionResult(boolean success, String message) {

	/**
	 * Create a successful result.
	 */
	public static ActionResult success(String message) {
		return new ActionResult(true, message);
	}

	/**
	 * Create an error result.
	 */
	public static ActionResult error(String message) {
		return new ActionResult(false, message);
	}

	/**
	 * Check if the result is successful.
	 */
	public boolean isSuccess() {
		return success;
	}

	/**
	 * Check if the result is an error.
	 */
	public boolean isError() {
		return !success;
	}
}

