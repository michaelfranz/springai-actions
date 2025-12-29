package org.javai.springai.dsl.sql;

/**
 * Exception thrown when SQL query validation fails.
 * 
 * <p>This exception is thrown when:</p>
 * <ul>
 *   <li>SQL syntax is invalid</li>
 *   <li>The statement is not a SELECT (e.g., INSERT, UPDATE, DELETE)</li>
 *   <li>The query references tables or columns not in the schema catalog</li>
 * </ul>
 */
public class QueryValidationException extends RuntimeException {

	public QueryValidationException(String message) {
		super(message);
	}

	public QueryValidationException(String message, Throwable cause) {
		super(message, cause);
	}
}

