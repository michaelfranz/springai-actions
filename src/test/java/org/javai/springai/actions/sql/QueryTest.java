package org.javai.springai.actions.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive tests for the Query class.
 * Tests parsing, validation, and dialect transformation.
 */
class QueryTest {

	private SqlCatalog catalog;

	@BeforeEach
	void setUp() {
		catalog = new InMemorySqlCatalog()
				.addTable("orders", "Order table", "fact")
				.addColumn("orders", "id", "Primary key", "integer", new String[]{"pk"}, null)
				.addColumn("orders", "customer_id", "FK to customers", "integer", new String[]{"fk"}, null)
				.addColumn("orders", "amount", "Order amount", "decimal", null, null)
				.addColumn("orders", "status", "Order status", "varchar", null, null)
				.addTable("customers", "Customer table", "dimension")
				.addColumn("customers", "id", "Primary key", "integer", new String[]{"pk"}, null)
				.addColumn("customers", "name", "Customer name", "varchar", null, null);
	}

	@Nested
	@DisplayName("Parsing valid SQL")
	class ValidSqlParsing {

		@Test
		@DisplayName("parses simple SELECT")
		void parsesSimpleSelect() {
			String sql = "SELECT id, amount FROM orders";
			Query query = Query.fromSql(sql, catalog);

			assertThat(query).isNotNull();
			assertThat(query.sqlString()).containsIgnoringCase("SELECT");
			assertThat(query.sqlString()).containsIgnoringCase("id");
			assertThat(query.sqlString()).containsIgnoringCase("amount");
			assertThat(query.sqlString()).containsIgnoringCase("orders");
		}

		@Test
		@DisplayName("parses SELECT with WHERE clause")
		void parsesSelectWithWhere() {
			String sql = "SELECT id FROM orders WHERE status = 'COMPLETED'";
			Query query = Query.fromSql(sql, catalog);

			assertThat(query.sqlString()).containsIgnoringCase("WHERE");
			assertThat(query.sqlString()).contains("COMPLETED");
		}

		@Test
		@DisplayName("parses SELECT with JOIN")
		void parsesSelectWithJoin() {
			String sql = "SELECT o.id, c.name FROM orders o JOIN customers c ON o.customer_id = c.id";
			Query query = Query.fromSql(sql, catalog);

			assertThat(query.sqlString()).containsIgnoringCase("JOIN");
			assertThat(query.sqlString()).containsIgnoringCase("customers");
		}

		@Test
		@DisplayName("parses SELECT with GROUP BY and HAVING")
		void parsesSelectWithGroupByHaving() {
			String sql = "SELECT customer_id, SUM(amount) FROM orders GROUP BY customer_id HAVING SUM(amount) > 1000";
			Query query = Query.fromSql(sql, catalog);

			assertThat(query.sqlString()).containsIgnoringCase("GROUP BY");
			assertThat(query.sqlString()).containsIgnoringCase("HAVING");
		}

		@Test
		@DisplayName("parses SELECT with ORDER BY and LIMIT")
		void parsesSelectWithOrderByLimit() {
			String sql = "SELECT id, amount FROM orders ORDER BY amount DESC LIMIT 10";
			Query query = Query.fromSql(sql, catalog);

			assertThat(query.sqlString()).containsIgnoringCase("ORDER BY");
			assertThat(query.sqlString()).containsIgnoringCase("LIMIT");
		}

		@Test
		@DisplayName("parses SELECT with subquery")
		void parsesSelectWithSubquery() {
			String sql = "SELECT id FROM orders WHERE customer_id IN (SELECT id FROM customers WHERE name = 'John')";
			Query query = Query.fromSql(sql, catalog);

			assertThat(query.sqlString()).containsIgnoringCase("SELECT id FROM customers");
		}

		@Test
		@DisplayName("parses SELECT with aliases")
		void parsesSelectWithAliases() {
			String sql = "SELECT o.id AS order_id, o.amount AS total FROM orders o";
			Query query = Query.fromSql(sql, catalog);

			assertThat(query.sqlString()).containsIgnoringCase("order_id");
			assertThat(query.sqlString()).containsIgnoringCase("total");
		}
	}

	@Nested
	@DisplayName("Rejecting invalid SQL")
	class InvalidSqlRejection {

		@Test
		@DisplayName("rejects INSERT statement")
		void rejectsInsert() {
			String sql = "INSERT INTO orders (id, amount) VALUES (1, 100)";

			assertThatThrownBy(() -> Query.fromSql(sql, catalog))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("Only SELECT statements are allowed");
		}

		@Test
		@DisplayName("rejects UPDATE statement")
		void rejectsUpdate() {
			String sql = "UPDATE orders SET status = 'SHIPPED' WHERE id = 1";

			assertThatThrownBy(() -> Query.fromSql(sql, catalog))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("Only SELECT statements are allowed");
		}

