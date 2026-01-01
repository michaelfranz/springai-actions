package org.javai.springai.scenarios.data_warehouse;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionParam;
import org.javai.springai.actions.sql.Query;

/**
 * Actions for data warehouse and SQL query scenarios.
 */
public class DataWarehouseActions {
	private final AtomicBoolean displaySqlQueryInvoked = new AtomicBoolean(false);
	private final AtomicBoolean executeAndDisplaySqlQueryInvoked = new AtomicBoolean(false);
	private final AtomicBoolean aggregateOrderValueInvoked = new AtomicBoolean(false);
	private OrderValueQuery lastOrderValueQuery;
	private Query lastQuery;

	@Action(description = "Show a SQL query as text without executing it against the database.")
	public void showSqlQuery(
			@ActionParam(description = "The SQL query to show") Query query) {
		displaySqlQueryInvoked.set(true);
		lastQuery = query;
		System.out.println(query.sqlString(Query.Dialect.ANSI));
	}

	@Action(description = "Run a SQL query against the database and return actual data rows.")
	public void runSqlQuery(
			@ActionParam(description = "The SQL query to run") Query query) {
		executeAndDisplaySqlQueryInvoked.set(true);
		lastQuery = query;
		System.out.println(query.sqlString(Query.Dialect.ANSI));
	}

	@Action(description = "Calculate total order value for a customer over a date range.")
	public void aggregateOrderValue(
			@ActionParam(description = "Order value query with customer name and date period",
				examples = {"{\"customer_name\": \"Mike\", \"period\": {\"start\": \"2024-01-01\", \"end\": \"2024-01-31\"}}"})
			OrderValueQuery orderValueQuery) {
		aggregateOrderValueInvoked.set(true);
		lastOrderValueQuery = orderValueQuery;
		System.out.printf("Aggregating order value for %s from %s to %s%n",
				orderValueQuery.customer_name(),
				orderValueQuery.period().start(),
				orderValueQuery.period().end());
	}


	public boolean showSqlQueryInvoked() {
		return displaySqlQueryInvoked.get();
	}

	public boolean runSqlQueryInvoked() {
		return executeAndDisplaySqlQueryInvoked.get();
	}

	public boolean aggregateOrderValueInvoked() {
		return aggregateOrderValueInvoked.get();
	}

	public Optional<OrderValueQuery> lastOrderValueQuery() {
		return Optional.ofNullable(lastOrderValueQuery);
	}

	public Optional<Query> lastQuery() {
		return Optional.ofNullable(lastQuery);
	}

	/**
	 * Resets all invocation flags and captured values for test isolation.
	 */
	public void reset() {
		displaySqlQueryInvoked.set(false);
		executeAndDisplaySqlQueryInvoked.set(false);
		aggregateOrderValueInvoked.set(false);
		lastOrderValueQuery = null;
		lastQuery = null;
	}
}
