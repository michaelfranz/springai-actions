package org.javai.springai.actions.sql;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("InMemorySqlCatalog")
class InMemorySqlCatalogTest {

	@Nested
	@DisplayName("Tokenization")
	class Tokenization {

		@Test
		@DisplayName("tokenization is disabled by default")
		void tokenizationDisabledByDefault() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.addTable("orders", "Orders", "fact");

			assertThat(catalog.isTokenized()).isFalse();
			assertThat(catalog.getTableToken("orders")).isEmpty();
		}

		@Test
		@DisplayName("tokenization can be enabled")
		void tokenizationCanBeEnabled() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withTokenization(true)
					.addTable("orders", "Orders", "fact");

			assertThat(catalog.isTokenized()).isTrue();
		}

		@Test
		@DisplayName("generates table tokens with correct prefix")
		void generatesTableTokensWithPrefix() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withTokenization(true)
					.addTable("fct_orders", "Orders fact", "fact")
					.addTable("dim_customer", "Customer dimension", "dimension")
					.addTable("bridge_table", "Bridge", "bridge")
					.addTable("generic_table", "Generic", "other");

			assertThat(catalog.getTableToken("fct_orders")).isPresent().get().asString().startsWith("ft_");
			assertThat(catalog.getTableToken("dim_customer")).isPresent().get().asString().startsWith("dt_");
			assertThat(catalog.getTableToken("bridge_table")).isPresent().get().asString().startsWith("bt_");
			assertThat(catalog.getTableToken("generic_table")).isPresent().get().asString().startsWith("t_");
		}

		@Test
		@DisplayName("generates column tokens with c_ prefix")
		void generatesColumnTokens() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withTokenization(true)
					.addTable("orders", "Orders", "fact")
					.addColumn("orders", "customer_id", "FK", "integer", null, null);

			assertThat(catalog.getColumnToken("orders", "customer_id"))
					.isPresent()
					.get().asString().startsWith("c_");
		}

		@Test
		@DisplayName("table tokens are stable")
		void tableTokensAreStable() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withTokenization(true)
					.addTable("orders", "Orders", "fact");

			String token1 = catalog.getTableToken("orders").orElseThrow();
			String token2 = catalog.getTableToken("orders").orElseThrow();

			assertThat(token1).isEqualTo(token2);
		}

		@Test
		@DisplayName("column tokens are stable")
		void columnTokensAreStable() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withTokenization(true)
					.addTable("orders", "Orders", "fact")
					.addColumn("orders", "id", "PK", "integer", null, null);

			String token1 = catalog.getColumnToken("orders", "id").orElseThrow();
			String token2 = catalog.getColumnToken("orders", "id").orElseThrow();

			assertThat(token1).isEqualTo(token2);
		}

		@Test
		@DisplayName("resolves table token back to name")
		void resolvesTableToken() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withTokenization(true)
					.addTable("fct_orders", "Orders", "fact");

			String token = catalog.getTableToken("fct_orders").orElseThrow();
			String resolved = catalog.resolveTableToken(token).orElseThrow();

			assertThat(resolved).isEqualTo("fct_orders");
		}

		@Test
		@DisplayName("resolves column token back to name")
		void resolvesColumnToken() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withTokenization(true)
					.addTable("orders", "Orders", "fact")
					.addColumn("orders", "customer_id", "FK", "integer", null, null);

			String tableToken = catalog.getTableToken("orders").orElseThrow();
			String columnToken = catalog.getColumnToken("orders", "customer_id").orElseThrow();
			String resolved = catalog.resolveColumnToken(tableToken, columnToken).orElseThrow();

			assertThat(resolved).isEqualTo("customer_id");
		}

		@Test
		@DisplayName("different tables have different tokens")
		void differentTablesHaveDifferentTokens() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withTokenization(true)
					.addTable("orders", "Orders", "fact")
					.addTable("customers", "Customers", "dimension");

			String ordersToken = catalog.getTableToken("orders").orElseThrow();
			String customersToken = catalog.getTableToken("customers").orElseThrow();

			assertThat(ordersToken).isNotEqualTo(customersToken);
		}

		@Test
		@DisplayName("same column name in different tables has different tokens")
		void sameColumnDifferentTablesHasDifferentTokens() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withTokenization(true)
					.addTable("orders", "Orders", "fact")
					.addColumn("orders", "id", "PK", "integer", null, null)
					.addTable("customers", "Customers", "dimension")
					.addColumn("customers", "id", "PK", "integer", null, null);

			String ordersIdToken = catalog.getColumnToken("orders", "id").orElseThrow();
			String customersIdToken = catalog.getColumnToken("customers", "id").orElseThrow();

			assertThat(ordersIdToken).isNotEqualTo(customersIdToken);
		}

		@Test
		@DisplayName("tokenMappings returns all mappings")
		void tokenMappingsReturnsAll() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withTokenization(true)
					.addTable("orders", "Orders", "fact")
					.addColumn("orders", "id", "PK", "integer", null, null)
					.addColumn("orders", "amount", "Amount", "decimal", null, null);

			var mappings = catalog.tokenMappings();

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

			assertThat(catalog.tokenMappings()).isEmpty();
		}

		@Test
		@DisplayName("getTableToken returns empty for unknown table")
		void getTableTokenReturnsEmptyForUnknown() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withTokenization(true)
					.addTable("orders", "Orders", "fact");

			assertThat(catalog.getTableToken("unknown")).isEmpty();
		}

		@Test
		@DisplayName("getColumnToken returns empty for unknown column")
		void getColumnTokenReturnsEmptyForUnknown() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withTokenization(true)
					.addTable("orders", "Orders", "fact");

			assertThat(catalog.getColumnToken("orders", "unknown")).isEmpty();
		}

		@Test
		@DisplayName("resolveTableToken returns empty for invalid token")
		void resolveTableTokenReturnsEmptyForInvalid() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withTokenization(true)
					.addTable("orders", "Orders", "fact");

			assertThat(catalog.resolveTableToken("invalid_token")).isEmpty();
		}

		@Test
		@DisplayName("token mappings rebuild after adding new table")
		void tokenMappingsRebuildAfterAddingTable() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withTokenization(true)
					.addTable("orders", "Orders", "fact");

			String ordersToken = catalog.getTableToken("orders").orElseThrow();

			// Add another table
			catalog.addTable("customers", "Customers", "dimension");

			// Both should be accessible
			assertThat(catalog.getTableToken("orders")).hasValue(ordersToken);
			assertThat(catalog.getTableToken("customers")).isPresent();
		}

		@Test
		@DisplayName("first synonym becomes table token when synonyms defined")
		void firstSynonymBecomesTableToken() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withTokenization(true)
					.addTable("fct_orders", "Order transactions", "fact")
					.withSynonyms("fct_orders", "orders", "sales", "order");

			// First synonym "orders" should be the token
			assertThat(catalog.getTableToken("fct_orders")).hasValue("orders");
			
			// Resolving "orders" should give back "fct_orders"
			assertThat(catalog.resolveTableToken("orders")).hasValue("fct_orders");
		}

		@Test
		@DisplayName("first synonym becomes column token when synonyms defined")
		void firstSynonymBecomesColumnToken() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withTokenization(true)
					.addTable("fct_orders", "Orders", "fact")
					.addColumn("fct_orders", "order_value", "Total amount", "decimal", null, null)
					.withColumnSynonyms("fct_orders", "order_value", "value", "amount", "total");

			String tableToken = catalog.getTableToken("fct_orders").orElseThrow();
			
			// First synonym "value" should be the column token
			assertThat(catalog.getColumnToken("fct_orders", "order_value")).hasValue("value");
			
			// Resolving "value" should give back "order_value"
			assertThat(catalog.resolveColumnToken(tableToken, "value")).hasValue("order_value");
		}

		@Test
		@DisplayName("cryptic token used when no synonyms defined")
		void crypticTokenWhenNoSynonyms() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withTokenization(true)
					.addTable("fct_orders", "Order transactions", "fact")
					.addColumn("fct_orders", "order_value", "Total amount", "decimal", null, null);
			// No synonyms added

			// Should use cryptic hash-based tokens
			assertThat(catalog.getTableToken("fct_orders")).isPresent().get().asString().startsWith("ft_");
			assertThat(catalog.getColumnToken("fct_orders", "order_value")).isPresent().get().asString().startsWith("c_");
		}

		@Test
		@DisplayName("mixed synonym and cryptic tokens in same catalog")
		void mixedSynonymAndCrypticTokens() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withTokenization(true)
					.addTable("fct_orders", "Orders", "fact")
					.withSynonyms("fct_orders", "orders")  // Gets synonym token
					.addTable("dim_customer", "Customers", "dimension");  // No synonyms, gets cryptic token

			assertThat(catalog.getTableToken("fct_orders")).hasValue("orders");
			assertThat(catalog.getTableToken("dim_customer")).isPresent().get().asString().startsWith("dt_");
		}
	}
}

