package org.javai.springai.actions.sql;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.javai.springai.actions.conversation.WorkingContext;
import org.javai.springai.actions.internal.prompt.SystemPromptContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for SQL working context types.
 * 
 * <p>The SqlWorkingContextExtractor tests are integration-level and
 * are covered in DataWarehouseMultiTurnScenarioTest.</p>
 */
@DisplayName("SQL Working Context")
class SqlWorkingContextTest {

	private InMemorySqlCatalog catalog;

	@BeforeEach
	void setUp() {
		catalog = new InMemorySqlCatalog()
				.addTable("fct_orders", "Order transactions", "fact")
				.withSynonyms("fct_orders", "orders")
				.addColumn("fct_orders", "order_value", "Order amount", "double", null, null)
				.addColumn("fct_orders", "customer_id", "FK to customer", "string", null, null)
				.addTable("dim_customer", "Customers", "dimension")
				.withSynonyms("dim_customer", "customers")
				.addColumn("dim_customer", "id", "PK", "string", null, null)
				.addColumn("dim_customer", "customer_name", "Name", "string", null, null);
	}

	@Nested
	@DisplayName("SqlQueryPayload")
	class PayloadTests {

		@Test
		@DisplayName("creates payload from model SQL")
		void createsFromModelSql() {
			SqlQueryPayload payload = SqlQueryPayload.fromModelSql(
					"SELECT order_value FROM orders");

			assertThat(payload.modelSql()).isEqualTo("SELECT order_value FROM orders");
			assertThat(payload.tables()).isEmpty();
			assertThat(payload.selectedColumns()).isEmpty();
			assertThat(payload.whereClause()).isNull();
		}

		@Test
		@DisplayName("creates payload from Query with metadata")
		void createsFromQuery() {
			Query query = Query.fromSql(
					"SELECT order_value, customer_name FROM fct_orders o " +
					"JOIN dim_customer c ON o.customer_id = c.id " +
					"WHERE c.customer_name = 'Smith'",
					catalog);

			SqlQueryPayload payload = SqlQueryPayload.fromQuery(query);

			assertThat(payload.modelSql()).isNotBlank();
			assertThat(payload.tables()).containsExactlyInAnyOrder("fct_orders", "dim_customer");
			assertThat(payload.selectedColumns()).containsExactlyInAnyOrder("order_value", "customer_name");
			assertThat(payload.whereClause()).contains("customer_name");
		}

		@Test
		@DisplayName("creates payload with model SQL and Query metadata")
		void createsWithModelSqlAndQuery() {
			String modelSql = "SELECT order_value FROM orders";
			Query query = Query.fromSql("SELECT order_value FROM fct_orders", catalog);

			SqlQueryPayload payload = SqlQueryPayload.fromModelSqlAndQuery(modelSql, query);

			assertThat(payload.modelSql()).isEqualTo(modelSql);
			assertThat(payload.tables()).contains("fct_orders");
		}

		@Test
		@DisplayName("context type is sql.query")
		void contextTypeIsSqlQuery() {
			assertThat(SqlQueryPayload.CONTEXT_TYPE).isEqualTo("sql.query");
		}
	}

	@Nested
	@DisplayName("Query Metadata Helpers")
	class QueryMetadataTests {

		@Test
		@DisplayName("referencedTables returns all tables")
		void referencedTablesReturnsAllTables() {
			Query query = Query.fromSql(
					"SELECT o.order_value FROM fct_orders o JOIN dim_customer c ON o.customer_id = c.id",
					catalog);

			List<String> tables = query.referencedTables();

			assertThat(tables).containsExactlyInAnyOrder("fct_orders", "dim_customer");
		}

		@Test
		@DisplayName("selectedColumns returns SELECT columns")
		void selectedColumnsReturnsSelectColumns() {
			Query query = Query.fromSql(
					"SELECT order_value, customer_name FROM fct_orders o " +
					"JOIN dim_customer c ON o.customer_id = c.id",
					catalog);

			List<String> columns = query.selectedColumns();

			assertThat(columns).containsExactly("order_value", "customer_name");
		}

		@Test
		@DisplayName("whereClause returns filter condition")
		void whereClauseReturnsFilter() {
			Query query = Query.fromSql(
					"SELECT order_value FROM fct_orders WHERE order_value > 100",
					catalog);

			Optional<String> where = query.whereClause();

			assertThat(where).isPresent();
			assertThat(where.get()).contains("order_value > 100");
		}

