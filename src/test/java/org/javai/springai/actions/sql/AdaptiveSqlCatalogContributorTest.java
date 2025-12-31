package org.javai.springai.actions.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AdaptiveSqlCatalogContributor")
class AdaptiveSqlCatalogContributorTest {

	private InMemorySqlCatalog catalog;
	private InMemorySchemaAccessTracker tracker;
	private AdaptiveSqlCatalogContributor contributor;

	@BeforeEach
	void setUp() {
		catalog = new InMemorySqlCatalog()
				.addTable("fct_orders", "Fact table for orders", "fact")
				.addColumn("fct_orders", "order_id", "Order PK", "string", new String[]{"pk"}, null)
				.addColumn("fct_orders", "customer_id", "FK to customer", "string", 
						new String[]{"fk:dim_customer.id"}, null)
				.addColumn("fct_orders", "order_value", "Order amount", "double", 
						new String[]{"measure"}, null)
				.addTable("dim_customer", "Customer dimension", "dimension")
				.addColumn("dim_customer", "id", "Customer PK", "string", new String[]{"pk"}, null)
				.addColumn("dim_customer", "customer_name", "Customer name", "string", 
						new String[]{"attribute"}, null)
				.addTable("dim_date", "Date dimension", "dimension")
				.addColumn("dim_date", "id", "Date PK", "string", new String[]{"pk"}, null);

		tracker = new InMemorySchemaAccessTracker();
		contributor = new AdaptiveSqlCatalogContributor(catalog, tracker, 2);
	}

	@Nested
	@DisplayName("constructor")
	class Constructor {

		@Test
		@DisplayName("rejects null catalog")
		void rejectsNullCatalog() {
			assertThatThrownBy(() -> new AdaptiveSqlCatalogContributor(null, tracker, 2))
					.isInstanceOf(NullPointerException.class)
					.hasMessageContaining("catalog");
		}

		@Test
		@DisplayName("rejects null tracker")
		void rejectsNullTracker() {
			assertThatThrownBy(() -> new AdaptiveSqlCatalogContributor(catalog, null, 2))
					.isInstanceOf(NullPointerException.class)
					.hasMessageContaining("tracker");
		}

		@Test
		@DisplayName("rejects zero threshold")
		void rejectsZeroThreshold() {
			assertThatThrownBy(() -> new AdaptiveSqlCatalogContributor(catalog, tracker, 0))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("hotThreshold");
		}

		@Test
		@DisplayName("rejects negative threshold")
		void rejectsNegativeThreshold() {
			assertThatThrownBy(() -> new AdaptiveSqlCatalogContributor(catalog, tracker, -1))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("hotThreshold");
		}
	}

	@Nested
	@DisplayName("contribute - cold start")
	class ContributeColdStart {

		@Test
		@DisplayName("returns no-tables message when tracker is empty")
		void returnsNoTablesMessageWhenEmpty() {
			String contribution = contributor.contribute(null).orElse("");

			assertThat(contribution).contains("No frequently-used tables yet");
			assertThat(contribution).contains("listTables");
			assertThat(contribution).contains("getTableDetails");
		}

		@Test
		@DisplayName("returns no-tables message when below threshold")
		void returnsNoTablesMessageWhenBelowThreshold() {
			tracker.recordTableAccess("fct_orders");  // Only 1 access, threshold is 2

			String contribution = contributor.contribute(null).orElse("");

			assertThat(contribution).contains("No frequently-used tables yet");
		}
	}

	@Nested
	@DisplayName("contribute - hot tables")
	class ContributeHotTables {

		@Test
		@DisplayName("includes table when meeting threshold")
		void includesTableWhenMeetingThreshold() {
			tracker.recordTableAccess("fct_orders");
			tracker.recordTableAccess("fct_orders");

			String contribution = contributor.contribute(null).orElse("");

			assertThat(contribution).contains("fct_orders");
			assertThat(contribution).contains("Fact table for orders");
		}

		@Test
		@DisplayName("includes columns for hot table")
		void includesColumnsForHotTable() {
			tracker.recordTableAccess("fct_orders");
			tracker.recordTableAccess("fct_orders");

			String contribution = contributor.contribute(null).orElse("");

			assertThat(contribution).contains("order_id");
			assertThat(contribution).contains("customer_id");
			assertThat(contribution).contains("order_value");
		}

		@Test
		@DisplayName("includes column metadata")
		void includesColumnMetadata() {
			tracker.recordTableAccess("fct_orders");
			tracker.recordTableAccess("fct_orders");

			String contribution = contributor.contribute(null).orElse("");

			assertThat(contribution).contains("type=string");
			assertThat(contribution).contains("tags=pk");
			assertThat(contribution).contains("fk:dim_customer.id");
		}

