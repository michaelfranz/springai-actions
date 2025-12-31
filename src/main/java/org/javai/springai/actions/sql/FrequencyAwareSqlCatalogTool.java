package org.javai.springai.actions.sql;

import java.util.List;
import java.util.Objects;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * A wrapper around {@link SqlCatalogTool} that records table access patterns.
 * 
 * <p>This tool delegates all operations to the underlying {@link SqlCatalogTool}
 * while recording which tables are accessed via a {@link SchemaAccessTracker}.
 * The tracked data can then be used by {@link AdaptiveSqlCatalogContributor}
 * to promote frequently-accessed tables to the system prompt.</p>
 * 
 * <h2>Usage</h2>
 * <pre>{@code
 * SqlCatalog catalog = ...;
 * SchemaAccessTracker tracker = new InMemorySchemaAccessTracker();
 * SqlCatalogTool baseTool = new SqlCatalogTool(catalog);
 * FrequencyAwareSqlCatalogTool tool = new FrequencyAwareSqlCatalogTool(baseTool, tracker);
 * 
 * // Use tool in Planner - accesses are automatically tracked
 * planner.tools(tool);
 * }</pre>
 * 
 * @see SqlCatalogTool
 * @see SchemaAccessTracker
 * @see AdaptiveSqlCatalogContributor
 */
public class FrequencyAwareSqlCatalogTool {

	private final SqlCatalogTool delegate;
	private final SchemaAccessTracker tracker;

	/**
	 * Creates a new frequency-aware tool.
	 * 
	 * @param delegate the underlying tool to delegate to
	 * @param tracker the tracker to record access patterns
	 */
	public FrequencyAwareSqlCatalogTool(SqlCatalogTool delegate, SchemaAccessTracker tracker) {
		this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
		this.tracker = Objects.requireNonNull(tracker, "tracker must not be null");
	}

	/**
	 * Lists all tables in the catalog.
	 * 
	 * <p>Note: This does not record access since no specific table is being queried.</p>
	 * 
	 * @return list of table summaries
	 */
	@Tool(name = "listTables", description = """
			List all tables in the data warehouse with their descriptions and types.
			Use this first to understand what data is available before formulating a query.
			Returns: table name, description, type (fact/dimension/bridge), column count, and synonyms.""")
	public List<TableSummary> listTables() {
		return delegate.listTables();
	}

	/**
	 * Gets detailed information about a specific table.
	 * 
	 * <p>Records the table access for frequency tracking.</p>
	 * 
	 * @param tableName the table name (or token if catalog is tokenized)
	 * @return table details including columns, or null if not found
	 */
	@Tool(name = "getTableDetails", description = """
			Get detailed column information for a specific table.
			Call this for tables relevant to the user's query.
			Returns: column names, data types, descriptions, tags (pk, fk:table.column, measure, attribute), and constraints.
			For JOINs, look at fk: tags which show the target table and column.""")
	public TableDetail getTableDetails(
			@ToolParam(description = "Name of the table to get details for") String tableName) {
		
		TableDetail detail = delegate.getTableDetails(tableName);
		
		// Record access if table was found
		if (detail != null) {
			// Use the canonical name for tracking (not the display name/token)
			String canonicalName = resolveCanonicalName(tableName);
			if (canonicalName != null) {
				tracker.recordTableAccess(canonicalName);
			}
		}
		
		return detail;
	}

	/**
	 * Resolves a display name (possibly a token or synonym) to its canonical table name.
	 */
	private String resolveCanonicalName(String displayName) {
		// The delegate's lastTableRequested gives us what was looked up,
		// but we need the canonical name. We'll track using the display name
		// and let the tracker handle the mapping.
		// For now, use the display name - the AdaptiveSqlCatalogContributor
		// will need to handle the mapping when querying hot tables.
		return displayName;
	}

	/**
	 * Gets the underlying tracker for inspection.
	 * 
	 * @return the schema access tracker
	 */
	public SchemaAccessTracker getTracker() {
		return tracker;
	}

	/**
	 * Gets the underlying delegate tool.
	 * 
	 * @return the delegate tool
	 */
	public SqlCatalogTool getDelegate() {
		return delegate;
	}
}

