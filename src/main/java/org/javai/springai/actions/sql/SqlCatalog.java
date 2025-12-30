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

	record SqlTable(String name,
			String description,
			List<SqlColumn> columns,
			List<String> tags,
			List<String> constraints) {
	}

	record SqlColumn(String name,
			String description,
			String dataType,
			List<String> tags,
			List<String> constraints) {
	}
}

