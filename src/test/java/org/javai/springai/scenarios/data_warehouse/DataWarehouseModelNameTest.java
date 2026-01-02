package org.javai.springai.scenarios.data_warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import org.javai.springai.actions.sql.InMemorySqlCatalog;
import org.javai.springai.actions.sql.Query;
import org.javai.springai.actions.sql.SqlCatalogContextContributor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for model name mapping and tokenization.
 * 
 * <p>These tests verify the tokenization infrastructure for hiding
 * real database object names from the LLM. They don't require LLM access
 * and run quickly.</p>
 */
@DisplayName("Data Warehouse - Model Name Mapping (No LLM)")
class DataWarehouseModelNameTest {

	private InMemorySqlCatalog modelNameCatalog;

	@BeforeEach
	void setUpModelNameTests() {
		// Create a catalog with model name mapping enabled
		modelNameCatalog = new InMemorySqlCatalog()
				.withModelNames(true)
				.addTable("fct_orders", "Fact table for orders", "fact")
				.addColumn("fct_orders", "customer_id", "FK to dim_customer", "string",
						new String[]{"fk:dim_customer.id"}, null)
				.addColumn("fct_orders", "date_id", "FK to dim_date", "string",
						new String[]{"fk:dim_date.id"}, null)
				.addColumn("fct_orders", "order_value", "Order amount", "double",
						new String[]{"measure"}, null)
				.addTable("dim_customer", "Customer dimension", "dimension")
				.addColumn("dim_customer", "id", "PK", "string",
						new String[]{"pk"}, new String[]{"unique"})
				.addColumn("dim_customer", "customer_name", "Customer name", "string",
						new String[]{"attribute"}, null)
				.addTable("dim_date", "Date dimension", "dimension")
				.addColumn("dim_date", "id", "PK", "string",
						new String[]{"pk"}, new String[]{"unique"})
				.addColumn("dim_date", "date", "Calendar date", "date",
						new String[]{"attribute"}, null);
	}

	@Test
	@DisplayName("catalog generates correct model name prefixes for DW schema")
	void catalogGeneratesCorrectModelNamePrefixes() {
		// Fact tables should have ft_ prefix when no synonyms defined
		String ordersModelName = modelNameCatalog.getTableModelName("fct_orders").orElseThrow();
		assertThat(ordersModelName).startsWith("ft_");

		// Dimension tables should have dt_ prefix when no synonyms defined
		String customerModelName = modelNameCatalog.getTableModelName("dim_customer").orElseThrow();
		assertThat(customerModelName).startsWith("dt_");

		String dateModelName = modelNameCatalog.getTableModelName("dim_date").orElseThrow();
		assertThat(dateModelName).startsWith("dt_");
	}

	@Test
	@DisplayName("model name catalog contributor uses synonyms as model names")
	void modelNameCatalogContributorUsesSynonyms() {
		// With model name mapping, first synonym becomes the displayed name
		// If no synonym defined, a generated identifier is used
		InMemorySqlCatalog catalogWithSynonyms = new InMemorySqlCatalog()
				.withModelNames(true)
				.addTable("fct_orders", "Fact table for orders", "fact")
				.withSynonyms("fct_orders", "orders", "sales")  // "orders" is displayed name
				.addColumn("fct_orders", "customer_id", "FK to dim_customer", "string",
						new String[]{"fk:dim_customer.id"}, null)  // no synonym -> cryptic token
				.addColumn("fct_orders", "order_value", "Order amount", "double",
						new String[]{"measure"}, null)
				.withColumnSynonyms("fct_orders", "order_value", "value", "amount")  // "value" is displayed
				.addTable("dim_customer", "Customer dimension", "dimension")
				.withSynonyms("dim_customer", "customers", "cust")  // "customers" is displayed name
				.addColumn("dim_customer", "id", "PK", "string",
						new String[]{"pk"}, new String[]{"unique"})  // no synonym -> cryptic token
				.addColumn("dim_customer", "customer_name", "Customer name", "string",
						new String[]{"attribute"}, null)
				.withColumnSynonyms("dim_customer", "customer_name", "name", "cust_name");  // "name" is displayed

		SqlCatalogContextContributor contributor = new SqlCatalogContextContributor(catalogWithSynonyms);
		String prompt = contributor.contribute(null).orElseThrow();

		// LLM sees standard SQL CATALOG (unaware of tokenization)
		assertThat(prompt).contains("SQL CATALOG:");
		
		// Tables with synonyms: first synonym becomes the displayed name
		assertThat(prompt).contains("- orders:");  // fct_orders -> "orders"
		assertThat(prompt).contains("- customers:");  // dim_customer -> "customers"
		
		// Columns without synonyms: still use cryptic tokens (c_)
		assertThat(prompt).contains("c_");

		// Real database names should NOT appear as identifiers
		assertThat(prompt).doesNotContain("fct_orders:");
		assertThat(prompt).doesNotContain("dim_customer:");
		assertThat(prompt).doesNotContain("• customer_id");
		assertThat(prompt).doesNotContain("• order_value");

		// Descriptions should still be present (for LLM understanding)
		assertThat(prompt).contains("Fact table for orders");
		assertThat(prompt).contains("Customer dimension");
		assertThat(prompt).contains("Order amount");

		// Remaining synonyms shown as "also:" (first one is the displayed name)
		assertThat(prompt).contains("(also: sales)");  // "orders" is displayed, "sales" is remaining
		assertThat(prompt).contains("(also: cust)");   // "customers" is displayed, "cust" is remaining
		assertThat(prompt).contains("also=amount");    // "value" is displayed, "amount" is remaining
		assertThat(prompt).contains("also=cust_name"); // "name" is displayed, "cust_name" is remaining
	}

