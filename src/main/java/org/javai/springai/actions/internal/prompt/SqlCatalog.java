package org.javai.springai.actions.internal.prompt;

import java.util.List;
import java.util.Map;

/**
 * Contract representing a SQL catalog (tables/columns with semantics) that can be surfaced to the
 * LLM as dynamic prompt context. Inspired by dbt's schema.yml metadata.
 */
public interface SqlCatalog {

	/**
	 * @return map of table name to table metadata (non-null; tables may be empty)
	 */
	Map<String, SqlTable> tables();

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

