package org.javai.springai.scenarios.data_warehouse;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionParam;

/**
 * Actions for aggregate calculation scenarios.
 * 
 * <p>This class provides the aggregateOrderValue action which requires
 * structured parameters with nested records. Used specifically for testing
 * PENDING parameter flow.</p>
 * 
 * <p>Keep this separate from {@link DataWarehouseActions} to avoid semantic
 * confusion when the LLM sees both SQL query actions and aggregate actions.</p>
 */
public class AggregateActions {
	private final AtomicBoolean aggregateOrderValueInvoked = new AtomicBoolean(false);
	private OrderValueQuery lastOrderValueQuery;

	@Action(description = """
			Calculate total order value for a customer over a date range.
			REQUIRES both customer_name AND period. If period is missing, use PENDING step.
			Parameter MUST be named 'orderValueQuery' - NOT 'customerName' (forbidden).""")
	public void aggregateOrderValue(
			@ActionParam(description = """
					EXACT SHAPE REQUIRED: {"customer_name":"<string>","period":{"start":"YYYY-MM-DD","end":"YYYY-MM-DD"}}
					FORBIDDEN KEYS: customerName, customer, customerId.
					If user doesn't provide date range, return PENDING step.""",
				examples = {"{\"customer_name\": \"Mike\", \"period\": {\"start\": \"2024-01-01\", \"end\": \"2024-01-31\"}}"})
			OrderValueQuery orderValueQuery) {
		aggregateOrderValueInvoked.set(true);
		lastOrderValueQuery = orderValueQuery;
		System.out.printf("Aggregating order value for %s from %s to %s%n",
				orderValueQuery.customer_name(),
				orderValueQuery.period().start(),
				orderValueQuery.period().end());
	}

	public boolean aggregateOrderValueInvoked() {
		return aggregateOrderValueInvoked.get();
	}

	public Optional<OrderValueQuery> lastOrderValueQuery() {
		return Optional.ofNullable(lastOrderValueQuery);
	}

	/**
	 * Resets all invocation flags and captured values for test isolation.
	 */
	public void reset() {
		aggregateOrderValueInvoked.set(false);
		lastOrderValueQuery = null;
	}
}

