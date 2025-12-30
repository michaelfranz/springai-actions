package org.javai.springai.actions.sql;

import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import org.javai.springai.actions.PromptContributor;
import org.javai.springai.actions.internal.prompt.SystemPromptContext;

/**
 * Contributes SQL catalog metadata (tables/columns) to the system prompt.
 * 
 * <p>This contributor provides schema information and guidance for generating
 * standard ANSI SQL SELECT statements.</p>
 * 
 * <p>By default it reads a provided {@link SqlCatalog} instance; if absent, it will look for
 * a {@link SqlCatalog} under the context key "sql" in {@link SystemPromptContext}.</p>
 * 
 * <h2>Tokenization (Internal)</h2>
 * 
 * <p>When the catalog has {@link SqlCatalog#isTokenized()} enabled, this contributor
 * emits alternative names (synonyms or cryptic identifiers) instead of real database 
 * object names. The LLM sees these as normal table/column names and generates SQL
 * using them. The framework automatically translates back to real names before
 * validation and execution.</p>
 * 
 * <p>The LLM is unaware of this translation - it simply uses the names shown in the catalog.</p>
 */
public final class SqlCatalogContextContributor implements PromptContributor {

	private static final String SQL_CATALOG_FOOTER = """
			
			🔴 CRITICAL: SQL table/column names MUST be taken from this catalog exactly as shown.
			- Use the table NAME shown before the colon, NOT user's informal terms
			- Use the column NAME shown after the bullet, NOT invented names
			- For JOINs, use FK relationships shown in column tags (e.g., fk:customers.id means JOIN customers ON ... = customers.id)
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

		if (effectiveCatalog.isTokenized()) {
			return Optional.of(contributeTokenized(effectiveCatalog));
		} else {
			return Optional.of(contributeStandard(effectiveCatalog));
		}
	}

	/**
	 * Contributes standard (non-tokenized) catalog format.
	 */
	private String contributeStandard(SqlCatalog catalog) {
		StringBuilder sb = new StringBuilder("SQL CATALOG:\n");
		catalog.tables().forEach((tableName, table) -> {
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
			// Add table synonyms
			if (table.synonyms() != null && !table.synonyms().isEmpty()) {
				sb.append(" (aka: ").append(String.join(", ", table.synonyms())).append(")");
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
					// Add column synonyms
					if (col.synonyms() != null && !col.synonyms().isEmpty()) {
						details.add("aka=" + String.join(",", col.synonyms()));
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
	 * Contributes catalog with alternative names (hiding real database object names).
	 * 
	 * <p>The LLM sees these as normal table/column names. Strategy:</p>
	 * <ul>
	 *   <li>If synonyms exist, the first synonym is used as the name</li>
	 *   <li>If no synonyms, a cryptic identifier is used</li>
	 *   <li>Remaining synonyms are listed as "also: ..." to help LLM understand alternatives</li>
	 * </ul>
	 */
	private String contributeTokenized(SqlCatalog catalog) {
		StringBuilder sb = new StringBuilder("SQL CATALOG:\n");
		catalog.tables().forEach((tableName, table) -> {
			String tableToken = catalog.getTableToken(tableName).orElse(tableName);
			sb.append("- ").append(tableToken);
			if (table.description() != null && !table.description().isBlank()) {
				sb.append(": ").append(table.description());
			}
			if (table.tags() != null && !table.tags().isEmpty()) {
				sb.append(" [tags: ").append(String.join(", ", table.tags())).append("]");
			}
			// Show remaining synonyms (first one is the token)
			List<String> remainingSynonyms = getRemainingTableSynonyms(table, tableToken);
			if (!remainingSynonyms.isEmpty()) {
				sb.append(" (also: ").append(String.join(", ", remainingSynonyms)).append(")");
			}
			sb.append("\n");
			if (table.columns() != null && !table.columns().isEmpty()) {
				for (var col : table.columns()) {
					String columnToken = catalog.getColumnToken(tableName, col.name()).orElse(col.name());
					sb.append("  • ").append(columnToken);
					StringJoiner details = new StringJoiner("; ");
					if (col.dataType() != null && !col.dataType().isBlank()) {
						details.add("type=" + col.dataType());
					}
					if (col.description() != null && !col.description().isBlank()) {
						details.add(col.description());
					}
					// Tokenize FK references in tags
					if (col.tags() != null && !col.tags().isEmpty()) {
						String tokenizedTags = tokenizeFkTags(col.tags(), catalog);
						details.add("tags=" + tokenizedTags);
					}
					// Show remaining column synonyms (first one is the token)
					List<String> remainingColSynonyms = getRemainingColumnSynonyms(col, columnToken);
					if (!remainingColSynonyms.isEmpty()) {
						details.add("also=" + String.join(",", remainingColSynonyms));
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
	 * Gets table synonyms excluding the first one if it's used as the token.
	 */
	private List<String> getRemainingTableSynonyms(SqlCatalog.SqlTable table, String tableToken) {
		if (table.synonyms() == null || table.synonyms().isEmpty()) {
			return List.of();
		}
		List<String> synonyms = table.synonyms();
		// If first synonym is the token, return the rest
		if (!synonyms.isEmpty() && synonyms.get(0).equals(tableToken)) {
			return synonyms.size() > 1 ? synonyms.subList(1, synonyms.size()) : List.of();
		}
		// Token is cryptic, show all synonyms
		return synonyms;
	}

	/**
	 * Gets column synonyms excluding the first one if it's used as the token.
	 */
	private List<String> getRemainingColumnSynonyms(SqlCatalog.SqlColumn col, String columnToken) {
		if (col.synonyms() == null || col.synonyms().isEmpty()) {
			return List.of();
		}
		List<String> synonyms = col.synonyms();
		// If first synonym is the token, return the rest
		if (!synonyms.isEmpty() && synonyms.get(0).equals(columnToken)) {
			return synonyms.size() > 1 ? synonyms.subList(1, synonyms.size()) : List.of();
		}
		// Token is cryptic, show all synonyms
		return synonyms;
	}

	/**
	 * Tokenizes FK references in column tags.
	 * <p>Converts tags like "fk:dim_customer.id" to "fk:dt_abc123.c_def456"</p>
	 */
	private String tokenizeFkTags(java.util.List<String> tags, SqlCatalog catalog) {
		return tags.stream()
				.map(tag -> tokenizeFkTag(tag, catalog))
				.collect(java.util.stream.Collectors.joining(","));
	}

	/**
	 * Tokenizes a single FK tag if it matches the fk:table.column pattern.
	 */
	private String tokenizeFkTag(String tag, SqlCatalog catalog) {
		if (tag == null || !tag.startsWith("fk:")) {
			return tag;
		}
		String reference = tag.substring(3); // Remove "fk:" prefix
		int dotIndex = reference.indexOf('.');
		if (dotIndex < 0) {
			// Just table reference, no column
			String tableToken = catalog.getTableToken(reference).orElse(reference);
			return "fk:" + tableToken;
		}
		String tableName = reference.substring(0, dotIndex);
		String columnName = reference.substring(dotIndex + 1);
		String tableToken = catalog.getTableToken(tableName).orElse(tableName);
		String columnToken = catalog.getColumnToken(tableName, columnName).orElse(columnName);
		return "fk:" + tableToken + "." + columnToken;
	}
}

