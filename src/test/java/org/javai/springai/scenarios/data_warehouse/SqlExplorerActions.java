package org.javai.springai.scenarios.data_warehouse;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionParam;
import org.javai.springai.actions.sql.Query;

/**
 * Actions for SQL exploration scenarios.
 * <p>
 * This focused action set is designed for the "SQL Explorer" application pane,
 * where users explore data through ad-hoc SQL queries. It deliberately excludes
 * specialized aggregation actions to keep the LLM's action selection simple.
 * </p>
 */
public class SqlExplorerActions {
	private final AtomicBoolean showSqlQueryInvoked = new AtomicBoolean(false);
	private final AtomicBoolean runSqlQueryInvoked = new AtomicBoolean(false);
	private Query lastQuery;

	@Action(description = "Show a SQL SELECT query. YOU must generate the SQL based on the user's request and CURRENT QUERY CONTEXT. NEVER use PENDING.")
	public void showSqlQuery(
			@ActionParam(description = "A complete SQL SELECT statement that YOU generate. Example: \"SELECT order_value, customer_name FROM fct_orders JOIN dim_customer ON fct_orders.customer_id = dim_customer.id\"") 
			Query query) {
		showSqlQueryInvoked.set(true);
		lastQuery = query;
		System.out.println(query.sqlString(Query.Dialect.ANSI));
	}

	@Action(description = "Run a SQL SELECT query and return results. YOU must generate the SQL based on the user's request and CURRENT QUERY CONTEXT. NEVER use PENDING.")
	public void runSqlQuery(
			@ActionParam(description = "A complete SQL SELECT statement that YOU generate. Example: \"SELECT order_value FROM fct_orders WHERE region = 'East'\"") 
			Query query) {
		runSqlQueryInvoked.set(true);
		lastQuery = query;
		System.out.println(query.sqlString(Query.Dialect.ANSI));
	}

	public boolean showSqlQueryInvoked() {
		return showSqlQueryInvoked.get();
	}

	public boolean runSqlQueryInvoked() {
		return runSqlQueryInvoked.get();
	}

	public Optional<Query> lastQuery() {
		return Optional.ofNullable(lastQuery);
	}

	/**
	 * Resets all invocation flags and captured values for test isolation.
	 */
	public void reset() {
		showSqlQueryInvoked.set(false);
		runSqlQueryInvoked.set(false);
		lastQuery = null;
	}
}

