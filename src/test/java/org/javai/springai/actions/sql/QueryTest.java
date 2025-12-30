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
		@DisplayName("sqlString() returns ANSI by default when catalog has no dialect set")
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
	@DisplayName("Catalog dialect configuration")
	class CatalogDialectConfiguration {

		@Test
		@DisplayName("sqlString() uses catalog dialect when configured")
		void usesDialectFromCatalog() {
			SqlCatalog postgresCatalog = new InMemorySqlCatalog()
					.withDialect(Query.Dialect.POSTGRES)
					.addTable("orders", "Order table")
					.addColumn("orders", "id", "Primary key", "integer", null, null);

			String sql = "SELECT id FROM orders LIMIT 10";
			Query query = Query.fromSql(sql, postgresCatalog);

			// sqlString() without args should use POSTGRES from catalog
			String result = query.sqlString();
			assertThat(result).containsIgnoringCase("LIMIT");
		}

		@Test
		@DisplayName("catalog defaults to ANSI dialect")
		void catalogDefaultsToAnsi() {
			SqlCatalog defaultCatalog = new InMemorySqlCatalog()
					.addTable("orders", "Order table");

			assertThat(defaultCatalog.dialect()).isEqualTo(Query.Dialect.ANSI);
		}

		@Test
		@DisplayName("withDialect allows fluent configuration")
		void withDialectIsFluent() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withDialect(Query.Dialect.POSTGRES)
					.addTable("orders", "Order table")
					.addColumn("orders", "id", "PK", "integer", null, null);

			assertThat(catalog.dialect()).isEqualTo(Query.Dialect.POSTGRES);
			assertThat(catalog.tables()).containsKey("orders");
		}

		@Test
		@DisplayName("sqlString() defaults to ANSI when no catalog")
		void defaultsToAnsiWithoutCatalog() {
			String sql = "SELECT id FROM any_table";
			Query query = Query.fromSql(sql);

			// Should not throw, should return valid SQL
			String result = query.sqlString();
			assertThat(result).containsIgnoringCase("SELECT");
		}

		@Test
		@DisplayName("explicit dialect overrides catalog dialect")
		void explicitDialectOverridesCatalog() {
			SqlCatalog postgresCatalog = new InMemorySqlCatalog()
					.withDialect(Query.Dialect.POSTGRES)
					.addTable("orders", "Order table")
					.addColumn("orders", "id", "Primary key", "integer", null, null);

			String sql = "SELECT id FROM orders";
			Query query = Query.fromSql(sql, postgresCatalog);

			// Explicit ANSI should work even though catalog is POSTGRES
			String ansiResult = query.sqlString(Query.Dialect.ANSI);
			assertThat(ansiResult).containsIgnoringCase("SELECT");
		}

		@Test
		@DisplayName("withDialect handles null gracefully")
		void withDialectHandlesNull() {
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.withDialect(null);

			// Should default to ANSI
			assertThat(catalog.dialect()).isEqualTo(Query.Dialect.ANSI);
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

	@Nested
	@DisplayName("Table synonym substitution")
	class SynonymSubstitution {

		private SqlCatalog catalogWithSynonyms;

		@BeforeEach
		void setUp() {
			catalogWithSynonyms = new InMemorySqlCatalog()
					.addTable("fct_orders", "Order transactions fact table", "fact")
					.withSynonyms("fct_orders", "orders", "order", "sales")
					.addColumn("fct_orders", "id", "Primary key", "integer", new String[]{"pk"}, null)
					.addColumn("fct_orders", "customer_id", "FK to customer", "integer", new String[]{"fk"}, null)
					.addColumn("fct_orders", "order_value", "Order value", "decimal", null, null)
					.addTable("dim_customer", "Customer dimension table", "dimension")
					.withSynonyms("dim_customer", "customers", "customer", "cust")
					.addColumn("dim_customer", "id", "Primary key", "integer", new String[]{"pk"}, null)
					.addColumn("dim_customer", "customer_name", "Customer name", "varchar", null, null);
		}

		@Test
		@DisplayName("substitutes single synonym in simple SELECT")
		void substitutesSingleSynonym() {
			String sql = "SELECT id FROM orders";
			Query query = Query.fromSql(sql, catalogWithSynonyms);

			assertThat(query.sqlString()).containsIgnoringCase("fct_orders");
			assertThat(query.sqlString()).doesNotContainIgnoringCase("FROM orders");
		}

		@Test
		@DisplayName("substitutes synonym with table alias")
		void substitutesSynonymWithAlias() {
			String sql = "SELECT o.id, o.order_value FROM orders o";
			Query query = Query.fromSql(sql, catalogWithSynonyms);

			assertThat(query.sqlString()).containsIgnoringCase("fct_orders o");
			// Check the full FROM clause to ensure "orders" was replaced with "fct_orders"
			assertThat(query.sqlString()).doesNotContain("FROM orders ");
		}

		@Test
		@DisplayName("substitutes synonyms in JOIN")
		void substitutesSynonymsInJoin() {
			String sql = "SELECT o.order_value, c.customer_name FROM orders o JOIN customers c ON o.customer_id = c.id";
			Query query = Query.fromSql(sql, catalogWithSynonyms);

			assertThat(query.sqlString()).containsIgnoringCase("fct_orders");
			assertThat(query.sqlString()).containsIgnoringCase("dim_customer");
			assertThat(query.sqlString()).containsIgnoringCase("JOIN");
		}

		@Test
		@DisplayName("case-insensitive synonym matching")
		void caseInsensitiveSynonymMatching() {
			String sql = "SELECT id FROM ORDERS";
			Query query = Query.fromSql(sql, catalogWithSynonyms);

			assertThat(query.sqlString()).containsIgnoringCase("fct_orders");
		}

		@Test
		@DisplayName("does not substitute partial matches")
		void doesNotSubstitutePartialMatches() {
			// Create catalog with a table named "order_items" to ensure "order" synonym doesn't replace it
			SqlCatalog catalogWithOrderItems = new InMemorySqlCatalog()
					.addTable("fct_orders", "Orders", "fact")
					.withSynonyms("fct_orders", "order")
					.addTable("order_items", "Order line items", "fact")
					.addColumn("fct_orders", "id", "PK", "integer", null, null)
					.addColumn("order_items", "id", "PK", "integer", null, null);

			String sql = "SELECT id FROM order_items";
			Query query = Query.fromSql(sql, catalogWithOrderItems);

			// Should NOT replace "order_items" because it's not a word boundary match
			assertThat(query.sqlString()).containsIgnoringCase("order_items");
			assertThat(query.sqlString()).doesNotContainIgnoringCase("fct_orders_items");
		}

		@Test
		@DisplayName("preserves canonical table names")
		void preservesCanonicalNames() {
			String sql = "SELECT id FROM fct_orders";
			Query query = Query.fromSql(sql, catalogWithSynonyms);

			assertThat(query.sqlString()).containsIgnoringCase("fct_orders");
		}

		@Test
		@DisplayName("multiple synonyms for same table all work")
		void multipleSynonymsAllWork() {
			// Test "sales" synonym
			Query q1 = Query.fromSql("SELECT id FROM sales", catalogWithSynonyms);
			assertThat(q1.sqlString()).containsIgnoringCase("fct_orders");

			// Test "order" synonym
			Query q2 = Query.fromSql("SELECT id FROM order", catalogWithSynonyms);
			assertThat(q2.sqlString()).containsIgnoringCase("fct_orders");

			// Test "cust" synonym for dim_customer
			Query q3 = Query.fromSql("SELECT id FROM cust", catalogWithSynonyms);
			assertThat(q3.sqlString()).containsIgnoringCase("dim_customer");
		}

		@Test
		@DisplayName("resolveTableName returns canonical name for synonym")
		void resolveTableNameReturnsCatalogName() {
			assertThat(catalogWithSynonyms.resolveTableName("orders"))
					.isPresent()
					.hasValue("fct_orders");

			assertThat(catalogWithSynonyms.resolveTableName("customers"))
					.isPresent()
					.hasValue("dim_customer");
		}

		@Test
		@DisplayName("resolveTableName returns canonical name directly")
		void resolveTableNameReturnsCanonicalName() {
			assertThat(catalogWithSynonyms.resolveTableName("fct_orders"))
					.isPresent()
					.hasValue("fct_orders");
		}

		@Test
		@DisplayName("resolveTableName returns empty for unknown name")
		void resolveTableNameReturnsEmptyForUnknown() {
			assertThat(catalogWithSynonyms.resolveTableName("unknown_table"))
					.isEmpty();
		}

		@Test
		@DisplayName("SqlTable.matchesName works for synonyms")
		void sqlTableMatchesNameWorks() {
			SqlCatalog.SqlTable ordersTable = catalogWithSynonyms.tables().get("fct_orders");

			assertThat(ordersTable.matchesName("fct_orders")).isTrue();
			assertThat(ordersTable.matchesName("orders")).isTrue();
			assertThat(ordersTable.matchesName("ORDERS")).isTrue();  // case-insensitive
			assertThat(ordersTable.matchesName("sales")).isTrue();
			assertThat(ordersTable.matchesName("unknown")).isFalse();
		}

		@Test
		@DisplayName("rejects duplicate synonym across tables")
		void rejectsDuplicateSynonymAcrossTables() {
			assertThatThrownBy(() -> new InMemorySqlCatalog()
					.addTable("fct_orders", "Orders", "fact")
					.withSynonyms("fct_orders", "orders")
					.addTable("order_items", "Order items", "fact")
					.withSynonyms("order_items", "orders"))  // duplicate!
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("Synonym 'orders' is already defined for table 'fct_orders'");
		}

		@Test
		@DisplayName("rejects synonym that matches existing table name")
		void rejectsSynonymMatchingTableName() {
			assertThatThrownBy(() -> new InMemorySqlCatalog()
					.addTable("orders", "Orders table", "fact")
					.addTable("fct_orders", "Orders fact", "fact")
					.withSynonyms("fct_orders", "orders"))  // conflicts with table name!
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("Synonym 'orders' conflicts with existing table name 'orders'");
		}

		@Test
		@DisplayName("allows same synonym to be added to same table multiple times")
		void allowsRedundantSynonymOnSameTable() {
			// This should not throw - just a no-op or adds duplicate to list
			InMemorySqlCatalog catalog = new InMemorySqlCatalog()
					.addTable("fct_orders", "Orders", "fact")
					.withSynonyms("fct_orders", "orders")
					.withSynonyms("fct_orders", "orders");  // same table, same synonym

			// Should work normally
			assertThat(catalog.resolveTableName("orders")).hasValue("fct_orders");
		}

		@Test
		@DisplayName("case-insensitive duplicate detection")
		void caseInsensitiveDuplicateDetection() {
			assertThatThrownBy(() -> new InMemorySqlCatalog()
					.addTable("fct_orders", "Orders", "fact")
					.withSynonyms("fct_orders", "orders")
					.addTable("order_items", "Order items", "fact")
					.withSynonyms("order_items", "ORDERS"))  // case-insensitive duplicate!
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("already defined");
		}
	}

	@Nested
	@DisplayName("Column synonym substitution and validation")
	class ColumnSynonymSubstitution {

		private SqlCatalog catalogWithColumnSynonyms;

		@BeforeEach
		void setUp() {
			// Enable column validation for these tests
			catalogWithColumnSynonyms = new InMemorySqlCatalog()
					.withValidateColumns(true)
					.addTable("fct_orders", "Order transactions", "fact")
					.withSynonyms("fct_orders", "orders")
					.addColumn("fct_orders", "id", "Primary key", "integer", new String[]{"pk"}, null)
					.addColumn("fct_orders", "customer_id", "FK to customer", "integer", new String[]{"fk"}, null)
					.addColumn("fct_orders", "order_value", "Order value", "decimal", null, null)
					.withColumnSynonyms("fct_orders", "order_value", "value", "amount", "total")
					.addTable("dim_customer", "Customer dimension", "dimension")
					.withSynonyms("dim_customer", "customers", "customer")
					.addColumn("dim_customer", "id", "Primary key", "integer", new String[]{"pk"}, null)
					.addColumn("dim_customer", "customer_name", "Customer full name", "varchar", null, null)
					.withColumnSynonyms("dim_customer", "customer_name", "name", "cust_name");
		}

		@Test
		@DisplayName("substitutes column synonym in SELECT clause")
		void substitutesColumnSynonymInSelect() {
			String sql = "SELECT name FROM dim_customer";
			Query query = Query.fromSql(sql, catalogWithColumnSynonyms);

			assertThat(query.sqlString()).containsIgnoringCase("customer_name");
			assertThat(query.sqlString()).doesNotContain("FROM name");
		}

		@Test
		@DisplayName("substitutes column synonym with table alias")
		void substitutesColumnSynonymWithAlias() {
			String sql = "SELECT c.name FROM dim_customer c";
			Query query = Query.fromSql(sql, catalogWithColumnSynonyms);

			assertThat(query.sqlString()).containsIgnoringCase("c.customer_name");
		}

		@Test
		@DisplayName("substitutes multiple column synonyms")
		void substitutesMultipleColumnSynonyms() {
			String sql = "SELECT o.value, c.name FROM fct_orders o JOIN dim_customer c ON o.customer_id = c.id";
			Query query = Query.fromSql(sql, catalogWithColumnSynonyms);

			assertThat(query.sqlString()).containsIgnoringCase("o.order_value");
			assertThat(query.sqlString()).containsIgnoringCase("c.customer_name");
		}

		@Test
		@DisplayName("validates columns exist in catalog")
		void validatesColumnsExist() {
			assertThatThrownBy(() -> 
					Query.fromSql("SELECT nonexistent_column FROM fct_orders", catalogWithColumnSynonyms))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("Unknown column 'nonexistent_column'");
		}

		@Test
		@DisplayName("validates qualified column references")
		void validatesQualifiedColumnReferences() {
			assertThatThrownBy(() -> 
					Query.fromSql("SELECT o.bad_column FROM fct_orders o", catalogWithColumnSynonyms))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("Unknown column 'bad_column'")
					.hasMessageContaining("fct_orders");
		}

		@Test
		@DisplayName("accepts column synonyms in validation")
		void acceptsColumnSynonymsInValidation() {
			// After substitution, "value" becomes "order_value" which exists
			Query query = Query.fromSql("SELECT value FROM fct_orders", catalogWithColumnSynonyms);
			assertThat(query.sqlString()).containsIgnoringCase("order_value");
		}

		@Test
		@DisplayName("SqlColumn.matchesName works for synonyms")
		void sqlColumnMatchesNameWorks() {
			SqlCatalog.SqlTable ordersTable = catalogWithColumnSynonyms.tables().get("fct_orders");
			SqlCatalog.SqlColumn valueColumn = ordersTable.findColumn("order_value").orElseThrow();

			assertThat(valueColumn.matchesName("order_value")).isTrue();
			assertThat(valueColumn.matchesName("value")).isTrue();
			assertThat(valueColumn.matchesName("VALUE")).isTrue();  // case-insensitive
			assertThat(valueColumn.matchesName("amount")).isTrue();
			assertThat(valueColumn.matchesName("unknown")).isFalse();
		}

		@Test
		@DisplayName("resolveColumnName returns canonical name for synonym")
		void resolveColumnNameReturnsCatalogName() {
			SqlCatalog.SqlTable ordersTable = catalogWithColumnSynonyms.tables().get("fct_orders");

			assertThat(ordersTable.resolveColumnName("value")).hasValue("order_value");
			assertThat(ordersTable.resolveColumnName("amount")).hasValue("order_value");
			assertThat(ordersTable.resolveColumnName("total")).hasValue("order_value");
		}

		@Test
		@DisplayName("rejects duplicate column synonym in same table")
		void rejectsDuplicateColumnSynonymInSameTable() {
			assertThatThrownBy(() -> new InMemorySqlCatalog()
					.addTable("orders", "Orders", "fact")
					.addColumn("orders", "value", "Value", "decimal", null, null)
					.withColumnSynonyms("orders", "value", "amount")
					.addColumn("orders", "total", "Total", "decimal", null, null)
					.withColumnSynonyms("orders", "total", "amount"))  // duplicate within same table!
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("already defined");
		}

		@Test
		@DisplayName("allows same synonym in different tables")
		void allowsSameSynonymInDifferentTables() {
			// "name" can be a synonym in both tables since columns are table-scoped
			SqlCatalog catalog = new InMemorySqlCatalog()
					.addTable("customers", "Customers", "dimension")
					.addColumn("customers", "customer_name", "Name", "varchar", null, null)
					.withColumnSynonyms("customers", "customer_name", "name")
					.addTable("products", "Products", "dimension")
					.addColumn("products", "product_name", "Name", "varchar", null, null)
					.withColumnSynonyms("products", "product_name", "name");

			assertThat(catalog.tables().get("customers").resolveColumnName("name")).hasValue("customer_name");
			assertThat(catalog.tables().get("products").resolveColumnName("name")).hasValue("product_name");
		}

		@Test
		@DisplayName("validates columns in WHERE clause")
		void validatesColumnsInWhereClause() {
			assertThatThrownBy(() -> 
					Query.fromSql("SELECT id FROM fct_orders WHERE bad_column = 1", catalogWithColumnSynonyms))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("Unknown column 'bad_column'");
		}

		@Test
		@DisplayName("validates columns in JOIN ON clause")
		void validatesColumnsInJoinOnClause() {
			assertThatThrownBy(() -> 
					Query.fromSql("SELECT o.id FROM fct_orders o JOIN dim_customer c ON o.bad_fk = c.id", 
							catalogWithColumnSynonyms))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("Unknown column 'bad_fk'");
		}

		@Test
		@DisplayName("column validation is disabled by default")
		void columnValidationDisabledByDefault() {
			SqlCatalog catalogWithoutValidation = new InMemorySqlCatalog()
					// Note: NOT calling withValidateColumns(true)
					.addTable("orders", "Orders", "fact")
					.addColumn("orders", "id", "PK", "integer", null, null);

			// Should NOT throw even though "nonexistent" column doesn't exist
			Query query = Query.fromSql("SELECT nonexistent FROM orders", catalogWithoutValidation);
			assertThat(query.sqlString()).containsIgnoringCase("nonexistent");
		}

		@Test
		@DisplayName("column validation can be enabled per catalog")
		void columnValidationCanBeEnabled() {
			SqlCatalog catalogWithValidation = new InMemorySqlCatalog()
					.withValidateColumns(true)
					.addTable("orders", "Orders", "fact")
					.addColumn("orders", "id", "PK", "integer", null, null);

			// Should throw because validation is enabled
			assertThatThrownBy(() -> 
					Query.fromSql("SELECT nonexistent FROM orders", catalogWithValidation))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("Unknown column 'nonexistent'");
		}

		@Test
		@DisplayName("AST-based substitution does not affect SQL keywords")
		void astSubstitutionDoesNotAffectKeywords() {
			// Create catalog with "order" as a table synonym - this could match ORDER BY
			SqlCatalog catalog = new InMemorySqlCatalog()
					.addTable("fct_orders", "Orders", "fact")
					.withSynonyms("fct_orders", "order")  // Dangerous if using string replacement!
					.addColumn("fct_orders", "id", "PK", "integer", null, null)
					.addColumn("fct_orders", "amount", "Amount", "decimal", null, null);

			// This SQL has "ORDER BY" which should NOT be affected by the "order" table synonym
			String sql = "SELECT id, amount FROM order ORDER BY amount DESC";
			Query query = Query.fromSql(sql, catalog);

			// The table "order" should be replaced with "fct_orders"
			// but "ORDER BY" should remain as "ORDER BY"
			String result = query.sqlString();
			assertThat(result).containsIgnoringCase("FROM fct_orders");
			assertThat(result).containsIgnoringCase("ORDER BY");
			assertThat(result).doesNotContain("fct_orders BY");  // This would happen with naive string replace
		}

		@Test
		@DisplayName("AST-based substitution does not affect string literals")
		void astSubstitutionDoesNotAffectStringLiterals() {
			SqlCatalog catalog = new InMemorySqlCatalog()
					.addTable("fct_orders", "Orders", "fact")
					.withSynonyms("fct_orders", "orders")
					.addColumn("fct_orders", "id", "PK", "integer", null, null)
					.addColumn("fct_orders", "status", "Status", "varchar", null, null);

			// The string literal 'orders pending' should NOT be affected
			String sql = "SELECT id FROM orders WHERE status = 'orders pending'";
			Query query = Query.fromSql(sql, catalog);

			String result = query.sqlString();
			assertThat(result).containsIgnoringCase("FROM fct_orders");
			assertThat(result).contains("'orders pending'");  // String literal preserved
		}
	}
}

