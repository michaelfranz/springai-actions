package org.javai.springai.actions.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Tool providing dynamic access to SQL catalog metadata for LLMs.
 * 
 * <p>This tool allows LLMs to discover database schema information on-demand,
 * rather than having the entire schema in the system prompt. This is particularly
 * useful for large schemas where including everything in the prompt would be
 * expensive or hit token limits.</p>
 * 
 * <h2>Usage Pattern</h2>
 * 
 * <p>The LLM typically:</p>
 * <ol>
 *   <li>Calls {@link #listTables()} to see available tables and their purposes</li>
 *   <li>Calls {@link #getTableDetails(String)} for tables relevant to the user's query</li>
 *   <li>Formulates the SQL query using the discovered schema</li>
 * </ol>
 * 
 * <p>Foreign key relationships are included in column tags (e.g., {@code fk:dim_customer.id}),
 * so the LLM can derive JOIN patterns directly from {@link #getTableDetails(String)}.</p>
 * 
 * <h2>Tokenization</h2>
 * 
 * <p>When the underlying catalog has tokenization enabled, this tool returns
 * tokenized names (synonyms or cryptic identifiers) instead of real database
 * object names. The LLM uses these names in its SQL, and the framework
 * translates them back to real names during query processing.</p>
 * 
 * @see SqlCatalog
 * @see TableSummary
 * @see TableDetail
 */
public class SqlCatalogTool {

	private final SqlCatalog catalog;
	
	// Invocation tracking for testing
	private final AtomicInteger listTablesCount = new AtomicInteger(0);
	private final AtomicInteger getTableDetailsCount = new AtomicInteger(0);
	private String lastTableRequested;

	/**
	 * Creates a new SqlCatalogTool backed by the given catalog.
	 * 
	 * @param catalog the SQL catalog to query
	 */
	public SqlCatalogTool(SqlCatalog catalog) {
		this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
	}

	/**
	 * Lists all tables in the catalog with summary information.
	 * 
	 * <p>Returns table names, descriptions, types (fact/dimension), and column counts
	 * to help the LLM decide which tables are relevant to the user's query.</p>
	 * 
	 * @return list of table summaries
	 */
	@Tool(name = "listTables", description = """
			List all tables in the data warehouse with their descriptions and types.
			Use this first to understand what data is available before formulating a query.
			Returns: table name, description, type (fact/dimension/bridge), column count, and synonyms.""")
	public List<TableSummary> listTables() {
		listTablesCount.incrementAndGet();
		
		List<TableSummary> summaries = new ArrayList<>();
		catalog.tables().forEach((tableName, table) -> {
			String displayName = catalog.usesModelNames() 
					? catalog.getTableModelName(tableName).orElse(tableName)
					: tableName;
			summaries.add(TableSummary.from(displayName, table));
		});
		return summaries;
	}

	/**
	 * Gets detailed information about a specific table including all columns.
	 * 
	 * <p>Call this after {@link #listTables()} to get full column information
	 * for tables relevant to the user's query. Foreign key relationships are
	 * included in column tags (e.g., {@code fk:dim_customer.id}).</p>
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
		getTableDetailsCount.incrementAndGet();
		lastTableRequested = tableName;
		
		// Find the table - may be by canonical name or token
		String canonicalName = findCanonicalTableName(tableName);
		if (canonicalName == null) {
			return null;
		}
		
		SqlCatalog.SqlTable table = catalog.tables().get(canonicalName);
		if (table == null) {
			return null;
		}
		
		String displayName = catalog.usesModelNames() 
				? catalog.getTableModelName(canonicalName).orElse(canonicalName)
				: canonicalName;
		
		return TableDetail.from(displayName, table, catalog, canonicalName);
	}

	/**
	 * Finds the canonical table name from a display name (which may be a token).
	 */
	private String findCanonicalTableName(String displayName) {
		if (displayName == null) {
			return null;
		}
		
		// First, check if it's a canonical name
		if (catalog.tables().containsKey(displayName)) {
			return displayName;
		}
		
		// If tokenized, try to resolve the token
		if (catalog.usesModelNames()) {
			String resolved = catalog.resolveTableFromModelName(displayName).orElse(null);
			if (resolved != null) {
				return resolved;
			}
		}
		
		// Try synonym matching
		String resolved = catalog.resolveTableName(displayName).orElse(null);
		if (resolved != null) {
			return resolved;
		}
		
		return null;
	}

	// Test accessors
	
	public int listTablesInvokedCount() {
		return listTablesCount.get();
	}

	public int getTableDetailsInvokedCount() {
		return getTableDetailsCount.get();
	}

	public String lastTableRequested() {
		return lastTableRequested;
	}

	public void resetCounters() {
		listTablesCount.set(0);
		getTableDetailsCount.set(0);
		lastTableRequested = null;
	}
}
