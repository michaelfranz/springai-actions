package org.javai.springai.actions.sql;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import org.javai.springai.actions.PromptContributor;
import org.javai.springai.actions.internal.prompt.SystemPromptContext;

/**
 * A prompt contributor that adaptively includes schema based on access frequency.
 * 
 * <p>This contributor works with {@link SchemaAccessTracker} to determine which
 * tables are "hot" (frequently accessed) and should be included in the system
 * prompt. Infrequently accessed tables are omitted, keeping the prompt lean
 * while still being discoverable via {@link SqlCatalogTool}.</p>
 * 
 * <h2>Adaptive Behavior</h2>
 * <ul>
 *   <li><b>Cold start</b>: No tables in prompt (all discovered via tool)</li>
 *   <li><b>After use</b>: Frequently-accessed tables promoted to prompt</li>
 *   <li><b>Steady state</b>: Hot tables in prompt, cold tables via tool</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * <pre>{@code
 * SqlCatalog catalog = ...;
 * SchemaAccessTracker tracker = new InMemorySchemaAccessTracker();
 * 
 * // Contributor only includes tables accessed >= 3 times
 * AdaptiveSqlCatalogContributor contributor = new AdaptiveSqlCatalogContributor(
 *         catalog, tracker, 3);
 * 
 * // Use in Planner alongside the tracking tool
 * FrequencyAwareSqlCatalogTool tool = new FrequencyAwareSqlCatalogTool(
 *         new SqlCatalogTool(catalog), tracker);
 * 
 * planner.contributor(contributor).tools(tool);
 * }</pre>
 * 
 * @see SchemaAccessTracker
 * @see FrequencyAwareSqlCatalogTool
 * @see SqlCatalogContextContributor
 */
public class AdaptiveSqlCatalogContributor implements PromptContributor {

	private static final String SQL_CATALOG_FOOTER = """
			
			🔴 CRITICAL: SQL table/column names MUST be taken from this catalog exactly as shown.
			- Use the table NAME shown before the colon, NOT user's informal terms
			- Use the column NAME shown after the bullet, NOT invented names
			- For JOINs, use FK relationships shown in column tags (e.g., fk:table.id means JOIN table ON ... = table.id)
			- If a name doesn't appear in this catalog, use the listTables/getTableDetails tools to discover it
			""";

	private static final String NO_HOT_TABLES_MESSAGE = """
			SQL CATALOG:
			No frequently-used tables yet. Use the listTables and getTableDetails tools to discover available tables.
			""";

	private final SqlCatalog catalog;
	private final SchemaAccessTracker tracker;
	private final int hotThreshold;

	/**
	 * Creates an adaptive contributor.
	 * 
	 * @param catalog the SQL catalog containing schema information
	 * @param tracker the tracker recording access patterns
	 * @param hotThreshold minimum access count to include a table in the prompt
	 */
	public AdaptiveSqlCatalogContributor(SqlCatalog catalog, SchemaAccessTracker tracker, int hotThreshold) {
		this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
		this.tracker = Objects.requireNonNull(tracker, "tracker must not be null");
		if (hotThreshold < 1) {
			throw new IllegalArgumentException("hotThreshold must be at least 1");
		}
		this.hotThreshold = hotThreshold;
	}

	@Override
	public Optional<String> contribute(SystemPromptContext context) {
		Set<String> hotTables = tracker.getHotTables(hotThreshold);
		
		if (hotTables.isEmpty()) {
			return Optional.of(NO_HOT_TABLES_MESSAGE.trim());
		}
		
		return Optional.of(contributeHotTables(hotTables));
	}

	/**
	 * Contributes schema for hot tables only.
	 */
	private String contributeHotTables(Set<String> hotTables) {
		StringBuilder sb = new StringBuilder("SQL CATALOG (frequently used tables):\n");
		
		catalog.tables().forEach((tableName, table) -> {
			// Only include hot tables
			if (!hotTables.contains(tableName)) {
				return;
			}
			
			String displayName = catalog.isTokenized() 
					? catalog.getTableToken(tableName).orElse(tableName)
					: tableName;
			
			sb.append("- ").append(displayName);
			if (table.description() != null && !table.description().isBlank()) {
				sb.append(": ").append(table.description());
			}
			if (table.tags() != null && !table.tags().isEmpty()) {
				sb.append(" [tags: ").append(String.join(", ", table.tags())).append("]");
			}
			sb.append("\n");
			
			// Include columns
			if (table.columns() != null && !table.columns().isEmpty()) {
				for (var col : table.columns()) {
					String columnDisplayName = catalog.isTokenized()
							? catalog.getColumnToken(tableName, col.name()).orElse(col.name())
							: col.name();
					
					sb.append("  • ").append(columnDisplayName);
					StringJoiner details = new StringJoiner("; ");
					if (col.dataType() != null && !col.dataType().isBlank()) {
						details.add("type=" + col.dataType());
					}
					if (col.description() != null && !col.description().isBlank()) {
						details.add(col.description());
					}
					if (col.tags() != null && !col.tags().isEmpty()) {
						details.add("tags=" + String.join(",", col.tags()));
					}
					if (details.length() > 0) {
						sb.append(" (").append(details).append(")");
					}
					sb.append("\n");
				}
			}
		});
		
		sb.append(SQL_CATALOG_FOOTER);
		return sb.toString().trim();
	}

	/**
	 * Gets the current hot threshold.
	 * 
	 * @return the minimum access count to be considered "hot"
	 */
	public int getHotThreshold() {
		return hotThreshold;
	}
}

