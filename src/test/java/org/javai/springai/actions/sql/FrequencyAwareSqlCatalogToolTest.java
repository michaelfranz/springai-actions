package org.javai.springai.actions.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("FrequencyAwareSqlCatalogTool")
class FrequencyAwareSqlCatalogToolTest {

	private InMemorySqlCatalog catalog;
	private SqlCatalogTool baseTool;
	private InMemorySchemaAccessTracker tracker;
	private FrequencyAwareSqlCatalogTool tool;

	@BeforeEach
	void setUp() {
		catalog = new InMemorySqlCatalog()
				.addTable("fct_orders", "Fact table for orders", "fact")
				.addColumn("fct_orders", "order_id", "Order PK", "string", new String[]{"pk"}, null)
				.addColumn("fct_orders", "order_value", "Order amount", "double", new String[]{"measure"}, null)
				.withSynonyms("fct_orders", "orders")
				.addTable("dim_customer", "Customer dimension", "dimension")
				.addColumn("dim_customer", "id", "Customer PK", "string", new String[]{"pk"}, null)
				.addColumn("dim_customer", "customer_name", "Customer name", "string", null, null)
				.withSynonyms("dim_customer", "customers");

		baseTool = new SqlCatalogTool(catalog);
		tracker = new InMemorySchemaAccessTracker();
		tool = new FrequencyAwareSqlCatalogTool(baseTool, tracker);
	}

	@Nested
	@DisplayName("constructor")
	class Constructor {

		@Test
		@DisplayName("rejects null delegate")
		void rejectsNullDelegate() {
			assertThatThrownBy(() -> new FrequencyAwareSqlCatalogTool(null, tracker))
					.isInstanceOf(NullPointerException.class)
					.hasMessageContaining("delegate");
		}

		@Test
		@DisplayName("rejects null tracker")
		void rejectsNullTracker() {
			assertThatThrownBy(() -> new FrequencyAwareSqlCatalogTool(baseTool, null))
					.isInstanceOf(NullPointerException.class)
					.hasMessageContaining("tracker");
		}
	}

	@Nested
	@DisplayName("listTables")
	class ListTables {

		@Test
		@DisplayName("delegates to base tool")
		void delegatesToBaseTool() {
			List<TableSummary> tables = tool.listTables();

			assertThat(tables).hasSize(2);
			assertThat(tables).extracting(TableSummary::name)
					.containsExactlyInAnyOrder("fct_orders", "dim_customer");
		}

		@Test
		@DisplayName("does not record access")
		void doesNotRecordAccess() {
			tool.listTables();

			assertThat(tracker.getAllAccessCounts()).isEmpty();
		}
	}

	@Nested
	@DisplayName("getTableDetails")
	class GetTableDetails {

		@Test
		@DisplayName("delegates to base tool")
		void delegatesToBaseTool() {
			TableDetail detail = tool.getTableDetails("fct_orders");

			assertThat(detail).isNotNull();
			assertThat(detail.name()).isEqualTo("fct_orders");
			assertThat(detail.columns()).hasSize(2);
		}

		@Test
		@DisplayName("records access for found table")
		void recordsAccessForFoundTable() {
			tool.getTableDetails("fct_orders");

			assertThat(tracker.getAccessCount("fct_orders")).isEqualTo(1);
		}

		@Test
		@DisplayName("records multiple accesses")
		void recordsMultipleAccesses() {
			tool.getTableDetails("fct_orders");
			tool.getTableDetails("fct_orders");
			tool.getTableDetails("dim_customer");

			assertThat(tracker.getAccessCount("fct_orders")).isEqualTo(2);
			assertThat(tracker.getAccessCount("dim_customer")).isEqualTo(1);
		}

		@Test
		@DisplayName("does not record access for unknown table")
		void doesNotRecordAccessForUnknownTable() {
			TableDetail detail = tool.getTableDetails("nonexistent");

			assertThat(detail).isNull();
			assertThat(tracker.getAllAccessCounts()).isEmpty();
		}

		@Test
		@DisplayName("records access using lookup name")
		void recordsAccessUsingLookupName() {
			// When looking up by synonym, we record the synonym used
			tool.getTableDetails("orders");

			assertThat(tracker.getAccessCount("orders")).isEqualTo(1);
		}
	}

	@Nested
	@DisplayName("accessors")
	class Accessors {

		@Test
		@DisplayName("getTracker returns the tracker")
		void getTrackerReturnsTracker() {
			assertThat(tool.getTracker()).isSameAs(tracker);
		}

		@Test
		@DisplayName("getDelegate returns the delegate")
		void getDelegateReturnsDelegate() {
			assertThat(tool.getDelegate()).isSameAs(baseTool);
		}
	}

	@Nested
	@DisplayName("integration")
	class Integration {

		@Test
		@DisplayName("hot tables can be determined from tracked access")
		void hotTablesFromTrackedAccess() {
			// Simulate usage pattern: fct_orders accessed frequently
			tool.getTableDetails("fct_orders");
			tool.getTableDetails("fct_orders");
			tool.getTableDetails("fct_orders");
			tool.getTableDetails("dim_customer");

			// fct_orders is "hot" at threshold 3
			assertThat(tracker.getHotTables(3)).containsExactly("fct_orders");
			
			// Both are "hot" at threshold 1
			assertThat(tracker.getHotTables(1))
					.containsExactlyInAnyOrder("fct_orders", "dim_customer");
		}
	}
}