		@Test
		@DisplayName("rejects DELETE statement")
		void rejectsDelete() {
			String sql = "DELETE FROM orders WHERE id = 1";

			assertThatThrownBy(() -> Query.fromSql(sql, catalog))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("Only SELECT statements are allowed");
		}

		@Test
		@DisplayName("rejects CREATE TABLE DDL")
		void rejectsCreateTable() {
			String sql = "CREATE TABLE new_table (id INT)";

			assertThatThrownBy(() -> Query.fromSql(sql, catalog))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("Only SELECT statements are allowed");
		}

		@Test
		@DisplayName("rejects DROP TABLE DDL")
		void rejectsDropTable() {
			String sql = "DROP TABLE orders";

			assertThatThrownBy(() -> Query.fromSql(sql, catalog))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("Only SELECT statements are allowed");
		}

		@Test
		@DisplayName("rejects malformed SQL")
		void rejectsMalformedSql() {
			String sql = "SELEC id FROM orders";  // typo

			assertThatThrownBy(() -> Query.fromSql(sql, catalog))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("Invalid SQL syntax");
		}

		@Test
		@DisplayName("rejects null SQL")
		void rejectsNullSql() {
			assertThatThrownBy(() -> Query.fromSql(null, catalog))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("cannot be null or blank");
		}

		@Test
		@DisplayName("rejects blank SQL")
		void rejectsBlankSql() {
			assertThatThrownBy(() -> Query.fromSql("   ", catalog))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("cannot be null or blank");
		}
	}

	@Nested
	@DisplayName("Schema validation")
	class SchemaValidation {

		@Test
		@DisplayName("accepts query referencing known tables")
		void acceptsKnownTables() {
			String sql = "SELECT id FROM orders";
			Query query = Query.fromSql(sql, catalog);

			assertThat(query).isNotNull();
		}

		@Test
		@DisplayName("rejects query referencing unknown table")
		void rejectsUnknownTable() {
			String sql = "SELECT id FROM unknown_table";

			assertThatThrownBy(() -> Query.fromSql(sql, catalog))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("Unknown table")
					.hasMessageContaining("unknown_table");
		}

		@Test
		@DisplayName("allows any table when catalog is null")
		void allowsAnyTableWithoutCatalog() {
			String sql = "SELECT id FROM any_table_name";
			Query query = Query.fromSql(sql, null);

			assertThat(query).isNotNull();
		}

		@Test
		@DisplayName("allows any table when catalog is empty")
		void allowsAnyTableWithEmptyCatalog() {
			SqlCatalog emptyCatalog = new InMemorySqlCatalog();
			String sql = "SELECT id FROM any_table_name";
			Query query = Query.fromSql(sql, emptyCatalog);

			assertThat(query).isNotNull();
		}
	}

	@Nested
	@DisplayName("Dialect transformation")
	class DialectTransformation {

		@Test
		@DisplayName("sqlString() returns ANSI by default")
		void sqlStringReturnsAnsiByDefault() {
			String sql = "SELECT id FROM orders WHERE status = 'ACTIVE'";
			Query query = Query.fromSql(sql, catalog);

			String result = query.sqlString();
			assertThat(result).isNotBlank();
			assertThat(result).containsIgnoringCase("SELECT");
		}

		@Test
		@DisplayName("sqlString(ANSI) returns valid SQL")
		void sqlStringAnsi() {
			String sql = "SELECT id, amount FROM orders ORDER BY amount DESC";
			Query query = Query.fromSql(sql, catalog);

			String result = query.sqlString(Query.Dialect.ANSI);
			assertThat(result).containsIgnoringCase("ORDER BY");
		}

		@Test
		@DisplayName("sqlString(POSTGRES) returns PostgreSQL-compatible SQL")
		void sqlStringPostgres() {
			String sql = "SELECT id FROM orders LIMIT 10";
			Query query = Query.fromSql(sql, catalog);

			String result = query.sqlString(Query.Dialect.POSTGRES);
			assertThat(result).containsIgnoringCase("LIMIT");
		}
	}

	@Nested
	@DisplayName("QueryFactory")
	class QueryFactoryTest {

		@Test
		@DisplayName("creates Query from SQL string")
		void createsQueryFromSql() {
			QueryFactory factory = new QueryFactory(catalog);
			Query query = factory.create("SELECT id FROM orders");

			assertThat(query).isNotNull();
			assertThat(query.sqlString()).containsIgnoringCase("orders");
		}

		@Test
		@DisplayName("factory without catalog skips schema validation")
		void factoryWithoutCatalog() {
			QueryFactory factory = new QueryFactory();
			Query query = factory.create("SELECT id FROM any_table");

			assertThat(query).isNotNull();
		}

		@Test
		@DisplayName("getType returns Query class")
		void getTypeReturnsQueryClass() {
			QueryFactory factory = new QueryFactory();
			assertThat(factory.getType()).isEqualTo(Query.class);
		}
	}
}

