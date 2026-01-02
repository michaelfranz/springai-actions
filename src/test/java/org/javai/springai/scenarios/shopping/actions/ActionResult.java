package org.javai.springai.scenarios.shopping.actions;

import java.util.List;

/**
 * Result type for action execution in the shopping scenario.
 * Captures success/failure status, a message, and optional notifications.
 * <p>
 * Notifications are side-channel data that are guaranteed to be surfaced
 * to the user, regardless of LLM behaviour. This is critical for
 * business requirements like surfacing special offers.
 *
 * @param success whether the action succeeded
 * @param message the result message
 * @param notifications list of notifications to surface (offers, warnings, etc.)
 */
public record ActionResult(
		boolean success,
		String message,
		List<Notification> notifications
) {

	/**
	 * Create a successful result with no notifications.
	 */
	public static ActionResult success(String message) {
		return new ActionResult(true, message, List.of());
	}

	/**
	 * Create a successful result with notifications.
	 */
	public static ActionResult success(String message, List<Notification> notifications) {
		return new ActionResult(true, message, notifications != null ? notifications : List.of());
	}

	/**
	 * Create an error result.
	 */
	public static ActionResult error(String message) {
		return new ActionResult(false, message, List.of());
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

	/**
	 * Check if there are any notifications.
	 */
	public boolean hasNotifications() {
		return notifications != null && !notifications.isEmpty();
	}

	/**
	 * Get notifications of a specific type.
	 */
	public List<Notification> getNotifications(NotificationType type) {
		if (notifications == null) {
			return List.of();
		}
		return notifications.stream()
				.filter(n -> n.type() == type)
				.toList();
	}

	/**
	 * Get all offer notifications.
	 */
	public List<Notification> getOffers() {
		return getNotifications(NotificationType.OFFER);
	}
}
