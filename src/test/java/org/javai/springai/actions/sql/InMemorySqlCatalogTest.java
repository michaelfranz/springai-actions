package org.javai.springai.actions.sql;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("InMemorySqlCatalog")
class InMemorySqlCatalogTest {

	@Nested
	@DisplayName("Model Name Mapping")
	class ModelNameMapping {

		@Test
		@DisplayName("model names disabled by default")
		void modelNamesDisabledByDefault() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.addTable("orders", "Orders", "fact");

			assertThat(catalog.usesModelNames()).isFalse();
			assertThat(catalog.getTableModelName("orders")).isEmpty();
		}

		@Test
		@DisplayName("model names can be enabled")
		void modelNamesCanBeEnabled() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withModelNames(true)
					.addTable("orders", "Orders", "fact");

			assertThat(catalog.usesModelNames()).isTrue();
		}

		@Test
		@DisplayName("generates table model names with correct prefix")
		void generatesTableModelNamesWithPrefix() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withModelNames(true)
					.addTable("fct_orders", "Orders fact", "fact")
					.addTable("dim_customer", "Customer dimension", "dimension")
					.addTable("bridge_table", "Bridge", "bridge")
					.addTable("generic_table", "Generic", "other");

			assertThat(catalog.getTableModelName("fct_orders")).isPresent().get().asString().startsWith("ft_");
			assertThat(catalog.getTableModelName("dim_customer")).isPresent().get().asString().startsWith("dt_");
			assertThat(catalog.getTableModelName("bridge_table")).isPresent().get().asString().startsWith("bt_");
			assertThat(catalog.getTableModelName("generic_table")).isPresent().get().asString().startsWith("t_");
		}

		@Test
		@DisplayName("generates column model names with c_ prefix")
		void generatesColumnModelNames() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withModelNames(true)
					.addTable("orders", "Orders", "fact")
					.addColumn("orders", "customer_id", "FK", "integer", null, null);

			assertThat(catalog.getColumnModelName("orders", "customer_id"))
					.isPresent()
					.get().asString().startsWith("c_");
		}

		@Test
		@DisplayName("table model names are stable")
		void tableModelNamesAreStable() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withModelNames(true)
					.addTable("orders", "Orders", "fact");

			String modelName1 = catalog.getTableModelName("orders").orElseThrow();
			String modelName2 = catalog.getTableModelName("orders").orElseThrow();

			assertThat(modelName1).isEqualTo(modelName2);
		}

		@Test
		@DisplayName("column model names are stable")
		void columnModelNamesAreStable() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withModelNames(true)
					.addTable("orders", "Orders", "fact")
					.addColumn("orders", "id", "PK", "integer", null, null);

			String modelName1 = catalog.getColumnModelName("orders", "id").orElseThrow();
			String modelName2 = catalog.getColumnModelName("orders", "id").orElseThrow();

			assertThat(modelName1).isEqualTo(modelName2);
		}

		@Test
		@DisplayName("resolves table model name back to canonical name")
		void resolvesTableModelName() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withModelNames(true)
					.addTable("fct_orders", "Orders", "fact");

			String modelName = catalog.getTableModelName("fct_orders").orElseThrow();
			String resolved = catalog.resolveTableFromModelName(modelName).orElseThrow();

			assertThat(resolved).isEqualTo("fct_orders");
		}

		@Test
		@DisplayName("resolves column model name back to canonical name")
		void resolvesColumnModelName() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withModelNames(true)
					.addTable("orders", "Orders", "fact")
					.addColumn("orders", "customer_id", "FK", "integer", null, null);

			String tableModelName = catalog.getTableModelName("orders").orElseThrow();
			String columnModelName = catalog.getColumnModelName("orders", "customer_id").orElseThrow();
			String resolved = catalog.resolveColumnFromModelName(tableModelName, columnModelName).orElseThrow();

			assertThat(resolved).isEqualTo("customer_id");
		}

		@Test
		@DisplayName("different tables have different tokens")
		void differentTablesHaveDifferentTokens() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withModelNames(true)
					.addTable("orders", "Orders", "fact")
					.addTable("customers", "Customers", "dimension");

			String ordersToken = catalog.getTableModelName("orders").orElseThrow();
			String customersToken = catalog.getTableModelName("customers").orElseThrow();

			assertThat(ordersToken).isNotEqualTo(customersToken);
		}

		@Test
		@DisplayName("same column name in different tables has different tokens")
		void sameColumnDifferentTablesHasDifferentTokens() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withModelNames(true)
					.addTable("orders", "Orders", "fact")
					.addColumn("orders", "id", "PK", "integer", null, null)
					.addTable("customers", "Customers", "dimension")
					.addColumn("customers", "id", "PK", "integer", null, null);

			String ordersIdToken = catalog.getColumnModelName("orders", "id").orElseThrow();
			String customersIdToken = catalog.getColumnModelName("customers", "id").orElseThrow();

			assertThat(ordersIdToken).isNotEqualTo(customersIdToken);
		}

		@Test
		@DisplayName("tokenMappings returns all mappings")
		void tokenMappingsReturnsAll() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withModelNames(true)
					.addTable("orders", "Orders", "fact")
					.addColumn("orders", "id", "PK", "integer", null, null)
					.addColumn("orders", "amount", "Amount", "decimal", null, null);

			var mappings = catalog.modelNameMappings();

			// Should contain table token and column tokens
			assertThat(mappings).containsValue("orders");
			assertThat(mappings).containsValue("orders.id");
			assertThat(mappings).containsValue("orders.amount");
		}

		@Test
		@DisplayName("tokenMappings is empty when tokenization disabled")
		void tokenMappingsEmptyWhenDisabled() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.addTable("orders", "Orders", "fact");

			assertThat(catalog.modelNameMappings()).isEmpty();
		}

		@Test
		@DisplayName("getTableToken returns empty for unknown table")
		void getTableTokenReturnsEmptyForUnknown() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withModelNames(true)
					.addTable("orders", "Orders", "fact");

			assertThat(catalog.getTableModelName("unknown")).isEmpty();
		}

		@Test
		@DisplayName("getColumnToken returns empty for unknown column")
		void getColumnTokenReturnsEmptyForUnknown() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withModelNames(true)
					.addTable("orders", "Orders", "fact");

			assertThat(catalog.getColumnModelName("orders", "unknown")).isEmpty();
		}

		@Test
		@DisplayName("resolveTableToken returns empty for invalid token")
		void resolveTableTokenReturnsEmptyForInvalid() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withModelNames(true)
					.addTable("orders", "Orders", "fact");

			assertThat(catalog.resolveTableFromModelName("invalid_token")).isEmpty();
		}

		@Test
		@DisplayName("token mappings rebuild after adding new table")
		void tokenMappingsRebuildAfterAddingTable() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withModelNames(true)
					.addTable("orders", "Orders", "fact");

			String ordersToken = catalog.getTableModelName("orders").orElseThrow();

			// Add another table
			catalog.addTable("customers", "Customers", "dimension");

			// Both should be accessible
			assertThat(catalog.getTableModelName("orders")).hasValue(ordersToken);
			assertThat(catalog.getTableModelName("customers")).isPresent();
		}

		@Test
		@DisplayName("first synonym becomes table token when synonyms defined")
		void firstSynonymBecomesTableToken() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withModelNames(true)
					.addTable("fct_orders", "Order transactions", "fact")
					.withSynonyms("fct_orders", "orders", "sales", "order");

			// First synonym "orders" should be the token
			assertThat(catalog.getTableModelName("fct_orders")).hasValue("orders");
			
			// Resolving "orders" should give back "fct_orders"
			assertThat(catalog.resolveTableFromModelName("orders")).hasValue("fct_orders");
		}

		@Test
		@DisplayName("first synonym becomes column token when synonyms defined")
		void firstSynonymBecomesColumnToken() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withModelNames(true)
					.addTable("fct_orders", "Orders", "fact")
					.addColumn("fct_orders", "order_value", "Total amount", "decimal", null, null)
					.withColumnSynonyms("fct_orders", "order_value", "value", "amount", "total");

			String tableToken = catalog.getTableModelName("fct_orders").orElseThrow();
			
			// First synonym "value" should be the column token
			assertThat(catalog.getColumnModelName("fct_orders", "order_value")).hasValue("value");
			
			// Resolving "value" should give back "order_value"
			assertThat(catalog.resolveColumnFromModelName(tableToken, "value")).hasValue("order_value");
		}

		@Test
		@DisplayName("cryptic token used when no synonyms defined")
		void crypticTokenWhenNoSynonyms() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withModelNames(true)
					.addTable("fct_orders", "Order transactions", "fact")
					.addColumn("fct_orders", "order_value", "Total amount", "decimal", null, null);
			// No synonyms added

			// Should use cryptic hash-based tokens
			assertThat(catalog.getTableModelName("fct_orders")).isPresent().get().asString().startsWith("ft_");
			assertThat(catalog.getColumnModelName("fct_orders", "order_value")).isPresent().get().asString().startsWith("c_");
		}

		@Test
		@DisplayName("mixed synonym and cryptic tokens in same catalog")
		void mixedSynonymAndCrypticTokens() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withModelNames(true)
					.addTable("fct_orders", "Orders", "fact")
					.withSynonyms("fct_orders", "orders")  // Gets synonym token
					.addTable("dim_customer", "Customers", "dimension");  // No synonyms, gets cryptic token

			assertThat(catalog.getTableModelName("fct_orders")).hasValue("orders");
			assertThat(catalog.getTableModelName("dim_customer")).isPresent().get().asString().startsWith("dt_");
		}
	}
}

