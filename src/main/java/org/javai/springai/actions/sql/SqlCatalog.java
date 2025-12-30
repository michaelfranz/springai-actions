package org.javai.springai.actions.sql;

import java.util.List;
import java.util.Map;

/**
 * Contract representing a SQL catalog (tables/columns with semantics) that can be surfaced to the
 * LLM as dynamic prompt context. Inspired by dbt's schema.yml metadata.
 * 
 * <p>The catalog also specifies the target SQL dialect, allowing {@link Query#sqlString()} 
 * to automatically return dialect-appropriate SQL without requiring the caller to specify 
 * the dialect on each call.</p>
 */
public interface SqlCatalog {

	/**
	 * @return map of table name to table metadata (non-null; tables may be empty)
	 */
	Map<String, SqlTable> tables();

	/**
	 * Returns the target SQL dialect for this catalog.
	 * 
	 * <p>This dialect is used by {@link Query#sqlString()} when no explicit dialect
	 * is specified, allowing developers to configure the dialect once at catalog
	 * creation and have all queries automatically use it.</p>
	 * 
	 * @return the SQL dialect (defaults to ANSI if not overridden)
	 */
	default Query.Dialect dialect() {
		return Query.Dialect.ANSI;
	}

	/**
	 * Resolves a table name (which may be a synonym) to the canonical table name.
	 * 
	 * <p>This allows the framework to automatically map informal table names
	 * (e.g., "orders") to canonical names (e.g., "fct_orders") without requiring
	 * an LLM retry.</p>
	 * 
	 * @param tableName the table name to resolve (may be canonical or a synonym)
	 * @return the canonical table name, or empty if no match found
	 */
	default java.util.Optional<String> resolveTableName(String tableName) {
		if (tableName == null || tableName.isBlank()) {
			return java.util.Optional.empty();
		}
		// First, check for exact canonical match
		if (tables().containsKey(tableName)) {
			return java.util.Optional.of(tableName);
		}
		// Then check synonyms
		for (SqlTable table : tables().values()) {
			if (table.matchesName(tableName)) {
				return java.util.Optional.of(table.name());
			}
		}
		return java.util.Optional.empty();
	}

	record SqlTable(String name,
			String description,
			List<SqlColumn> columns,
			List<String> tags,
			List<String> constraints,
			List<String> synonyms) {

		/**
		 * Returns true if the given name matches this table's name or any of its synonyms.
		 * Comparison is case-insensitive.
		 */
		public boolean matchesName(String candidate) {
			if (candidate == null) return false;
			if (name.equalsIgnoreCase(candidate)) return true;
			return synonyms != null && synonyms.stream()
					.anyMatch(s -> s.equalsIgnoreCase(candidate));
		}
	}

	record SqlColumn(String name,
			String description,
			String dataType,
			List<String> tags,
			List<String> constraints) {
	}
}