		@Test
		@DisplayName("excludes cold tables")
		void excludesColdTables() {
			tracker.recordTableAccess("fct_orders");
			tracker.recordTableAccess("fct_orders");
			tracker.recordTableAccess("dim_customer");  // Only 1 access

			String contribution = contributor.contribute(null).orElse("");

			assertThat(contribution).contains("fct_orders");
			// Note: fct_orders has FK to dim_customer, so that reference will appear in tags
			// We check for dim_customer as a table header (with colon) to exclude the FK reference
			assertThat(contribution).doesNotContain("dim_customer:");
			assertThat(contribution).doesNotContain("dim_date");
		}

		@Test
		@DisplayName("includes multiple hot tables")
		void includesMultipleHotTables() {
			tracker.recordTableAccess("fct_orders");
			tracker.recordTableAccess("fct_orders");
			tracker.recordTableAccess("dim_customer");
			tracker.recordTableAccess("dim_customer");

			String contribution = contributor.contribute(null).orElse("");

			assertThat(contribution).contains("fct_orders");
			assertThat(contribution).contains("dim_customer");
			assertThat(contribution).doesNotContain("dim_date");
		}

		@Test
		@DisplayName("includes critical footer guidance")
		void includesCriticalFooterGuidance() {
			tracker.recordTableAccess("fct_orders");
			tracker.recordTableAccess("fct_orders");

			String contribution = contributor.contribute(null).orElse("");

			assertThat(contribution).contains("CRITICAL");
			assertThat(contribution).contains("table NAME");
			assertThat(contribution).contains("column NAME");
		}

		@Test
		@DisplayName("mentions tools for undiscovered tables")
		void mentionsToolsForUndiscoveredTables() {
			tracker.recordTableAccess("fct_orders");
			tracker.recordTableAccess("fct_orders");

			String contribution = contributor.contribute(null).orElse("");

			assertThat(contribution).contains("listTables");
			assertThat(contribution).contains("getTableDetails");
		}
	}

	@Nested
	@DisplayName("contribute - with tokenization")
	class ContributeWithTokenization {

		@BeforeEach
		void enableTokenization() {
			catalog = new InMemorySqlCatalog()
					.withTokenization(true)
					.addTable("fct_orders", "Fact table for orders", "fact")
					.withSynonyms("fct_orders", "orders")  // "orders" becomes token
					.addColumn("fct_orders", "order_value", "Order amount", "double", 
							new String[]{"measure"}, null)
					.withColumnSynonyms("fct_orders", "order_value", "value");  // "value" becomes token

			contributor = new AdaptiveSqlCatalogContributor(catalog, tracker, 2);
		}

		@Test
		@DisplayName("uses tokenized names in contribution")
		void usesTokenizedNames() {
			tracker.recordTableAccess("fct_orders");
			tracker.recordTableAccess("fct_orders");

			String contribution = contributor.contribute(null).orElse("");

			assertThat(contribution).contains("orders");  // Token, not fct_orders
			assertThat(contribution).contains("value");   // Token, not order_value
			assertThat(contribution).doesNotContain("fct_orders");
			assertThat(contribution).doesNotContain("order_value");
		}
	}

	@Nested
	@DisplayName("getHotThreshold")
	class GetHotThreshold {

		@Test
		@DisplayName("returns configured threshold")
		void returnsConfiguredThreshold() {
			assertThat(contributor.getHotThreshold()).isEqualTo(2);
		}
	}

	@Nested
	@DisplayName("integration scenario")
	class IntegrationScenario {

		@Test
		@DisplayName("simulates adaptive warm-up over time")
		void simulatesAdaptiveWarmUp() {
			// Cold start - no tables in prompt
			String cold = contributor.contribute(null).orElse("");
			assertThat(cold).contains("No frequently-used tables");

			// First access to fct_orders
			tracker.recordTableAccess("fct_orders");
			String warming = contributor.contribute(null).orElse("");
			assertThat(warming).contains("No frequently-used tables");  // Still cold

			// Second access - now hot
			tracker.recordTableAccess("fct_orders");
			String warm = contributor.contribute(null).orElse("");
			assertThat(warm).contains("fct_orders");
			assertThat(warm).doesNotContain("No frequently-used tables");

			// dim_customer also becomes hot
			tracker.recordTableAccess("dim_customer");
			tracker.recordTableAccess("dim_customer");
			String hot = contributor.contribute(null).orElse("");
			assertThat(hot).contains("fct_orders");
			assertThat(hot).contains("dim_customer");
		}
	}
}