		@Test
		@DisplayName("whereClause returns empty for no filter")
		void whereClauseEmptyForNoFilter() {
			Query query = Query.fromSql("SELECT order_value FROM fct_orders", catalog);

			assertThat(query.whereClause()).isEmpty();
		}

		@Test
		@DisplayName("groupByColumns returns GROUP BY columns")
		void groupByColumnsReturnsGroupByColumns() {
			Query query = Query.fromSql(
					"SELECT customer_id, SUM(order_value) FROM fct_orders GROUP BY customer_id",
					catalog);

			List<String> groupBy = query.groupByColumns();

			assertThat(groupBy).containsExactly("customer_id");
		}

		@Test
		@DisplayName("groupByColumns returns empty for no grouping")
		void groupByColumnsEmptyForNoGrouping() {
			Query query = Query.fromSql("SELECT order_value FROM fct_orders", catalog);

			assertThat(query.groupByColumns()).isEmpty();
		}
	}

	@Nested
	@DisplayName("SqlWorkingContextContributor")
	class ContributorTests {

		@Test
		@DisplayName("contributes nothing when no working context")
		void contributesNothingWhenNoContext() {
			SqlWorkingContextContributor contributor = new SqlWorkingContextContributor();
			SystemPromptContext promptContext = new SystemPromptContext(null, null, null, Map.of());

			Optional<String> contribution = contributor.contribute(promptContext);

			assertThat(contribution).isEmpty();
		}

		@Test
		@DisplayName("contributes SQL context when payload present")
		void contributesSqlContext() {
			SqlWorkingContextContributor contributor = new SqlWorkingContextContributor();
			
			SqlQueryPayload payload = new SqlQueryPayload(
					"SELECT order_value FROM orders",
					List.of("orders"),
					List.of("order_value"),
					null);
			
			WorkingContext<SqlQueryPayload> wc = WorkingContext.of(SqlQueryPayload.CONTEXT_TYPE, payload);
			SystemPromptContext promptContext = new SystemPromptContext(
					null, null, null, Map.of("workingContext", wc));

			Optional<String> contribution = contributor.contribute(promptContext);

			assertThat(contribution).isPresent();
			assertThat(contribution.get()).contains("SELECT order_value FROM orders");
			assertThat(contribution.get()).contains("- Tables: orders");
			assertThat(contribution.get()).contains("- Selected columns: order_value");
		}

		@Test
		@DisplayName("contributes filter when present")
		void contributesFilterWhenPresent() {
			SqlWorkingContextContributor contributor = new SqlWorkingContextContributor();
			
			SqlQueryPayload payload = new SqlQueryPayload(
					"SELECT order_value FROM orders WHERE region = 'West'",
					List.of("orders"),
					List.of("order_value"),
					"region = 'West'");
			
			WorkingContext<SqlQueryPayload> wc = WorkingContext.of(SqlQueryPayload.CONTEXT_TYPE, payload);
			SystemPromptContext promptContext = new SystemPromptContext(
					null, null, null, Map.of("workingContext", wc));

			Optional<String> contribution = contributor.contribute(promptContext);

			assertThat(contribution).isPresent();
			assertThat(contribution.get()).contains("- Current filter: region = 'West'");
		}

		@Test
		@DisplayName("ignores non-SQL working context")
		void ignoresNonSqlContext() {
			SqlWorkingContextContributor contributor = new SqlWorkingContextContributor();
			
			WorkingContext<String> wc = WorkingContext.of("shopping.basket", "basket-content");
			SystemPromptContext promptContext = new SystemPromptContext(
					null, null, null, Map.of("workingContext", wc));

			Optional<String> contribution = contributor.contribute(promptContext);

			assertThat(contribution).isEmpty();
		}
	}

	@Nested
	@DisplayName("SqlWorkingContextExtractor")
	class ExtractorTests {

		@Test
		@DisplayName("context type is sql.query")
		void contextTypeIsSqlQuery() {
			SqlWorkingContextExtractor extractor = new SqlWorkingContextExtractor();
			assertThat(extractor.contextType()).isEqualTo(SqlQueryPayload.CONTEXT_TYPE);
		}
	}
}

