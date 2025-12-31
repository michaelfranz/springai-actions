package org.javai.springai.actions.sql;

import java.util.Map;
import java.util.Set;

/**
 * Tracks access patterns to SQL catalog schema elements.
 * 
 * <p>Used by the adaptive hybrid approach to determine which tables are
 * frequently accessed and should be promoted to the system prompt for
 * lower-latency responses.</p>
 * 
 * <h2>Usage Pattern</h2>
 * <ol>
 *   <li>Wrap {@link SqlCatalogTool} to record access via {@link #recordTableAccess}</li>
 *   <li>Query hot tables via {@link #getHotTables} with a threshold</li>
 *   <li>Include hot tables in system prompt via AdaptiveSqlCatalogContributor</li>
 * </ol>
 * 
 * <h2>Implementations</h2>
 * <ul>
 *   <li>{@link InMemorySchemaAccessTracker} - Simple in-memory implementation for testing</li>
 * </ul>
 * 
 * @see SqlCatalogTool
 */
public interface SchemaAccessTracker {

	/**
	 * Records an access to a table.
	 * 
	 * <p>Called when the LLM requests information about a table via tools.
	 * Uses canonical table names (not tokens) so tracking works regardless
	 * of tokenization mode.</p>
	 * 
	 * @param tableName the canonical table name that was accessed
	 */
	void recordTableAccess(String tableName);

	/**
	 * Gets the number of times a table has been accessed.
	 * 
	 * @param tableName the table name
	 * @return access count, or 0 if never accessed
	 */
	int getAccessCount(String tableName);

	/**
	 * Gets all tables that have been accessed at least {@code threshold} times.
	 * 
	 * <p>These are "hot" tables that should be promoted to the system prompt
	 * for lower-latency access.</p>
	 * 
	 * @param threshold minimum access count to be considered "hot"
	 * @return set of table names meeting the threshold (never null)
	 */
	Set<String> getHotTables(int threshold);

	/**
	 * Gets all access counts as a map.
	 * 
	 * <p>Useful for debugging and analytics.</p>
	 * 
	 * @return map of table name to access count (never null)
	 */
	Map<String, Integer> getAllAccessCounts();

	/**
	 * Resets all access tracking data.
	 * 
	 * <p>Useful for testing and for scenarios where you want to
	 * "forget" historical access patterns.</p>
	 */
	void reset();
}

