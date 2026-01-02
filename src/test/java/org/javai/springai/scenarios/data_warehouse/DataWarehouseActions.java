package org.javai.springai.scenarios.data_warehouse;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionParam;
import org.javai.springai.actions.sql.Query;

/**
 * Actions for data warehouse and SQL query scenarios.
 */
/**
 * Actions for SQL query scenarios.
 * 
 * <p>This class provides only SQL query actions (show/run). For aggregate
 * calculations that require structured parameters, see {@link AggregateActions}.</p>
 */
public class DataWarehouseActions {
	private final AtomicBoolean displaySqlQueryInvoked = new AtomicBoolean(false);
	private final AtomicBoolean executeAndDisplaySqlQueryInvoked = new AtomicBoolean(false);
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

	public boolean showSqlQueryInvoked() {
		return displaySqlQueryInvoked.get();
	}

	public boolean runSqlQueryInvoked() {
		return executeAndDisplaySqlQueryInvoked.get();
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
		lastQuery = null;
	}
}
