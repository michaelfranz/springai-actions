package org.javai.springai.actions.internal.resolve;

import org.javai.springai.actions.internal.bind.ActionRegistry;
import org.javai.springai.actions.sql.SqlCatalog;

/**
 * Context for plan resolution, bundling the action registry and optional SQL catalog.
 * 
 * <p>This allows the resolver to access schema information for validating
 * SQL queries against the catalog during resolution.</p>
 */
public record ResolutionContext(
		ActionRegistry registry,
		SqlCatalog sqlCatalog
) {
	/**
	 * Creates a context with just the registry (no SQL catalog).
	 */
	public static ResolutionContext of(ActionRegistry registry) {
		return new ResolutionContext(registry, null);
	}

	/**
	 * Creates a context with registry and SQL catalog.
	 */
	public static ResolutionContext of(ActionRegistry registry, SqlCatalog sqlCatalog) {
		return new ResolutionContext(registry, sqlCatalog);
	}
}

