package org.javai.springai.scenarios.shopping.actions;

/**
 * A side-channel notification that is programmatically attached to action results.
 * These are guaranteed to be surfaced to the user - not left to LLM discretion.
 * <p>
 * Examples: special offers, low stock warnings, loyalty points updates.
 *
 * @param type the notification type (affects presentation)
 * @param title short title for the notification
 * @param body detailed notification message
 */
public record Notification(
		NotificationType type,
		String title,
		String body
) {

	/**
	 * Create an offer notification.
	 */
	public static Notification offer(String title, String description) {
		return new Notification(NotificationType.OFFER, title, description);
	}

	/**
	 * Create a warning notification.
	 */
	public static Notification warning(String title, String message) {
		return new Notification(NotificationType.WARNING, title, message);
	}

	/**
	 * Create an informational notification.
	 */
	public static Notification info(String title, String message) {
		return new Notification(NotificationType.INFO, title, message);
	}
}