	@Test
	@DisplayName("resolves simple SELECT from model SQL to canonical names")
	void resolvesSimpleSelect() {
		String ordersModelName = modelNameCatalog.getTableModelName("fct_orders").orElseThrow();
		String orderValueModelName = modelNameCatalog.getColumnModelName("fct_orders", "order_value").orElseThrow();

		String modelSql = "SELECT " + orderValueModelName + " FROM " + ordersModelName;
		Query query = Query.fromSql(modelSql, modelNameCatalog);

		String sql = query.sqlString().toUpperCase();
		assertThat(sql).contains("ORDER_VALUE");
		assertThat(sql).contains("FCT_ORDERS");
		assertThat(sql).doesNotContain(ordersModelName.toUpperCase());
		assertThat(sql).doesNotContain(orderValueModelName.toUpperCase());
	}

	@Test
	@DisplayName("resolves JOIN query from model SQL to canonical names")
	void resolvesJoinQuery() {
		String ordersModelName = modelNameCatalog.getTableModelName("fct_orders").orElseThrow();
		String customerModelName = modelNameCatalog.getTableModelName("dim_customer").orElseThrow();
		String orderValueModelName = modelNameCatalog.getColumnModelName("fct_orders", "order_value").orElseThrow();
		String customerIdModelName = modelNameCatalog.getColumnModelName("fct_orders", "customer_id").orElseThrow();
		String customerPkModelName = modelNameCatalog.getColumnModelName("dim_customer", "id").orElseThrow();
		String customerNameModelName = modelNameCatalog.getColumnModelName("dim_customer", "customer_name").orElseThrow();

		String modelSql = "SELECT o." + orderValueModelName + ", c." + customerNameModelName 
				+ " FROM " + ordersModelName + " o JOIN " + customerModelName + " c ON o." + customerIdModelName 
				+ " = c." + customerPkModelName;

		Query query = Query.fromSql(modelSql, modelNameCatalog);
		String sql = query.sqlString().toUpperCase();

		assertThat(sql).contains("O.ORDER_VALUE");
		assertThat(sql).contains("C.CUSTOMER_NAME");
		assertThat(sql).contains("FCT_ORDERS O");
		assertThat(sql).contains("DIM_CUSTOMER C");
		assertThat(sql).contains("O.CUSTOMER_ID = C.ID");
	}

	@Test
	@DisplayName("modelSql() converts canonical names to model names")
	void modelSqlConvertsToModelNames() {
		// Start with canonical SQL
		String canonicalSql = "SELECT order_value, customer_id FROM fct_orders";
		Query query = Query.fromSql(canonicalSql, modelNameCatalog);

		String modelSql = query.modelSql();

		// Should contain model names
		String ordersModelName = modelNameCatalog.getTableModelName("fct_orders").orElseThrow();
		String orderValueModelName = modelNameCatalog.getColumnModelName("fct_orders", "order_value").orElseThrow();
		String customerIdModelName = modelNameCatalog.getColumnModelName("fct_orders", "customer_id").orElseThrow();

		assertThat(modelSql).contains(ordersModelName);
		assertThat(modelSql).contains(orderValueModelName);
		assertThat(modelSql).contains(customerIdModelName);

		// Real/canonical names should NOT appear
		assertThat(modelSql).doesNotContain("fct_orders");
		assertThat(modelSql).doesNotContain("order_value");
		assertThat(modelSql).doesNotContain("customer_id");
	}

	@Test
	@DisplayName("FK references in prompt use model names")
	void fkReferencesUseModelNames() {
		SqlCatalogContextContributor contributor = new SqlCatalogContextContributor(modelNameCatalog);
		String prompt = contributor.contribute(null).orElseThrow();

		// FK reference should use model names, not real/canonical names
		// e.g., "fk:dt_abc123.c_def456" instead of "fk:dim_customer.id"
		String customerModelName = modelNameCatalog.getTableModelName("dim_customer").orElseThrow();
		String customerIdModelName = modelNameCatalog.getColumnModelName("dim_customer", "id").orElseThrow();

		assertThat(prompt).contains("fk:" + customerModelName + "." + customerIdModelName);
		assertThat(prompt).doesNotContain("fk:dim_customer.id");
	}
}

