package org.javai.springai.actions.sql;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TokenGenerator")
class TokenGeneratorTest {

	@Nested
	@DisplayName("Table token generation")
	class TableTokenGeneration {

		@Test
		@DisplayName("generates ft_ prefix for fact tables")
		void generatesFactPrefix() {
			String token = TokenGenerator.tableToken("fct_orders", "fact");
			assertThat(token).startsWith("ft_");
		}

		@Test
		@DisplayName("generates dt_ prefix for dimension tables")
		void generatesDimensionPrefix() {
			String token = TokenGenerator.tableToken("dim_customer", "dimension");
			assertThat(token).startsWith("dt_");
		}

		@Test
		@DisplayName("generates bt_ prefix for bridge tables")
		void generatesBridgePrefix() {
			String token = TokenGenerator.tableToken("bridge_order_product", "bridge");
			assertThat(token).startsWith("bt_");
		}

		@Test
		@DisplayName("generates t_ prefix for unknown table types")
		void generatesGenericPrefix() {
			String token = TokenGenerator.tableToken("some_table");
			assertThat(token).startsWith("t_");
		}

		@Test
		@DisplayName("generates t_ prefix when tags are null")
		void generatesGenericPrefixForNullTags() {
			String token = TokenGenerator.tableToken("some_table", (String[]) null);
			assertThat(token).startsWith("t_");
		}

		@Test
		@DisplayName("generates stable tokens for same input")
		void generatesStableTokens() {
			String token1 = TokenGenerator.tableToken("fct_orders", "fact");
			String token2 = TokenGenerator.tableToken("fct_orders", "fact");
			assertThat(token1).isEqualTo(token2);
		}

		@Test
		@DisplayName("generates different tokens for different tables")
		void generatesDifferentTokensForDifferentTables() {
			String token1 = TokenGenerator.tableToken("fct_orders", "fact");
			String token2 = TokenGenerator.tableToken("fct_sales", "fact");
			assertThat(token1).isNotEqualTo(token2);
		}

		@Test
		@DisplayName("token has correct length (prefix + 6 char hash)")
		void tokenHasCorrectLength() {
			String token = TokenGenerator.tableToken("fct_orders", "fact");
			// ft_ (3) + hash (6) = 9
			assertThat(token).hasSize(9);
		}

		@Test
		@DisplayName("finds tag in mixed tags array")
		void findTagInMixedArray() {
			String token = TokenGenerator.tableToken("fct_orders", "pk", "fact", "metric");
			assertThat(token).startsWith("ft_");
		}

		@Test
		@DisplayName("handles case-insensitive tags")
		void handlesCaseInsensitiveTags() {
			String token1 = TokenGenerator.tableToken("orders", "FACT");
			String token2 = TokenGenerator.tableToken("orders", "fact");
			String token3 = TokenGenerator.tableToken("orders", "Fact");
			
			assertThat(token1).startsWith("ft_");
			assertThat(token2).startsWith("ft_");
			assertThat(token3).startsWith("ft_");
		}
	}

	@Nested
	@DisplayName("Column token generation")
	class ColumnTokenGeneration {

		@Test
		@DisplayName("generates c_ prefix for columns")
		void generatesColumnPrefix() {
			String token = TokenGenerator.columnToken("orders", "customer_id");
			assertThat(token).startsWith("c_");
		}

		@Test
		@DisplayName("generates stable tokens for same input")
		void generatesStableTokens() {
			String token1 = TokenGenerator.columnToken("orders", "customer_id");
			String token2 = TokenGenerator.columnToken("orders", "customer_id");
			assertThat(token1).isEqualTo(token2);
		}

		@Test
		@DisplayName("generates different tokens for same column in different tables")
		void generatesDifferentTokensForSameColumnDifferentTables() {
			String token1 = TokenGenerator.columnToken("orders", "id");
			String token2 = TokenGenerator.columnToken("customers", "id");
			assertThat(token1).isNotEqualTo(token2);
		}

		@Test
		@DisplayName("generates different tokens for different columns in same table")
		void generatesDifferentTokensForDifferentColumns() {
			String token1 = TokenGenerator.columnToken("orders", "customer_id");
			String token2 = TokenGenerator.columnToken("orders", "order_date");
			assertThat(token1).isNotEqualTo(token2);
		}

		@Test
		@DisplayName("token has correct length (c_ + 6 char hash)")
		void tokenHasCorrectLength() {
			String token = TokenGenerator.columnToken("orders", "customer_id");
			// c_ (2) + hash (6) = 8
			assertThat(token).hasSize(8);
		}
	}

	@Nested
	@DisplayName("Token pattern detection")
	class TokenPatternDetection {

		@Test
		@DisplayName("isTableToken returns true for valid table tokens")
		void detectsValidTableTokens() {
			assertThat(TokenGenerator.isTableToken("ft_a1b2c3")).isTrue();
			assertThat(TokenGenerator.isTableToken("dt_d4e5f6")).isTrue();
			assertThat(TokenGenerator.isTableToken("bt_789abc")).isTrue();
			assertThat(TokenGenerator.isTableToken("t_fedcba")).isTrue();
		}

		@Test
		@DisplayName("isTableToken returns false for invalid tokens")
		void rejectsInvalidTableTokens() {
			assertThat(TokenGenerator.isTableToken("fct_orders")).isFalse();  // real table name
			assertThat(TokenGenerator.isTableToken("c_a1b2c3")).isFalse();    // column token
			assertThat(TokenGenerator.isTableToken("ft_")).isFalse();          // missing hash
			assertThat(TokenGenerator.isTableToken("ft_abc")).isFalse();       // hash too short
			assertThat(TokenGenerator.isTableToken(null)).isFalse();
			assertThat(TokenGenerator.isTableToken("")).isFalse();
		}

		@Test
		@DisplayName("isColumnToken returns true for valid column tokens")
		void detectsValidColumnTokens() {
			assertThat(TokenGenerator.isColumnToken("c_a1b2c3")).isTrue();
			assertThat(TokenGenerator.isColumnToken("c_789abc")).isTrue();
		}

		@Test
		@DisplayName("isColumnToken returns false for invalid tokens")
		void rejectsInvalidColumnTokens() {
			assertThat(TokenGenerator.isColumnToken("customer_id")).isFalse();  // real column name
			assertThat(TokenGenerator.isColumnToken("ft_a1b2c3")).isFalse();    // table token
			assertThat(TokenGenerator.isColumnToken("c_")).isFalse();            // missing hash
			assertThat(TokenGenerator.isColumnToken("c_abc")).isFalse();         // hash too short
			assertThat(TokenGenerator.isColumnToken(null)).isFalse();
			assertThat(TokenGenerator.isColumnToken("")).isFalse();
		}
	}
}

