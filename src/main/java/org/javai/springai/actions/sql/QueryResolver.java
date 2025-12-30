package org.javai.springai.actions.sql;

import java.util.Map;
import java.util.Optional;
import org.javai.springai.actions.api.TypeResolver;

/**
 * Resolves raw LLM output to a {@link Query} object.
 * 
 * <p>Expects the LLM to provide a JSON object with an "sql" field containing
 * the SELECT statement. This resolver:</p>
 * <ol>
 *   <li>Extracts the SQL string from the "sql" field</li>
 *   <li>Validates syntax (must be valid SQL)</li>
 *   <li>Validates it's a SELECT statement (no DDL/DML)</li>
 *   <li>Validates schema references against the catalog (if provided)</li>
 *   <li>Constructs and returns the Query object</li>
 * </ol>
 */
public final class QueryResolver implements TypeResolver {

	/** Context key for the SqlCatalog */
	public static final String CATALOG_CONTEXT_KEY = "sql";

	@Override
	public Class<?> supportedType() {
		return Query.class;
	}

	@Override
	public ResolveResult resolve(Object raw, Map<String, Object> context) {
		// Extract SQL string from the raw value
		return extractSql(raw)
				.map(sql -> resolveQuery(sql, context))
				.orElseGet(() -> ResolveResult.failure(
						"Query parameter must be an object with 'sql' field or a SQL string"));
	}

	private ResolveResult resolveQuery(String sql, Map<String, Object> context) {
		SqlCatalog catalog = getCatalog(context).orElse(null);
		try {
			Query query = Query.fromSql(sql, catalog);
			return ResolveResult.success(query);
		} catch (QueryValidationException e) {
			return ResolveResult.failure("Invalid SQL: " + e.getMessage());
		}
	}

	/**
	 * Extracts the SQL string from various input formats.
	 * Supports both { "sql": "..." } object format and raw string format.
	 */
	private Optional<String> extractSql(Object raw) {
		if (raw instanceof String s && !s.isBlank()) {
			return Optional.of(s);
		}
		if (raw instanceof Map<?, ?> map) {
			Object sqlValue = map.get("sql");
			if (sqlValue instanceof String s && !s.isBlank()) {
				return Optional.of(s);
			}
		}
		return Optional.empty();
	}

	private Optional<SqlCatalog> getCatalog(Map<String, Object> context) {
		if (context == null) {
			return Optional.empty();
		}
		Object value = context.get(CATALOG_CONTEXT_KEY);
		return value instanceof SqlCatalog catalog ? Optional.of(catalog) : Optional.empty();
	}
}

