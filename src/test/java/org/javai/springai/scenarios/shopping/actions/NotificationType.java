package org.javai.springai.scenarios.shopping.actions;

/**
 * Types of notifications that can be attached to action results.
 */
public enum NotificationType {
	/**
	 * Special offer or promotion notification.
	 * Business-critical: must always be surfaced when applicable.
	 */
	OFFER,

	/**
	 * Warning about something the user should be aware of.
	 * E.g., low stock, approaching budget limit.
	 */
	WARNING,

	/**
	 * General informational notification.
	 * E.g., loyalty points earned, delivery time estimate.
	 */
	INFO
}

