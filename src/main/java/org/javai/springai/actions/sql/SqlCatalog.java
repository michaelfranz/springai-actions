package org.javai.springai.actions.sql;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
	 * Returns whether column-level validation is enabled for this catalog.
	 * 
	 * <p>When enabled, {@link Query#fromSql(String, SqlCatalog)} will validate that
	 * all column references in the SQL exist in the catalog. This is a stricter check
	 * that may reject valid queries with SQL expressions or computed columns.</p>
	 * 
	 * <p>Default is {@code false} for backward compatibility with LLM-generated SQL
	 * that may have minor column reference errors.</p>
	 * 
	 * @return true if column validation should be performed
	 */
	default boolean validateColumns() {
		return false;
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
	default Optional<String> resolveTableName(String tableName) {
		if (tableName == null || tableName.isBlank()) {
			return Optional.empty();
		}
		// First, check for exact canonical match
		if (tables().containsKey(tableName)) {
			return Optional.of(tableName);
		}
		// Then check synonyms
		for (SqlTable table : tables().values()) {
			if (table.matchesName(tableName)) {
				return Optional.of(table.name());
			}
		}
		return Optional.empty();
	}

	// ==================== MODEL NAME MAPPING ====================
	// 
	// When model name mapping is enabled, the catalog provides alternative names
	// (derived from synonyms or generated) for tables and columns. The LLM sees
	// and generates SQL using these "model names" rather than real schema names.
	// The framework automatically resolves model names to canonical names.

	/**
	 * Returns whether model name mapping is enabled for this catalog.
	 * 
	 * <p>When enabled, the catalog provides model-facing names (derived from 
	 * synonyms or generated) for table and column names. The LLM sees and 
	 * generates SQL using these model names. The framework automatically 
	 * resolves model names back to canonical names during query processing.</p>
	 * 
	 * @return true if model name mapping is enabled
	 */
	default boolean usesModelNames() {
		return false;
	}

	/**
	 * Gets the model-facing name for a table.
	 * 
	 * <p>The model name is derived from the first synonym if available,
	 * otherwise a generated identifier is used.</p>
	 * 
	 * @param tableName the canonical table name
	 * @return the model name, or empty if mapping is disabled or table not found
	 */
	default Optional<String> getTableModelName(String tableName) {
		return Optional.empty();
	}

	/**
	 * Gets the model-facing name for a column.
	 * 
	 * <p>The model name is derived from the first synonym if available,
	 * otherwise a generated identifier is used.</p>
	 * 
	 * @param tableName the canonical table name containing the column
	 * @param columnName the canonical column name
	 * @return the model name, or empty if mapping is disabled or column not found
	 */
	default Optional<String> getColumnModelName(String tableName, String columnName) {
		return Optional.empty();
	}

	/**
	 * Resolves a model table name back to the canonical table name.
	 * 
	 * @param modelName the model-facing table name
	 * @return the canonical table name, or empty if not found
	 */
	default Optional<String> resolveTableFromModelName(String modelName) {
		return Optional.empty();
	}

	/**
	 * Resolves a model column name back to the canonical column name.
	 * 
	 * @param tableModelName the model-facing table name (for context)
	 * @param columnModelName the model-facing column name
	 * @return the canonical column name, or empty if not found
	 */
	default Optional<String> resolveColumnFromModelName(String tableModelName, String columnModelName) {
		return Optional.empty();
	}

	/**
	 * Returns all model name mappings for debugging purposes.
	 * 
	 * @return map of model name to canonical name for all mapped objects
	 */
	default Map<String, String> modelNameMappings() {
		return Map.of();
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

		/**
		 * Derives the table type from tags (fact, dimension, bridge, or table).
		 * 
		 * @return the table type string
		 */
		public String tableType() {
			if (tags == null || tags.isEmpty()) {
				return "table";
			}
			if (tags.contains("fact")) return "fact";
			if (tags.contains("dimension")) return "dimension";
			if (tags.contains("bridge")) return "bridge";
			return "table";
		}

		/**
		 * Returns synonyms as a non-null list.
		 * 
		 * @return synonyms, or empty list if null
		 */
		public List<String> synonymsOrEmpty() {
			return synonyms != null ? synonyms : List.of();
		}

		/**
		 * Returns the number of columns.
		 * 
		 * @return column count
		 */
		public int columnCount() {
			return columns != null ? columns.size() : 0;
		}

		/**
		 * Resolves a column name (which may be a synonym) to the canonical column name.
		 * 
		 * @param columnName the column name to resolve (may be canonical or a synonym)
		 * @return the canonical column name, or empty if no match found
		 */
		public Optional<String> resolveColumnName(String columnName) {
			if (columnName == null || columnName.isBlank() || columns == null) {
				return Optional.empty();
			}
			for (SqlColumn column : columns) {
				if (column.matchesName(columnName)) {
					return Optional.of(column.name());
				}
			}
			return Optional.empty();
		}

		/**
		 * Returns the column matching the given name or synonym, if found.
		 */
		public Optional<SqlColumn> findColumn(String columnName) {
			if (columnName == null || columnName.isBlank() || columns == null) {
				return Optional.empty();
			}
			return columns.stream()
					.filter(c -> c.matchesName(columnName))
					.findFirst();
		}
	}

	record SqlColumn(String name,
			String description,
			String dataType,
			List<String> tags,
			List<String> constraints,
			List<String> synonyms) {

		/**
		 * Returns true if the given name matches this column's name or any of its synonyms.
		 * Comparison is case-insensitive.
		 */
		public boolean matchesName(String candidate) {
			if (candidate == null) return false;
			if (name.equalsIgnoreCase(candidate)) return true;
			return synonyms != null && synonyms.stream()
					.anyMatch(s -> s.equalsIgnoreCase(candidate));
		}
	}
}

