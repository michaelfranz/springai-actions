package org.javai.springai.scenarios.data_warehouse;

import java.util.concurrent.atomic.AtomicBoolean;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionParam;
import org.javai.springai.dsl.sql.Query;

/**
 * Actions for data warehouse and SQL query scenarios.
 */
public class DataWarehouseActions {
	private final AtomicBoolean displaySqlQueryInvoked = new AtomicBoolean(false);
	private final AtomicBoolean executeAndDisplaySqlQueryInvoked = new AtomicBoolean(false);
	private final AtomicBoolean aggregateOrderValueInvoked = new AtomicBoolean(false);
	private OrderValueQuery lastOrderValueQuery;

	@Action(description = """
			Display a SQL query WITHOUT executing it.
			Use ONLY when user says: create, show, build, display, make.
			DO NOT use for: execute, run, get.""")
	public void displaySqlQuery(
			@ActionParam(description = "Must be (EMBED sxl-sql (Q ...))", 
				examples = {"(EMBED sxl-sql (Q (F dim_customer c) (S c.customer_name)))"}) Query query) {
		displaySqlQueryInvoked.set(true);
		System.out.println(query.sqlString(Query.Dialect.ANSI));
	}

	@Action(description = """
			Execute a SQL query and return results.
			Use ONLY when user says: execute, run, get, fetch.
			DO NOT use for: create, show, build, display.""")
	public void executeAndDisplaySqlQuery(
			@ActionParam(description = "Must be (EMBED sxl-sql (Q ...))",
				examples = {"(EMBED sxl-sql (Q (F fct_orders o) (S o.order_value)))"}) Query query) {
		executeAndDisplaySqlQueryInvoked.set(true);
		System.out.println(query.sqlString(Query.Dialect.ANSI));
	}

	@Action(description = """
			Calculate total order value for a customer over a date range.
			Use when user asks for order value aggregation by customer and date period.""")
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


	public boolean displaySqlQueryInvoked() {
		return displaySqlQueryInvoked.get();
	}

	public boolean executeAndDisplaySqlQueryInvoked() {
		return executeAndDisplaySqlQueryInvoked.get();
	}

	public boolean aggregateOrderValueInvoked() {
		return aggregateOrderValueInvoked.get();
	}

	public OrderValueQuery lastOrderValueQuery() {
		return lastOrderValueQuery;
	}
}

