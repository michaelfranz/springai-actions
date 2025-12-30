package org.javai.springai.actions.sql;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SqlCatalogTool")
class SqlCatalogToolTest {

	private InMemorySqlCatalog catalog;
	private SqlCatalogTool tool;

	@BeforeEach
	void setUp() {
		catalog = new InMemorySqlCatalog()
				.addTable("fct_orders", "Fact table for orders", "fact")
				.addColumn("fct_orders", "order_id", "Order PK", "string", new String[]{"pk"}, null)
				.addColumn("fct_orders", "customer_id", "FK to customer", "string", 
						new String[]{"fk:dim_customer.id"}, null)
				.addColumn("fct_orders", "date_id", "FK to date", "string", 
						new String[]{"fk:dim_date.id"}, null)
				.addColumn("fct_orders", "order_value", "Order amount", "double", 
						new String[]{"measure"}, null)
				.withSynonyms("fct_orders", "orders", "sales")
				.withColumnSynonyms("fct_orders", "order_value", "value", "amount")
				.addTable("dim_customer", "Customer dimension", "dimension")
				.addColumn("dim_customer", "id", "Customer PK", "string", new String[]{"pk"}, null)
				.addColumn("dim_customer", "customer_name", "Customer name", "string", 
						new String[]{"attribute"}, null)
				.withSynonyms("dim_customer", "customers", "cust")
				.addTable("dim_date", "Date dimension", "dimension")
				.addColumn("dim_date", "id", "Date PK", "string", new String[]{"pk"}, null)
				.addColumn("dim_date", "date", "Calendar date", "date", new String[]{"attribute"}, null)
				.withSynonyms("dim_date", "dates");

		tool = new SqlCatalogTool(catalog);
	}

	@Nested
	@DisplayName("listTables")
	class ListTables {

		@Test
		@DisplayName("returns all tables with summaries")
		void returnsAllTables() {
			List<TableSummary> tables = tool.listTables();

			assertThat(tables).hasSize(3);
			assertThat(tables).extracting(TableSummary::name)
					.containsExactlyInAnyOrder("fct_orders", "dim_customer", "dim_date");
		}

		@Test
		@DisplayName("includes table descriptions")
		void includesDescriptions() {
			List<TableSummary> tables = tool.listTables();

			TableSummary orders = tables.stream()
					.filter(t -> t.name().equals("fct_orders"))
					.findFirst().orElseThrow();
			assertThat(orders.description()).isEqualTo("Fact table for orders");
		}

		@Test
		@DisplayName("includes table types")
		void includesTypes() {
			List<TableSummary> tables = tool.listTables();

			assertThat(tables.stream().filter(t -> t.name().equals("fct_orders")).findFirst().orElseThrow().type())
					.isEqualTo("fact");
			assertThat(tables.stream().filter(t -> t.name().equals("dim_customer")).findFirst().orElseThrow().type())
					.isEqualTo("dimension");
		}

		@Test
		@DisplayName("includes column counts")
		void includesColumnCounts() {
			List<TableSummary> tables = tool.listTables();

			TableSummary orders = tables.stream()
					.filter(t -> t.name().equals("fct_orders"))
					.findFirst().orElseThrow();
			assertThat(orders.columnCount()).isEqualTo(4);
		}

		@Test
		@DisplayName("includes synonyms")
		void includesSynonyms() {
			List<TableSummary> tables = tool.listTables();

			TableSummary orders = tables.stream()
					.filter(t -> t.name().equals("fct_orders"))
					.findFirst().orElseThrow();
			assertThat(orders.synonyms()).containsExactly("orders", "sales");
		}

		@Test
		@DisplayName("tracks invocation count")
		void tracksInvocations() {
			assertThat(tool.listTablesInvokedCount()).isZero();
			tool.listTables();
			assertThat(tool.listTablesInvokedCount()).isEqualTo(1);
			tool.listTables();
			assertThat(tool.listTablesInvokedCount()).isEqualTo(2);
		}
	}

