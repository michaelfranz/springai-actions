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
				.addColumn("orders", "order_date", "Order date", "date", null, null)
				.addColumn("orders", "created_at", "Creation timestamp", "timestamp", null, null)
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
	@DisplayName("Error handling - clear error messages")
	class ErrorHandling {

		@Test
		@DisplayName("error message lists available tables when unknown table referenced")
		void errorListsAvailableTables() {
			assertThatThrownBy(() -> Query.fromSql("SELECT id FROM bad_table", catalog))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("Unknown table: bad_table")
					.hasMessageContaining("orders")
					.hasMessageContaining("customers");
		}

		@Test
		@DisplayName("error message shows column context when unknown column with validation enabled")
		void errorShowsColumnContext() {
			SqlCatalog validatingCatalog = new InMemorySqlCatalog()
					.withValidateColumns(true)
					.addTable("orders", "Orders", "fact")
					.addColumn("orders", "id", "PK", "integer", null, null)
					.addColumn("orders", "amount", "Amount", "decimal", null, null);

			assertThatThrownBy(() -> Query.fromSql("SELECT bad_column FROM orders", validatingCatalog))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("Unknown column 'bad_column'")
					.hasMessageContaining("orders");
		}

		@Test
		@DisplayName("error message lists available columns when unknown column referenced")
		void errorListsAvailableColumns() {
			SqlCatalog validatingCatalog = new InMemorySqlCatalog()
					.withValidateColumns(true)
					.addTable("orders", "Orders", "fact")
					.addColumn("orders", "id", "PK", "integer", null, null)
					.addColumn("orders", "amount", "Amount", "decimal", null, null);

			assertThatThrownBy(() -> Query.fromSql("SELECT o.bad_column FROM orders o", validatingCatalog))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("Available columns")
					.hasMessageContaining("id")
					.hasMessageContaining("amount");
		}

		@Test
		@DisplayName("error shows syntax position for malformed SQL")
		void errorShowsSyntaxPosition() {
			assertThatThrownBy(() -> Query.fromSql("SELECT FROM orders", catalog))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("Invalid SQL syntax");
		}

		@Test
		@DisplayName("error distinguishes between table not found and column not found")
		void errorDistinguishesTableVsColumn() {
			SqlCatalog validatingCatalog = new InMemorySqlCatalog()
					.withValidateColumns(true)
					.addTable("orders", "Orders", "fact")
					.addColumn("orders", "id", "PK", "integer", null, null);

			// Unknown table
			assertThatThrownBy(() -> Query.fromSql("SELECT id FROM bad_table", validatingCatalog))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("Unknown table");

			// Unknown column
			assertThatThrownBy(() -> Query.fromSql("SELECT bad_col FROM orders", validatingCatalog))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("Unknown column");
		}

		@Test
		@DisplayName("error for unknown table in JOIN")
		void errorForUnknownTableInJoin() {
			assertThatThrownBy(() -> 
					Query.fromSql("SELECT o.id FROM orders o JOIN unknown_table u ON o.id = u.id", catalog))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("Unknown table")
					.hasMessageContaining("unknown_table");
		}

		@Test
		@DisplayName("error for unknown table in subquery")
		void errorForUnknownTableInSubquery() {
			assertThatThrownBy(() -> 
					Query.fromSql("SELECT id FROM orders WHERE customer_id IN (SELECT id FROM unknown_table)", catalog))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("Unknown table")
					.hasMessageContaining("unknown_table");
		}

		@Test
		@DisplayName("error message is clear for null SQL")
		void errorForNullSql() {
			assertThatThrownBy(() -> Query.fromSql(null, catalog))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("cannot be null or blank");
		}

		@Test
		@DisplayName("error message is clear for blank SQL")
		void errorForBlankSql() {
			assertThatThrownBy(() -> Query.fromSql("   ", catalog))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("cannot be null or blank");
		}

		@Test
		@DisplayName("error message is clear for non-SELECT statement")
		void errorForNonSelect() {
			assertThatThrownBy(() -> Query.fromSql("INSERT INTO orders VALUES (1)", catalog))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("Only SELECT statements are allowed")
					.hasMessageContaining("Insert");  // Shows the actual type
		}
	}

	@Nested
	@DisplayName("Large schema handling")
	class LargeSchemaHandling {

		private SqlCatalog largeSchema;

		@BeforeEach
		void setUp() {
			// Create a realistic data warehouse schema with 15+ tables and 100+ columns
			InMemorySqlCatalog catalog = new InMemorySqlCatalog();

			// Fact tables (3)
			catalog.addTable("fct_sales", "Sales transactions fact table", "fact")
					.addColumn("fct_sales", "sale_id", "Sale PK", "bigint", new String[]{"pk"}, null)
					.addColumn("fct_sales", "customer_id", "FK to dim_customer", "integer", new String[]{"fk:dim_customer.id"}, null)
					.addColumn("fct_sales", "product_id", "FK to dim_product", "integer", new String[]{"fk:dim_product.id"}, null)
					.addColumn("fct_sales", "store_id", "FK to dim_store", "integer", new String[]{"fk:dim_store.id"}, null)
					.addColumn("fct_sales", "date_id", "FK to dim_date", "integer", new String[]{"fk:dim_date.id"}, null)
					.addColumn("fct_sales", "time_id", "FK to dim_time", "integer", new String[]{"fk:dim_time.id"}, null)
					.addColumn("fct_sales", "promo_id", "FK to dim_promotion", "integer", new String[]{"fk:dim_promotion.id"}, null)
					.addColumn("fct_sales", "quantity", "Units sold", "integer", null, null)
					.addColumn("fct_sales", "unit_price", "Price per unit", "decimal", null, null)
					.addColumn("fct_sales", "discount_amount", "Discount applied", "decimal", null, null)
					.addColumn("fct_sales", "net_amount", "Net sale amount", "decimal", null, null)
					.addColumn("fct_sales", "tax_amount", "Tax amount", "decimal", null, null)
					.addColumn("fct_sales", "gross_amount", "Gross sale amount", "decimal", null, null);

			catalog.addTable("fct_inventory", "Inventory snapshot fact table", "fact")
					.addColumn("fct_inventory", "snapshot_id", "Snapshot PK", "bigint", new String[]{"pk"}, null)
					.addColumn("fct_inventory", "product_id", "FK to dim_product", "integer", new String[]{"fk:dim_product.id"}, null)
					.addColumn("fct_inventory", "store_id", "FK to dim_store", "integer", new String[]{"fk:dim_store.id"}, null)
					.addColumn("fct_inventory", "date_id", "FK to dim_date", "integer", new String[]{"fk:dim_date.id"}, null)
					.addColumn("fct_inventory", "quantity_on_hand", "Units in stock", "integer", null, null)
					.addColumn("fct_inventory", "quantity_on_order", "Units on order", "integer", null, null)
					.addColumn("fct_inventory", "reorder_point", "Reorder trigger level", "integer", null, null);

			catalog.addTable("fct_orders", "Order transactions fact table", "fact")
					.addColumn("fct_orders", "order_id", "Order PK", "bigint", new String[]{"pk"}, null)
					.addColumn("fct_orders", "customer_id", "FK to dim_customer", "integer", new String[]{"fk:dim_customer.id"}, null)
					.addColumn("fct_orders", "date_id", "FK to dim_date", "integer", new String[]{"fk:dim_date.id"}, null)
					.addColumn("fct_orders", "shipping_id", "FK to dim_shipping", "integer", new String[]{"fk:dim_shipping.id"}, null)
					.addColumn("fct_orders", "order_total", "Order total amount", "decimal", null, null)
					.addColumn("fct_orders", "shipping_cost", "Shipping cost", "decimal", null, null)
					.addColumn("fct_orders", "order_status", "Current order status", "varchar", null, null);

			// Dimension tables (12+)
			catalog.addTable("dim_customer", "Customer dimension", "dimension")
					.addColumn("dim_customer", "id", "Customer PK", "integer", new String[]{"pk"}, null)
					.addColumn("dim_customer", "customer_key", "Business key", "varchar", null, new String[]{"UNIQUE"})
					.addColumn("dim_customer", "first_name", "First name", "varchar", null, null)
					.addColumn("dim_customer", "last_name", "Last name", "varchar", null, null)
					.addColumn("dim_customer", "email", "Email address", "varchar", null, null)
					.addColumn("dim_customer", "phone", "Phone number", "varchar", null, null)
					.addColumn("dim_customer", "address_line1", "Address line 1", "varchar", null, null)
					.addColumn("dim_customer", "address_line2", "Address line 2", "varchar", null, null)
					.addColumn("dim_customer", "city", "City", "varchar", null, null)
					.addColumn("dim_customer", "state", "State/Province", "varchar", null, null)
					.addColumn("dim_customer", "postal_code", "Postal code", "varchar", null, null)
					.addColumn("dim_customer", "country", "Country", "varchar", null, null)
					.addColumn("dim_customer", "customer_segment", "Customer segment", "varchar", null, null)
					.addColumn("dim_customer", "registration_date", "Registration date", "date", null, null);

			catalog.addTable("dim_product", "Product dimension", "dimension")
					.addColumn("dim_product", "id", "Product PK", "integer", new String[]{"pk"}, null)
					.addColumn("dim_product", "product_key", "Business key (SKU)", "varchar", null, new String[]{"UNIQUE"})
					.addColumn("dim_product", "product_name", "Product name", "varchar", null, null)
					.addColumn("dim_product", "product_description", "Description", "text", null, null)
					.addColumn("dim_product", "category_id", "FK to dim_category", "integer", new String[]{"fk:dim_category.id"}, null)
					.addColumn("dim_product", "brand", "Brand name", "varchar", null, null)
					.addColumn("dim_product", "unit_cost", "Unit cost", "decimal", null, null)
					.addColumn("dim_product", "unit_price", "Suggested retail price", "decimal", null, null)
					.addColumn("dim_product", "weight", "Product weight", "decimal", null, null)
					.addColumn("dim_product", "is_active", "Active flag", "boolean", null, null);

			catalog.addTable("dim_category", "Product category dimension", "dimension")
					.addColumn("dim_category", "id", "Category PK", "integer", new String[]{"pk"}, null)
					.addColumn("dim_category", "category_name", "Category name", "varchar", null, null)
					.addColumn("dim_category", "parent_category_id", "Parent category", "integer", new String[]{"fk:dim_category.id"}, null)
					.addColumn("dim_category", "category_level", "Hierarchy level", "integer", null, null)
					.addColumn("dim_category", "category_path", "Full category path", "varchar", null, null);

			catalog.addTable("dim_store", "Store dimension", "dimension")
					.addColumn("dim_store", "id", "Store PK", "integer", new String[]{"pk"}, null)
					.addColumn("dim_store", "store_name", "Store name", "varchar", null, null)
					.addColumn("dim_store", "store_type", "Store type", "varchar", null, null)
					.addColumn("dim_store", "address", "Store address", "varchar", null, null)
					.addColumn("dim_store", "city", "City", "varchar", null, null)
					.addColumn("dim_store", "state", "State", "varchar", null, null)
					.addColumn("dim_store", "country", "Country", "varchar", null, null)
					.addColumn("dim_store", "region", "Geographic region", "varchar", null, null)
					.addColumn("dim_store", "manager_name", "Store manager", "varchar", null, null)
					.addColumn("dim_store", "open_date", "Store opening date", "date", null, null);

			catalog.addTable("dim_date", "Date dimension", "dimension")
					.addColumn("dim_date", "id", "Date PK (YYYYMMDD)", "integer", new String[]{"pk"}, null)
					.addColumn("dim_date", "full_date", "Full date", "date", null, null)
					.addColumn("dim_date", "day_of_week", "Day of week (1-7)", "integer", null, null)
					.addColumn("dim_date", "day_name", "Day name", "varchar", null, null)
					.addColumn("dim_date", "day_of_month", "Day of month", "integer", null, null)
					.addColumn("dim_date", "day_of_year", "Day of year", "integer", null, null)
					.addColumn("dim_date", "week_of_year", "Week number", "integer", null, null)
					.addColumn("dim_date", "month", "Month number", "integer", null, null)
					.addColumn("dim_date", "month_name", "Month name", "varchar", null, null)
					.addColumn("dim_date", "quarter", "Quarter (1-4)", "integer", null, null)
					.addColumn("dim_date", "year", "Year", "integer", null, null)
					.addColumn("dim_date", "is_weekend", "Weekend flag", "boolean", null, null)
					.addColumn("dim_date", "is_holiday", "Holiday flag", "boolean", null, null)
					.addColumn("dim_date", "fiscal_year", "Fiscal year", "integer", null, null)
					.addColumn("dim_date", "fiscal_quarter", "Fiscal quarter", "integer", null, null);

			catalog.addTable("dim_time", "Time of day dimension", "dimension")
					.addColumn("dim_time", "id", "Time PK", "integer", new String[]{"pk"}, null)
					.addColumn("dim_time", "hour", "Hour (0-23)", "integer", null, null)
					.addColumn("dim_time", "minute", "Minute (0-59)", "integer", null, null)
					.addColumn("dim_time", "time_of_day", "Time of day category", "varchar", null, null)
					.addColumn("dim_time", "am_pm", "AM/PM indicator", "varchar", null, null);

			catalog.addTable("dim_promotion", "Promotion dimension", "dimension")
					.addColumn("dim_promotion", "id", "Promotion PK", "integer", new String[]{"pk"}, null)
					.addColumn("dim_promotion", "promo_name", "Promotion name", "varchar", null, null)
					.addColumn("dim_promotion", "promo_type", "Promotion type", "varchar", null, null)
					.addColumn("dim_promotion", "discount_percent", "Discount percentage", "decimal", null, null)
					.addColumn("dim_promotion", "start_date", "Start date", "date", null, null)
					.addColumn("dim_promotion", "end_date", "End date", "date", null, null)
					.addColumn("dim_promotion", "is_active", "Active flag", "boolean", null, null);

			catalog.addTable("dim_shipping", "Shipping method dimension", "dimension")
					.addColumn("dim_shipping", "id", "Shipping PK", "integer", new String[]{"pk"}, null)
					.addColumn("dim_shipping", "shipping_method", "Shipping method", "varchar", null, null)
					.addColumn("dim_shipping", "carrier", "Carrier name", "varchar", null, null)
					.addColumn("dim_shipping", "estimated_days", "Estimated delivery days", "integer", null, null)
					.addColumn("dim_shipping", "base_cost", "Base shipping cost", "decimal", null, null);

			catalog.addTable("dim_employee", "Employee dimension", "dimension")
					.addColumn("dim_employee", "id", "Employee PK", "integer", new String[]{"pk"}, null)
					.addColumn("dim_employee", "employee_key", "Business key", "varchar", null, null)
					.addColumn("dim_employee", "first_name", "First name", "varchar", null, null)
					.addColumn("dim_employee", "last_name", "Last name", "varchar", null, null)
					.addColumn("dim_employee", "title", "Job title", "varchar", null, null)
					.addColumn("dim_employee", "department", "Department", "varchar", null, null)
					.addColumn("dim_employee", "hire_date", "Hire date", "date", null, null)
					.addColumn("dim_employee", "manager_id", "Manager FK", "integer", new String[]{"fk:dim_employee.id"}, null);

			catalog.addTable("dim_supplier", "Supplier dimension", "dimension")
					.addColumn("dim_supplier", "id", "Supplier PK", "integer", new String[]{"pk"}, null)
					.addColumn("dim_supplier", "supplier_name", "Supplier name", "varchar", null, null)
					.addColumn("dim_supplier", "contact_name", "Contact person", "varchar", null, null)
					.addColumn("dim_supplier", "email", "Contact email", "varchar", null, null)
					.addColumn("dim_supplier", "phone", "Phone number", "varchar", null, null)
					.addColumn("dim_supplier", "country", "Country", "varchar", null, null)
					.addColumn("dim_supplier", "payment_terms", "Payment terms", "varchar", null, null);

			catalog.addTable("dim_geography", "Geography dimension", "dimension")
					.addColumn("dim_geography", "id", "Geography PK", "integer", new String[]{"pk"}, null)
					.addColumn("dim_geography", "country", "Country", "varchar", null, null)
					.addColumn("dim_geography", "region", "Region", "varchar", null, null)
					.addColumn("dim_geography", "state", "State/Province", "varchar", null, null)
					.addColumn("dim_geography", "city", "City", "varchar", null, null)
					.addColumn("dim_geography", "postal_code", "Postal code", "varchar", null, null)
					.addColumn("dim_geography", "latitude", "Latitude", "decimal", null, null)
					.addColumn("dim_geography", "longitude", "Longitude", "decimal", null, null);

			// Bridge tables
			catalog.addTable("bridge_order_product", "Order-Product bridge table", "bridge")
					.addColumn("bridge_order_product", "order_id", "FK to fct_orders", "bigint", new String[]{"fk:fct_orders.order_id"}, null)
					.addColumn("bridge_order_product", "product_id", "FK to dim_product", "integer", new String[]{"fk:dim_product.id"}, null)
					.addColumn("bridge_order_product", "quantity", "Quantity ordered", "integer", null, null)
					.addColumn("bridge_order_product", "line_total", "Line total", "decimal", null, null);

			largeSchema = catalog;
		}

		@Test
		@DisplayName("catalog has 15+ tables")
		void catalogHas15PlusTables() {
			assertThat(largeSchema.tables().size()).isGreaterThanOrEqualTo(15);
		}

		@Test
		@DisplayName("catalog has 100+ columns total")
		void catalogHas100PlusColumns() {
			int totalColumns = largeSchema.tables().values().stream()
					.mapToInt(t -> t.columns().size())
					.sum();
			assertThat(totalColumns).isGreaterThanOrEqualTo(100);
		}

		@Test
		@DisplayName("parses query spanning multiple dimension tables")
		void parsesQuerySpanningMultipleDimensions() {
			String sql = """
					SELECT 
					    d.full_date,
					    c.first_name, c.last_name,
					    p.product_name,
					    s.store_name,
					    f.quantity, f.net_amount
					FROM fct_sales f
					JOIN dim_date d ON f.date_id = d.id
					JOIN dim_customer c ON f.customer_id = c.id
					JOIN dim_product p ON f.product_id = p.id
					JOIN dim_store s ON f.store_id = s.id
					WHERE d.year = 2024
					""";
			Query query = Query.fromSql(sql, largeSchema);

			assertThat(query.sqlString()).containsIgnoringCase("fct_sales");
			assertThat(query.sqlString()).containsIgnoringCase("dim_date");
			assertThat(query.sqlString()).containsIgnoringCase("dim_customer");
			assertThat(query.sqlString()).containsIgnoringCase("dim_product");
			assertThat(query.sqlString()).containsIgnoringCase("dim_store");
		}

		@Test
		@DisplayName("parses complex aggregation across large schema")
		void parsesComplexAggregationAcrossLargeSchema() {
			String sql = """
					SELECT 
					    d.year, d.quarter, d.month_name,
					    s.region,
					    cat.category_name,
					    SUM(f.net_amount) AS total_sales,
					    COUNT(DISTINCT f.customer_id) AS unique_customers,
					    AVG(f.quantity) AS avg_quantity
					FROM fct_sales f
					JOIN dim_date d ON f.date_id = d.id
					JOIN dim_store s ON f.store_id = s.id
					JOIN dim_product p ON f.product_id = p.id
					JOIN dim_category cat ON p.category_id = cat.id
					WHERE d.year >= 2023
					GROUP BY d.year, d.quarter, d.month_name, s.region, cat.category_name
					HAVING SUM(f.net_amount) > 10000
					ORDER BY d.year, d.quarter, total_sales DESC
					""";
			Query query = Query.fromSql(sql, largeSchema);

			assertThat(query.sqlString()).containsIgnoringCase("GROUP BY");
			assertThat(query.sqlString()).containsIgnoringCase("HAVING");
			assertThat(query.sqlString()).containsIgnoringCase("ORDER BY");
		}

		@Test
		@DisplayName("validates unknown table in large schema")
		void validatesUnknownTableInLargeSchema() {
			String sql = "SELECT * FROM nonexistent_table";
			
			assertThatThrownBy(() -> Query.fromSql(sql, largeSchema))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("Unknown table");
		}

		@Test
		@DisplayName("parses query with category hierarchy (self-reference)")
		void parsesQueryWithCategoryHierarchy() {
			String sql = """
					SELECT 
					    parent.category_name AS parent_category,
					    child.category_name AS child_category
					FROM dim_category child
					LEFT JOIN dim_category parent ON child.parent_category_id = parent.id
					WHERE child.category_level = 2
					""";
			Query query = Query.fromSql(sql, largeSchema);

			assertThat(query.sqlString()).containsIgnoringCase("parent.category_name");
			assertThat(query.sqlString()).containsIgnoringCase("child.category_name");
		}

		@Test
		@DisplayName("parses inventory and sales correlation query")
		void parsesInventorySalesCorrelationQuery() {
			String sql = """
					SELECT 
					    p.product_name,
					    s.store_name,
					    inv.quantity_on_hand,
					    COALESCE(SUM(sales.quantity), 0) AS total_sold
					FROM fct_inventory inv
					JOIN dim_product p ON inv.product_id = p.id
					JOIN dim_store s ON inv.store_id = s.id
					LEFT JOIN fct_sales sales ON inv.product_id = sales.product_id 
					    AND inv.store_id = sales.store_id
					GROUP BY p.product_name, s.store_name, inv.quantity_on_hand
					ORDER BY inv.quantity_on_hand DESC
					""";
			Query query = Query.fromSql(sql, largeSchema);

			assertThat(query.sqlString()).containsIgnoringCase("fct_inventory");
			assertThat(query.sqlString()).containsIgnoringCase("fct_sales");
			assertThat(query.sqlString()).containsIgnoringCase("COALESCE");
		}
	}

	@Nested
	@DisplayName("Complex constraints")
	class ComplexConstraints {

		private SqlCatalog catalogWithCompositeKeys;

		@BeforeEach
		void setUp() {
			// Create catalog with composite keys and complex constraints
			catalogWithCompositeKeys = new InMemorySqlCatalog()
					// Order line items with composite PK (order_id, line_number)
					.addTable("order_items", "Order line items", "fact")
					.addColumn("order_items", "order_id", "FK to orders", "integer", 
							new String[]{"pk", "fk:orders.id"}, new String[]{"NOT NULL"})
					.addColumn("order_items", "line_number", "Line number within order", "integer", 
							new String[]{"pk"}, new String[]{"NOT NULL"})
					.addColumn("order_items", "product_id", "FK to products", "integer", 
							new String[]{"fk:products.id"}, null)
					.addColumn("order_items", "quantity", "Quantity ordered", "integer", 
							null, new String[]{"CHECK (quantity > 0)"})
					.addColumn("order_items", "unit_price", "Price per unit", "decimal", 
							null, new String[]{"CHECK (unit_price >= 0)"})
					// Orders table
					.addTable("orders", "Order header", "fact")
					.addColumn("orders", "id", "Order PK", "integer", new String[]{"pk"}, null)
					.addColumn("orders", "customer_id", "FK to customers", "integer", 
							new String[]{"fk:customers.id"}, null)
					.addColumn("orders", "order_date", "Order date", "date", null, null)
					// Customers with unique constraint
					.addTable("customers", "Customer table", "dimension")
					.addColumn("customers", "id", "Customer PK", "integer", new String[]{"pk"}, null)
					.addColumn("customers", "email", "Email address", "varchar", 
							null, new String[]{"UNIQUE", "NOT NULL"})
					.addColumn("customers", "name", "Customer name", "varchar", null, null)
					// Products table
					.addTable("products", "Product table", "dimension")
					.addColumn("products", "id", "Product PK", "integer", new String[]{"pk"}, null)
					.addColumn("products", "sku", "Stock keeping unit", "varchar", 
							null, new String[]{"UNIQUE"})
					.addColumn("products", "name", "Product name", "varchar", null, null);
		}

		@Test
		@DisplayName("parses query with composite key JOIN")
		void parsesQueryWithCompositeKeyJoin() {
			// Join on both parts of composite key
			String sql = """
					SELECT oi.order_id, oi.line_number, oi.quantity
					FROM order_items oi
					WHERE oi.order_id = 1 AND oi.line_number = 1
					""";
			Query query = Query.fromSql(sql, catalogWithCompositeKeys);

			assertThat(query.sqlString()).containsIgnoringCase("order_id");
			assertThat(query.sqlString()).containsIgnoringCase("line_number");
		}

		@Test
		@DisplayName("parses multi-table join following FK chain")
		void parsesMultiTableJoinFollowingFkChain() {
			// orders -> order_items -> products
			String sql = """
					SELECT o.id AS order_id, oi.line_number, p.name AS product_name, oi.quantity
					FROM orders o
					JOIN order_items oi ON o.id = oi.order_id
					JOIN products p ON oi.product_id = p.id
					WHERE o.id = 100
					""";
			Query query = Query.fromSql(sql, catalogWithCompositeKeys);

			assertThat(query.sqlString()).containsIgnoringCase("JOIN order_items oi ON o.id = oi.order_id");
			assertThat(query.sqlString()).containsIgnoringCase("JOIN products p ON oi.product_id = p.id");
		}

		@Test
		@DisplayName("parses aggregation across composite key")
		void parsesAggregationAcrossCompositeKey() {
			String sql = """
					SELECT order_id, COUNT(line_number) AS line_count, SUM(quantity * unit_price) AS total
					FROM order_items
					GROUP BY order_id
					HAVING SUM(quantity * unit_price) > 100
					""";
			Query query = Query.fromSql(sql, catalogWithCompositeKeys);

			assertThat(query.sqlString()).containsIgnoringCase("GROUP BY order_id");
			assertThat(query.sqlString()).containsIgnoringCase("SUM(quantity * unit_price)");
		}

		@Test
		@DisplayName("catalog correctly stores composite key tags")
		void catalogStoresCompositeKeyTags() {
			SqlCatalog.SqlTable orderItems = catalogWithCompositeKeys.tables().get("order_items");
			
			// Both order_id and line_number should have pk tag
			SqlCatalog.SqlColumn orderId = orderItems.findColumn("order_id").orElseThrow();
			SqlCatalog.SqlColumn lineNumber = orderItems.findColumn("line_number").orElseThrow();
			
			assertThat(orderId.tags()).contains("pk");
			assertThat(orderId.tags()).contains("fk:orders.id");
			assertThat(lineNumber.tags()).contains("pk");
		}

		@Test
		@DisplayName("catalog correctly stores check constraints")
		void catalogStoresCheckConstraints() {
			SqlCatalog.SqlTable orderItems = catalogWithCompositeKeys.tables().get("order_items");
			
			SqlCatalog.SqlColumn quantity = orderItems.findColumn("quantity").orElseThrow();
			SqlCatalog.SqlColumn unitPrice = orderItems.findColumn("unit_price").orElseThrow();
			
			assertThat(quantity.constraints()).contains("CHECK (quantity > 0)");
			assertThat(unitPrice.constraints()).contains("CHECK (unit_price >= 0)");
		}

		@Test
		@DisplayName("catalog correctly stores unique constraints")
		void catalogStoresUniqueConstraints() {
			SqlCatalog.SqlTable customers = catalogWithCompositeKeys.tables().get("customers");
			SqlCatalog.SqlColumn email = customers.findColumn("email").orElseThrow();
			
			assertThat(email.constraints()).contains("UNIQUE");
			assertThat(email.constraints()).contains("NOT NULL");
		}

		@Test
		@DisplayName("parses query referencing unique constrained column")
		void parsesQueryReferencingUniqueColumn() {
			String sql = "SELECT id, name FROM customers WHERE email = 'test@example.com'";
			Query query = Query.fromSql(sql, catalogWithCompositeKeys);

			assertThat(query.sqlString()).containsIgnoringCase("email");
		}

		@Test
		@DisplayName("parses self-referencing query pattern")
		void parsesSelfReferencingQueryPattern() {
			// Common pattern: find order items in same order
			String sql = """
					SELECT oi1.line_number, oi2.line_number
					FROM order_items oi1
					JOIN order_items oi2 ON oi1.order_id = oi2.order_id AND oi1.line_number < oi2.line_number
					""";
			Query query = Query.fromSql(sql, catalogWithCompositeKeys);

			assertThat(query.sqlString()).containsIgnoringCase("oi1.order_id = oi2.order_id");
		}
	}

	@Nested
	@DisplayName("Ambiguous column handling")
	class AmbiguousColumnHandling {

		private SqlCatalog catalogWithAmbiguousColumns;

		@BeforeEach
		void setUp() {
			// Create catalog where multiple tables share column names (id, name, status)
			catalogWithAmbiguousColumns = new InMemorySqlCatalog()
					.addTable("orders", "Order table", "fact")
					.addColumn("orders", "id", "Order PK", "integer", new String[]{"pk"}, null)
					.addColumn("orders", "customer_id", "FK to customers", "integer", new String[]{"fk"}, null)
					.addColumn("orders", "status", "Order status", "varchar", null, null)
					.addColumn("orders", "name", "Order name/description", "varchar", null, null)
					.addTable("customers", "Customer table", "dimension")
					.addColumn("customers", "id", "Customer PK", "integer", new String[]{"pk"}, null)
					.addColumn("customers", "name", "Customer name", "varchar", null, null)
					.addColumn("customers", "status", "Customer status", "varchar", null, null)
					.addTable("products", "Product table", "dimension")
					.addColumn("products", "id", "Product PK", "integer", new String[]{"pk"}, null)
					.addColumn("products", "name", "Product name", "varchar", null, null);
		}

		@Test
		@DisplayName("allows unqualified column when unambiguous in single table query")
		void allowsUnqualifiedColumnInSingleTable() {
			String sql = "SELECT id, name, status FROM orders";
			Query query = Query.fromSql(sql, catalogWithAmbiguousColumns);

			assertThat(query.sqlString()).containsIgnoringCase("id");
			assertThat(query.sqlString()).containsIgnoringCase("name");
		}

		@Test
		@DisplayName("parses qualified columns in JOIN to disambiguate")
		void parsesQualifiedColumnsInJoin() {
			String sql = "SELECT o.id, o.name, c.id, c.name FROM orders o JOIN customers c ON o.customer_id = c.id";
			Query query = Query.fromSql(sql, catalogWithAmbiguousColumns);

			assertThat(query.sqlString()).containsIgnoringCase("o.id");
			assertThat(query.sqlString()).containsIgnoringCase("c.id");
			assertThat(query.sqlString()).containsIgnoringCase("o.name");
			assertThat(query.sqlString()).containsIgnoringCase("c.name");
		}

		@Test
		@DisplayName("handles three-way join with shared column names")
		void handlesThreeWayJoinWithSharedColumns() {
			String sql = """
					SELECT o.id AS order_id, 
					       o.name AS order_desc, 
					       c.name AS customer_name,
					       p.name AS product_name
					FROM orders o 
					JOIN customers c ON o.customer_id = c.id
					JOIN products p ON o.id = p.id
					""";
			Query query = Query.fromSql(sql, catalogWithAmbiguousColumns);

			assertThat(query.sqlString()).containsIgnoringCase("order_id");
			assertThat(query.sqlString()).containsIgnoringCase("customer_name");
			assertThat(query.sqlString()).containsIgnoringCase("product_name");
		}

		@Test
		@DisplayName("handles ambiguous column in WHERE clause with alias")
		void handlesAmbiguousColumnInWhereWithAlias() {
			String sql = "SELECT o.id FROM orders o JOIN customers c ON o.customer_id = c.id WHERE o.status = 'ACTIVE' AND c.status = 'VERIFIED'";
			Query query = Query.fromSql(sql, catalogWithAmbiguousColumns);

			assertThat(query.sqlString()).containsIgnoringCase("o.status");
			assertThat(query.sqlString()).containsIgnoringCase("c.status");
		}

		@Test
		@DisplayName("handles ambiguous column in ORDER BY with alias")
		void handlesAmbiguousColumnInOrderBy() {
			String sql = "SELECT o.id, c.name FROM orders o JOIN customers c ON o.customer_id = c.id ORDER BY c.name";
			Query query = Query.fromSql(sql, catalogWithAmbiguousColumns);

			assertThat(query.sqlString()).containsIgnoringCase("ORDER BY c.name");
		}

		@Test
		@DisplayName("handles column aliases to avoid ambiguity in result set")
		void handlesColumnAliasesToAvoidAmbiguity() {
			String sql = "SELECT o.id AS order_id, c.id AS customer_id FROM orders o JOIN customers c ON o.customer_id = c.id";
			Query query = Query.fromSql(sql, catalogWithAmbiguousColumns);

			assertThat(query.sqlString()).containsIgnoringCase("order_id");
			assertThat(query.sqlString()).containsIgnoringCase("customer_id");
		}

		@Test
		@DisplayName("handles subquery with same column names as outer query")
		void handlesSubqueryWithSameColumnNames() {
			String sql = "SELECT o.id, o.name FROM orders o WHERE o.customer_id IN (SELECT id FROM customers WHERE status = 'ACTIVE')";
			Query query = Query.fromSql(sql, catalogWithAmbiguousColumns);

			assertThat(query.sqlString()).containsIgnoringCase("SELECT id FROM customers");
		}

		@Test
		@DisplayName("synonym substitution works with ambiguous columns")
		void synonymSubstitutionWorksWithAmbiguousColumns() {
			SqlCatalog catalogWithSynonyms = new InMemorySqlCatalog()
					.addTable("fct_orders", "Orders", "fact")
					.withSynonyms("fct_orders", "orders")
					.addColumn("fct_orders", "id", "Order PK", "integer", null, null)
					.addColumn("fct_orders", "customer_id", "FK", "integer", null, null)
					.addColumn("fct_orders", "customer_name", "Denormalized name", "varchar", null, null)
					.withColumnSynonyms("fct_orders", "customer_name", "name")
					.addTable("dim_customer", "Customers", "dimension")
					.withSynonyms("dim_customer", "customers")
					.addColumn("dim_customer", "id", "Customer PK", "integer", null, null)
					.addColumn("dim_customer", "customer_name", "Name", "varchar", null, null)
					.withColumnSynonyms("dim_customer", "customer_name", "name");

			// The "name" synonym resolves differently per table
			String sql = "SELECT o.name, c.name FROM orders o JOIN customers c ON o.customer_id = c.id";
			Query query = Query.fromSql(sql, catalogWithSynonyms);

			// Both should be resolved to customer_name
			assertThat(query.sqlString()).containsIgnoringCase("o.customer_name");
			assertThat(query.sqlString()).containsIgnoringCase("c.customer_name");
			// And table synonyms should be resolved
			assertThat(query.sqlString()).containsIgnoringCase("fct_orders o");
			assertThat(query.sqlString()).containsIgnoringCase("dim_customer c");
		}
	}

	@Nested
	@DisplayName("Time intelligence queries")
	class TimeIntelligenceQueries {

		@Test
		@DisplayName("parses BETWEEN for date range")
		void parsesBetweenDateRange() {
			String sql = "SELECT id, amount FROM orders WHERE order_date BETWEEN '2024-01-01' AND '2024-12-31'";
			Query query = Query.fromSql(sql, catalog);

			assertThat(query.sqlString()).containsIgnoringCase("BETWEEN");
			assertThat(query.sqlString()).contains("2024-01-01");
			assertThat(query.sqlString()).contains("2024-12-31");
		}

		@Test
		@DisplayName("parses date comparison operators")
		void parsesDateComparisonOperators() {
			String sql = "SELECT id FROM orders WHERE order_date >= '2024-01-01' AND order_date < '2024-02-01'";
			Query query = Query.fromSql(sql, catalog);

			assertThat(query.sqlString()).containsIgnoringCase("order_date >=");
			assertThat(query.sqlString()).containsIgnoringCase("order_date <");
		}

		@Test
		@DisplayName("parses EXTRACT function for year")
		void parsesExtractYear() {
			String sql = "SELECT id FROM orders WHERE EXTRACT(YEAR FROM order_date) = 2024";
			Query query = Query.fromSql(sql, catalog);

			assertThat(query.sqlString()).containsIgnoringCase("EXTRACT(YEAR FROM order_date)");
		}

		@Test
		@DisplayName("parses EXTRACT function for month")
		void parsesExtractMonth() {
			String sql = "SELECT EXTRACT(MONTH FROM order_date), SUM(amount) FROM orders GROUP BY EXTRACT(MONTH FROM order_date)";
			Query query = Query.fromSql(sql, catalog);

			assertThat(query.sqlString()).containsIgnoringCase("EXTRACT(MONTH FROM order_date)");
		}

		@Test
		@DisplayName("parses DATE_TRUNC function")
		void parsesDateTrunc() {
			String sql = "SELECT DATE_TRUNC('month', order_date), SUM(amount) FROM orders GROUP BY DATE_TRUNC('month', order_date)";
			Query query = Query.fromSql(sql, catalog);

			assertThat(query.sqlString()).containsIgnoringCase("DATE_TRUNC");
		}

		@Test
		@DisplayName("parses CURRENT_DATE")
		void parsesCurrentDate() {
			String sql = "SELECT id FROM orders WHERE order_date = CURRENT_DATE";
			Query query = Query.fromSql(sql, catalog);

			assertThat(query.sqlString()).containsIgnoringCase("CURRENT_DATE");
		}

		@Test
		@DisplayName("parses date arithmetic")
		void parsesDateArithmetic() {
			String sql = "SELECT id FROM orders WHERE order_date >= CURRENT_DATE - INTERVAL '30' DAY";
			Query query = Query.fromSql(sql, catalog);

			assertThat(query.sqlString()).containsIgnoringCase("INTERVAL");
		}

		@Test
		@DisplayName("parses timestamp comparison")
		void parsesTimestampComparison() {
			String sql = "SELECT id FROM orders WHERE created_at >= TIMESTAMP '2024-01-01 00:00:00'";
			Query query = Query.fromSql(sql, catalog);

			assertThat(query.sqlString()).containsIgnoringCase("TIMESTAMP");
			assertThat(query.sqlString()).contains("2024-01-01 00:00:00");
		}

		@Test
		@DisplayName("parses GROUP BY date with aggregation")
		void parsesGroupByDateWithAggregation() {
			String sql = "SELECT order_date, COUNT(*), SUM(amount) FROM orders GROUP BY order_date ORDER BY order_date";
			Query query = Query.fromSql(sql, catalog);

			assertThat(query.sqlString()).containsIgnoringCase("GROUP BY order_date");
			assertThat(query.sqlString()).containsIgnoringCase("ORDER BY order_date");
		}

		@Test
		@DisplayName("parses CASE with date conditions")
		void parsesCaseWithDateConditions() {
			String sql = """
					SELECT id,
					       CASE 
					           WHEN order_date >= '2024-01-01' THEN 'current_year'
					           ELSE 'previous_year'
					       END AS year_category
					FROM orders
					""";
			Query query = Query.fromSql(sql, catalog);

			assertThat(query.sqlString()).containsIgnoringCase("CASE");
			assertThat(query.sqlString()).containsIgnoringCase("WHEN");
			assertThat(query.sqlString()).containsIgnoringCase("current_year");
		}

		@Test
		@DisplayName("parses year-over-year comparison pattern")
		void parsesYearOverYearPattern() {
			// Common pattern: compare current year to previous year
			String sql = """
					SELECT EXTRACT(MONTH FROM order_date) AS month,
					       SUM(CASE WHEN EXTRACT(YEAR FROM order_date) = 2024 THEN amount ELSE 0 END) AS this_year,
					       SUM(CASE WHEN EXTRACT(YEAR FROM order_date) = 2023 THEN amount ELSE 0 END) AS last_year
					FROM orders
					GROUP BY EXTRACT(MONTH FROM order_date)
					""";
			Query query = Query.fromSql(sql, catalog);

			assertThat(query.sqlString()).containsIgnoringCase("this_year");
			assertThat(query.sqlString()).containsIgnoringCase("last_year");
		}
	}

	@Nested
	@DisplayName("Aggregation queries")
	class AggregationQueries {

		@Test
		@DisplayName("parses SUM aggregate")
		void parsesSumAggregate() {
			String sql = "SELECT SUM(amount) FROM orders";
			Query query = Query.fromSql(sql, catalog);

			assertThat(query.sqlString()).containsIgnoringCase("SUM(amount)");
		}

		@Test
		@DisplayName("parses COUNT aggregate")
		void parsesCountAggregate() {
			String sql = "SELECT COUNT(*) FROM orders";
			Query query = Query.fromSql(sql, catalog);

			assertThat(query.sqlString()).containsIgnoringCase("COUNT(*)");
		}

		@Test
		@DisplayName("parses COUNT DISTINCT")
		void parsesCountDistinct() {
			String sql = "SELECT COUNT(DISTINCT customer_id) FROM orders";
			Query query = Query.fromSql(sql, catalog);

			assertThat(query.sqlString()).containsIgnoringCase("COUNT(DISTINCT customer_id)");
		}

		@Test
		@DisplayName("parses AVG aggregate")
		void parsesAvgAggregate() {
			String sql = "SELECT AVG(amount) AS avg_amount FROM orders";
			Query query = Query.fromSql(sql, catalog);

			assertThat(query.sqlString()).containsIgnoringCase("AVG(amount)");
			assertThat(query.sqlString()).containsIgnoringCase("avg_amount");
		}

		@Test
		@DisplayName("parses MIN and MAX aggregates")
		void parsesMinMaxAggregates() {
			String sql = "SELECT MIN(amount), MAX(amount) FROM orders";
			Query query = Query.fromSql(sql, catalog);

			assertThat(query.sqlString()).containsIgnoringCase("MIN(amount)");
			assertThat(query.sqlString()).containsIgnoringCase("MAX(amount)");
		}

		@Test
		@DisplayName("parses GROUP BY with single column")
		void parsesGroupBySingleColumn() {
			String sql = "SELECT customer_id, SUM(amount) FROM orders GROUP BY customer_id";
			Query query = Query.fromSql(sql, catalog);

			assertThat(query.sqlString()).containsIgnoringCase("GROUP BY customer_id");
		}

		@Test
		@DisplayName("parses GROUP BY with multiple columns")
		void parsesGroupByMultipleColumns() {
			String sql = "SELECT customer_id, status, COUNT(*) FROM orders GROUP BY customer_id, status";
			Query query = Query.fromSql(sql, catalog);

			assertThat(query.sqlString()).containsIgnoringCase("GROUP BY customer_id, status");
		}

		@Test
		@DisplayName("parses HAVING clause")
		void parsesHavingClause() {
			String sql = "SELECT customer_id, SUM(amount) FROM orders GROUP BY customer_id HAVING SUM(amount) > 1000";
			Query query = Query.fromSql(sql, catalog);

			assertThat(query.sqlString()).containsIgnoringCase("HAVING");
			assertThat(query.sqlString()).containsIgnoringCase("SUM(amount) > 1000");
		}

		@Test
		@DisplayName("parses HAVING with COUNT")
		void parsesHavingWithCount() {
			String sql = "SELECT customer_id, COUNT(*) FROM orders GROUP BY customer_id HAVING COUNT(*) >= 5";
			Query query = Query.fromSql(sql, catalog);

			assertThat(query.sqlString()).containsIgnoringCase("HAVING COUNT(*) >= 5");
		}

		@Test
		@DisplayName("parses aggregation with JOIN")
		void parsesAggregationWithJoin() {
			String sql = "SELECT c.name, SUM(o.amount) FROM orders o JOIN customers c ON o.customer_id = c.id GROUP BY c.name";
			Query query = Query.fromSql(sql, catalog);

			assertThat(query.sqlString()).containsIgnoringCase("SUM(o.amount)");
			assertThat(query.sqlString()).containsIgnoringCase("GROUP BY c.name");
		}

		@Test
		@DisplayName("parses aggregation with WHERE and HAVING")
		void parsesAggregationWithWhereAndHaving() {
			String sql = "SELECT customer_id, SUM(amount) FROM orders WHERE status = 'COMPLETED' GROUP BY customer_id HAVING SUM(amount) > 500";
			Query query = Query.fromSql(sql, catalog);

			assertThat(query.sqlString()).containsIgnoringCase("WHERE");
			assertThat(query.sqlString()).containsIgnoringCase("GROUP BY");
			assertThat(query.sqlString()).containsIgnoringCase("HAVING");
		}

		@Test
		@DisplayName("parses nested aggregation")
		void parsesNestedAggregation() {
			// Using a subquery with aggregation
			String sql = "SELECT customer_id FROM orders GROUP BY customer_id HAVING SUM(amount) > (SELECT AVG(amount) FROM orders)";
			Query query = Query.fromSql(sql, catalog);

			assertThat(query.sqlString()).containsIgnoringCase("HAVING SUM(amount) >");
			assertThat(query.sqlString()).containsIgnoringCase("AVG(amount)");
		}

		@Test
		@DisplayName("parses DISTINCT")
		void parsesDistinct() {
			String sql = "SELECT DISTINCT status FROM orders";
			Query query = Query.fromSql(sql, catalog);

			assertThat(query.sqlString()).containsIgnoringCase("DISTINCT");
		}

		@Test
		@DisplayName("parses aggregate with ORDER BY")
		void parsesAggregateWithOrderBy() {
			String sql = "SELECT customer_id, SUM(amount) AS total FROM orders GROUP BY customer_id ORDER BY total DESC";
			Query query = Query.fromSql(sql, catalog);

			assertThat(query.sqlString()).containsIgnoringCase("ORDER BY total DESC");
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
	@DisplayName("Security - DDL/DML rejection and injection prevention")
	class SecurityTests {

		@Test
		@DisplayName("rejects ALTER TABLE")
		void rejectsAlterTable() {
			assertThatThrownBy(() -> Query.fromSql("ALTER TABLE orders ADD COLUMN notes VARCHAR(255)", catalog))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("Only SELECT statements are allowed");
		}

		@Test
		@DisplayName("rejects TRUNCATE TABLE")
		void rejectsTruncateTable() {
			assertThatThrownBy(() -> Query.fromSql("TRUNCATE TABLE orders", catalog))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("Only SELECT statements are allowed");
		}

		@Test
		@DisplayName("rejects CREATE INDEX")
		void rejectsCreateIndex() {
			assertThatThrownBy(() -> Query.fromSql("CREATE INDEX idx_orders ON orders(id)", catalog))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("Only SELECT statements are allowed");
		}

		@Test
		@DisplayName("rejects DROP INDEX")
		void rejectsDropIndex() {
			assertThatThrownBy(() -> Query.fromSql("DROP INDEX idx_orders", catalog))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("Only SELECT statements are allowed");
		}

		@Test
		@DisplayName("rejects CREATE VIEW")
		void rejectsCreateView() {
			assertThatThrownBy(() -> Query.fromSql("CREATE VIEW order_summary AS SELECT id FROM orders", catalog))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("Only SELECT statements are allowed");
		}

		@Test
		@DisplayName("rejects GRANT statement (parser doesn't recognize it)")
		void rejectsGrant() {
			// JSqlParser doesn't recognize GRANT - it will throw a parse error
			// which is also safe (rejected as invalid SQL)
			assertThatThrownBy(() -> Query.fromSql("GRANT SELECT ON orders TO public", catalog))
					.isInstanceOf(QueryValidationException.class);
		}

		@Test
		@DisplayName("rejects MERGE/UPSERT statement")
		void rejectsMerge() {
			String mergeSql = """
					MERGE INTO orders AS target
					USING new_orders AS source ON target.id = source.id
					WHEN MATCHED THEN UPDATE SET amount = source.amount
					""";
			assertThatThrownBy(() -> Query.fromSql(mergeSql, catalog))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("Only SELECT statements are allowed");
		}

		@Test
		@DisplayName("rejects multi-statement with semicolon (injection attempt)")
		void rejectsMultiStatementInjection() {
			// JSqlParser parses only the first statement, so second is ignored
			// But we should verify no dangerous execution happens
			String sql = "SELECT id FROM orders; DROP TABLE orders";
			
			// Parser may parse first statement successfully but second gets ignored
			// This is actually safe because we only get the SELECT back
			// But let's verify the behavior
			assertThatThrownBy(() -> Query.fromSql(sql, catalog))
					.isInstanceOf(QueryValidationException.class);
		}

		@Test
		@DisplayName("rejects SQL comment hiding injection (-- style)")
		void rejectsCommentDashInjection() {
			// Attempting to hide malicious SQL after comment
			String sql = "SELECT id FROM orders -- ; DROP TABLE orders";
			// This should parse as valid SELECT (comment is ignored)
			Query query = Query.fromSql(sql, catalog);
			assertThat(query.sqlString()).containsIgnoringCase("SELECT");
			assertThat(query.sqlString()).doesNotContainIgnoringCase("DROP");
		}

		@Test
		@DisplayName("rejects SQL comment hiding injection (/* */ style)")
		void rejectsCommentBlockInjection() {
			String sql = "SELECT id FROM orders /* ; DROP TABLE orders */";
			Query query = Query.fromSql(sql, catalog);
			assertThat(query.sqlString()).containsIgnoringCase("SELECT");
			assertThat(query.sqlString()).doesNotContainIgnoringCase("DROP");
		}

		@Test
		@DisplayName("handles case variations of DDL keywords")
		void handlesCaseVariations() {
			assertThatThrownBy(() -> Query.fromSql("insert into orders values (1, 100)", catalog))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("Only SELECT statements are allowed");

			assertThatThrownBy(() -> Query.fromSql("DELETE from orders WHERE id = 1", catalog))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("Only SELECT statements are allowed");

			assertThatThrownBy(() -> Query.fromSql("Create Table evil (id int)", catalog))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("Only SELECT statements are allowed");
		}

		@Test
		@DisplayName("rejects EXECUTE/EXEC statement")
		void rejectsExecute() {
			assertThatThrownBy(() -> Query.fromSql("EXECUTE sp_delete_orders", catalog))
					.isInstanceOf(QueryValidationException.class);
		}

		@Test
		@DisplayName("rejects SET statement")
		void rejectsSet() {
			assertThatThrownBy(() -> Query.fromSql("SET autocommit = 0", catalog))
					.isInstanceOf(QueryValidationException.class);
		}

		@Test
		@DisplayName("allows SELECT with UNION (valid query composition)")
		void allowsSelectWithUnion() {
			// UNION queries are valid SELECTs - JSqlParser returns SetOperationList
			// Test without catalog to avoid synonym substitution (which only handles PlainSelect)
			String sql = "SELECT id FROM orders UNION SELECT id FROM customers";
			Query query = Query.fromSql(sql);  // No catalog - skip schema validation
			assertThat(query.sqlString()).containsIgnoringCase("UNION");
		}

		@Test
		@DisplayName("allows SELECT with subquery (valid)")
		void allowsSelectWithSubquery() {
			String sql = "SELECT id FROM orders WHERE customer_id IN (SELECT id FROM customers)";
			Query query = Query.fromSql(sql, catalog);
			assertThat(query.sqlString()).containsIgnoringCase("SELECT id FROM customers");
		}

		@Test
		@DisplayName("allows SELECT with CTE (valid)")
		void allowsSelectWithCte() {
			String sql = "WITH top_orders AS (SELECT id, amount FROM orders) SELECT id FROM top_orders";
			Query query = Query.fromSql(sql, catalog);
			assertThat(query.sqlString()).containsIgnoringCase("WITH");
		}

		@Test
		@DisplayName("rejects whitespace-padded DDL")
		void rejectsWhitespacePaddedDdl() {
			assertThatThrownBy(() -> Query.fromSql("   DROP TABLE orders   ", catalog))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("Only SELECT statements are allowed");
		}

		@Test
		@DisplayName("rejects newline-padded DDL")
		void rejectsNewlinePaddedDdl() {
			assertThatThrownBy(() -> Query.fromSql("\n\nDELETE FROM orders\n\n", catalog))
					.isInstanceOf(QueryValidationException.class)
					.hasMessageContaining("Only SELECT statements are allowed");
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

