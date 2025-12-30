package org.javai.springai.actions.sql;

import java.util.Optional;
import java.util.StringJoiner;
import org.javai.springai.actions.PromptContributor;
import org.javai.springai.actions.internal.prompt.SystemPromptContext;

/**
 * Contributes SQL catalog metadata (tables/columns) to the system prompt.
 * 
 * <p>This contributor provides schema information and guidance for generating
 * standard ANSI SQL SELECT statements. The LLM should return SQL strings directly,
 * not S-expression DSL syntax.</p>
 * 
 * <p>By default it reads a provided {@link SqlCatalog} instance; if absent, it will look for
 * a {@link SqlCatalog} under the context key "sql" in {@link SystemPromptContext}.</p>
 */
public final class SqlCatalogContextContributor implements PromptContributor {

	private static final String SQL_CATALOG_FOOTER = """
			
			🔴 CRITICAL: SQL table/column names MUST be taken from this catalog exactly as shown.
			- Use the table NAME shown before the colon (e.g., "fct_orders", "dim_customer"), NOT user's informal terms
			- Use the column NAME shown after the bullet (e.g., "customer_id", "order_value"), NOT invented names
			- For JOINs, use FK relationships shown in column tags (e.g., fk:dim_customer.id means JOIN dim_customer ON ... = dim_customer.id)
			- If a name doesn't appear in this catalog, DON'T use it in SQL
			""";

	private final SqlCatalog catalog;

	public SqlCatalogContextContributor(SqlCatalog catalog) {
		this.catalog = catalog;
	}

	@Override
	public Optional<String> contribute(SystemPromptContext context) {
		SqlCatalog effectiveCatalog = catalog != null ? catalog
				: context != null
						? context.contextFor("sql")
								.or(() -> context.contextFor("sxl-sql"))  // backward compatibility
								.filter(SqlCatalog.class::isInstance)
								.map(SqlCatalog.class::cast)
								.orElse(null)
						: null;
		if (effectiveCatalog == null || effectiveCatalog.tables().isEmpty()) {
			return Optional.empty();
		}
		StringBuilder sb = new StringBuilder("SQL CATALOG:\n");
		effectiveCatalog.tables().forEach((tableName, table) -> {
			sb.append("- ").append(tableName);
			if (table.description() != null && !table.description().isBlank()) {
				sb.append(": ").append(table.description());
			}
			if (table.tags() != null && !table.tags().isEmpty()) {
				sb.append(" [tags: ").append(String.join(", ", table.tags())).append("]");
			}
			if (table.constraints() != null && !table.constraints().isEmpty()) {
				sb.append(" [constraints: ").append(String.join("; ", table.constraints())).append("]");
			}
			sb.append("\n");
			if (table.columns() != null && !table.columns().isEmpty()) {
				for (var col : table.columns()) {
					sb.append("  • ").append(col.name());
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
					if (col.constraints() != null && !col.constraints().isEmpty()) {
						details.add("constraints=" + String.join(",", col.constraints()));
					}
					if (details.length() > 0) {
						sb.append(" (").append(details).append(")");
					}
					sb.append("\n");
				}
			}
		});
		sb.append(SQL_CATALOG_FOOTER);
		return Optional.of(sb.toString().trim());
	}
}