	@Nested
	@DisplayName("getTableDetails")
	class GetTableDetails {

		@Test
		@DisplayName("returns table with all columns")
		void returnsTableWithColumns() {
			TableDetail detail = tool.getTableDetails("fct_orders");

			assertThat(detail).isNotNull();
			assertThat(detail.name()).isEqualTo("fct_orders");
			assertThat(detail.columns()).hasSize(4);
		}

		@Test
		@DisplayName("includes column metadata")
		void includesColumnMetadata() {
			TableDetail detail = tool.getTableDetails("fct_orders");

			ColumnDetail orderValue = detail.columns().stream()
					.filter(c -> c.name().equals("order_value"))
					.findFirst().orElseThrow();
			
			assertThat(orderValue.description()).isEqualTo("Order amount");
			assertThat(orderValue.dataType()).isEqualTo("double");
			assertThat(orderValue.tags()).contains("measure");
			assertThat(orderValue.synonyms()).containsExactly("value", "amount");
		}

		@Test
		@DisplayName("returns null for unknown table")
		void returnsNullForUnknown() {
			TableDetail detail = tool.getTableDetails("nonexistent");
			assertThat(detail).isNull();
		}

		@Test
		@DisplayName("finds table by synonym")
		void findsBySynonym() {
			TableDetail detail = tool.getTableDetails("orders");
			
			assertThat(detail).isNotNull();
			assertThat(detail.name()).isEqualTo("fct_orders");
		}

		@Test
		@DisplayName("tracks invocations and last requested table")
		void tracksInvocations() {
			assertThat(tool.getTableDetailsInvokedCount()).isZero();
			tool.getTableDetails("fct_orders");
			assertThat(tool.getTableDetailsInvokedCount()).isEqualTo(1);
			assertThat(tool.lastTableRequested()).isEqualTo("fct_orders");
			
			tool.getTableDetails("dim_customer");
			assertThat(tool.getTableDetailsInvokedCount()).isEqualTo(2);
			assertThat(tool.lastTableRequested()).isEqualTo("dim_customer");
		}
	}

	@Nested
	@DisplayName("getTableRelationships")
	class GetTableRelationships {

		@Test
		@DisplayName("returns FK relationships")
		void returnsFkRelationships() {
			List<TableRelationship> relationships = tool.getTableRelationships("fct_orders");

			assertThat(relationships).hasSize(2);  // customer_id and date_id FKs
		}

		@Test
		@DisplayName("includes correct relationship details")
		void includesRelationshipDetails() {
			List<TableRelationship> relationships = tool.getTableRelationships("fct_orders");

			TableRelationship customerFk = relationships.stream()
					.filter(r -> r.toTable().equals("dim_customer"))
					.findFirst().orElseThrow();
			
			assertThat(customerFk.fromTable()).isEqualTo("fct_orders");
			assertThat(customerFk.fromColumn()).isEqualTo("customer_id");
			assertThat(customerFk.toColumn()).isEqualTo("id");
			assertThat(customerFk.description()).contains("Customer dimension");
		}

		@Test
		@DisplayName("provides join hint")
		void providesJoinHint() {
			List<TableRelationship> relationships = tool.getTableRelationships("fct_orders");

			TableRelationship customerFk = relationships.stream()
					.filter(r -> r.toTable().equals("dim_customer"))
					.findFirst().orElseThrow();
			
			assertThat(customerFk.joinHint())
					.isEqualTo("JOIN dim_customer ON fct_orders.customer_id = dim_customer.id");
		}

		@Test
		@DisplayName("returns empty list for table without FKs")
		void returnsEmptyForNoFks() {
			List<TableRelationship> relationships = tool.getTableRelationships("dim_customer");
			assertThat(relationships).isEmpty();
		}

		@Test
		@DisplayName("returns empty list for unknown table")
		void returnsEmptyForUnknown() {
			List<TableRelationship> relationships = tool.getTableRelationships("nonexistent");
			assertThat(relationships).isEmpty();
		}

		@Test
		@DisplayName("finds table by synonym")
		void findsBySynonym() {
			List<TableRelationship> relationships = tool.getTableRelationships("orders");
			assertThat(relationships).hasSize(2);
		}

		@Test
		@DisplayName("tracks invocation count")
		void tracksInvocations() {
			assertThat(tool.getRelationshipsInvokedCount()).isZero();
			tool.getTableRelationships("fct_orders");
			assertThat(tool.getRelationshipsInvokedCount()).isEqualTo(1);
		}
	}

	@Nested
	@DisplayName("with tokenization")
	class WithTokenization {

		@BeforeEach
		void enableTokenization() {
			catalog = new InMemorySqlCatalog()
					.withTokenization(true)
					.addTable("fct_orders", "Fact table for orders", "fact")
					.withSynonyms("fct_orders", "orders", "sales")  // "orders" becomes token
					.addColumn("fct_orders", "customer_id", "FK to customer", "string", 
							new String[]{"fk:dim_customer.id"}, null)
					.addColumn("fct_orders", "order_value", "Order amount", "double", 
							new String[]{"measure"}, null)
					.withColumnSynonyms("fct_orders", "order_value", "value")  // "value" becomes token
					.addTable("dim_customer", "Customer dimension", "dimension")
					.withSynonyms("dim_customer", "customers")  // "customers" becomes token
					.addColumn("dim_customer", "id", "Customer PK", "string", new String[]{"pk"}, null)
					.addColumn("dim_customer", "customer_name", "Customer name", "string", 
							new String[]{"attribute"}, null);

			tool = new SqlCatalogTool(catalog);
		}

		@Test
		@DisplayName("listTables returns tokenized names")
		void listTablesReturnsTokens() {
			List<TableSummary> tables = tool.listTables();

			// With synonyms, first synonym becomes the token
			assertThat(tables).extracting(TableSummary::name)
					.containsExactlyInAnyOrder("orders", "customers");
		}

		@Test
		@DisplayName("getTableDetails works with token")
		void getTableDetailsWithToken() {
			TableDetail detail = tool.getTableDetails("orders");

			assertThat(detail).isNotNull();
			assertThat(detail.name()).isEqualTo("orders");
		}

		@Test
		@DisplayName("getTableDetails returns tokenized column names")
		void getTableDetailsReturnsTokenizedColumns() {
			TableDetail detail = tool.getTableDetails("orders");

			// order_value has synonym "value" which becomes its token
			ColumnDetail valueCol = detail.columns().stream()
					.filter(c -> c.name().equals("value"))
					.findFirst().orElseThrow();
			assertThat(valueCol.description()).isEqualTo("Order amount");
			
			// customer_id has no synonym, so it gets a cryptic token
			ColumnDetail customerIdCol = detail.columns().stream()
					.filter(c -> c.name().startsWith("c_"))
					.findFirst().orElseThrow();
			assertThat(customerIdCol.description()).isEqualTo("FK to customer");
		}

		@Test
		@DisplayName("getTableRelationships returns tokenized names")
		void getTableRelationshipsReturnsTokens() {
			List<TableRelationship> relationships = tool.getTableRelationships("orders");

			assertThat(relationships).hasSize(1);
			TableRelationship rel = relationships.get(0);
			
			assertThat(rel.fromTable()).isEqualTo("orders");
			assertThat(rel.toTable()).isEqualTo("customers");
		}
	}

	@Test
	@DisplayName("resetCounters clears all tracking")
	void resetCountersClearsAll() {
		tool.listTables();
		tool.getTableDetails("fct_orders");
		tool.getTableRelationships("fct_orders");

		tool.resetCounters();

		assertThat(tool.listTablesInvokedCount()).isZero();
		assertThat(tool.getTableDetailsInvokedCount()).isZero();
		assertThat(tool.getRelationshipsInvokedCount()).isZero();
		assertThat(tool.lastTableRequested()).isNull();
	}
}

