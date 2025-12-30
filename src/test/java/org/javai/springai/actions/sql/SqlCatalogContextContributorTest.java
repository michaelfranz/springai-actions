package org.javai.springai.actions.sql;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import java.util.Optional;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionParam;
import org.javai.springai.actions.internal.prompt.SystemPromptContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SqlCatalogContextContributorTest {

	private InMemorySqlCatalog catalog;
	private SqlCatalogContextContributor contributor;

	@BeforeEach
	void setup() {
		catalog = new InMemorySqlCatalog()
				.addTable("orders", "Orders placed by customers", "fact", "orders")
				.addColumn("orders", "id", "Primary key", "string", new String[] { "pk" }, null)
				.addColumn("orders", "status", "Order status", "string", new String[] { "dim" }, new String[] { "enum:SHIPPED,OPEN" })
				.addTable("facts_sales", "Sales fact table", "fact")
				.addColumn("facts_sales", "bundle_id", "Bundle identifier", "string", new String[] { "fk:orders.id" }, null)
				.addColumn("facts_sales", "value", "Sales amount", "number", new String[] { "measure" }, null);
		
		contributor = new SqlCatalogContextContributor(catalog);
	}

	@Test
	void contributesSchemaMetadata() {
		Optional<String> contribution = contributor.contribute(null);

		assertThat(contribution).isPresent();
		String content = contribution.get();

		// Verify table information
		assertThat(content).contains("SQL CATALOG:");
		assertThat(content).contains("orders: Orders placed by customers");
		assertThat(content).contains("facts_sales: Sales fact table");

		// Verify column information
		assertThat(content).contains("• id (type=string; Primary key; tags=pk)");
		assertThat(content).contains("• status (type=string; Order status; tags=dim; constraints=enum:SHIPPED,OPEN)");
		assertThat(content).contains("• bundle_id (type=string; Bundle identifier; tags=fk:orders.id)");
		assertThat(content).contains("• value (type=number; Sales amount; tags=measure)");
	}

	@Test
	void includesSqlCatalogNotes() {
		Optional<String> contribution = contributor.contribute(null);

		assertThat(contribution).isPresent();
		String content = contribution.get();

		// Verify catalog footer note is included
		assertThat(content).contains("CRITICAL: SQL table/column names MUST be taken from this catalog");
		assertThat(content).contains("For JOINs, use FK relationships");
	}

	@Test
	void returnsEmptyWhenCatalogIsEmpty() {
		SqlCatalogContextContributor emptyContributor = new SqlCatalogContextContributor(new InMemorySqlCatalog());
		Optional<String> contribution = emptyContributor.contribute(null);

		assertThat(contribution).isEmpty();
	}

	@Test
	void returnsEmptyWhenCatalogIsNull() {
		SqlCatalogContextContributor nullContributor = new SqlCatalogContextContributor(null);
		Optional<String> contribution = nullContributor.contribute(null);

		assertThat(contribution).isEmpty();
	}

	@Test
	void fallsBackToContextCatalogWhenDirectCatalogIsNull() {
		SqlCatalogContextContributor nullContributor = new SqlCatalogContextContributor(null);
		
		// Create a context with catalog under "sql" key
		SystemPromptContext context = new SystemPromptContext(null, null, null, Map.of("sql", catalog));
		Optional<String> contribution = nullContributor.contribute(context);

		assertThat(contribution).isPresent();
		assertThat(contribution.get()).contains("orders: Orders placed by customers");
	}

	@Test
	void fallsBackToSxlSqlKeyForBackwardCompatibility() {
		SqlCatalogContextContributor nullContributor = new SqlCatalogContextContributor(null);
		
		// Create a context with catalog under legacy "sxl-sql" key
		SystemPromptContext context = new SystemPromptContext(null, null, null, Map.of("sxl-sql", catalog));
		Optional<String> contribution = nullContributor.contribute(context);

		assertThat(contribution).isPresent();
		assertThat(contribution.get()).contains("orders: Orders placed by customers");
	}

	@Test
	void contributesToSystemPrompt() {
		// SqlCatalogContextContributor contributes directly to the prompt
		// SystemPromptBuilder.build() now returns empty string (action catalog
		// is provided by PlanActionsContextContributor instead)
		Optional<String> contribution = contributor.contribute(null);

		assertThat(contribution).isPresent();
		String content = contribution.get();
		
		// Verify the contributor provides SQL catalog information
		assertThat(content).contains("SQL CATALOG:");
		assertThat(content).contains("orders: Orders placed by customers");
	}

	@Test
	void contributesTokenizedCatalogWhenEnabled() {
		// Synonym-based tokenization: first synonym becomes the token
		InMemorySqlCatalog tokenizedCatalog = new InMemorySqlCatalog()
				.withTokenization(true)
				.addTable("fct_orders", "Order transactions", "fact")
				.withSynonyms("fct_orders", "orders", "sales")  // "orders" is token, "sales" is remaining
				.addColumn("fct_orders", "customer_id", "FK to customers", "integer", 
						new String[]{"fk:dim_customer.id"}, null)  // no synonyms -> cryptic token
				.addColumn("fct_orders", "order_value", "Order total", "decimal", 
						new String[]{"measure"}, null)
				.withColumnSynonyms("fct_orders", "order_value", "value", "amount")  // "value" is token
				.addTable("dim_customer", "Customer dimension", "dimension")
				.withSynonyms("dim_customer", "customers", "cust")  // "customers" is token
				.addColumn("dim_customer", "id", "Customer PK", "integer", new String[]{"pk"}, null)
				.addColumn("dim_customer", "name", "Customer name", "varchar", null, null)
				.withColumnSynonyms("dim_customer", "name", "customer_name", "cust_name");

		SqlCatalogContextContributor tokenizedContributor = new SqlCatalogContextContributor(tokenizedCatalog);
		Optional<String> contribution = tokenizedContributor.contribute(null);

		assertThat(contribution).isPresent();
		String content = contribution.get();

		// Verify catalog header (same as non-tokenized - LLM is unaware of tokenization)
		assertThat(content).contains("SQL CATALOG:");
		
		// When synonyms are defined, first synonym becomes the displayed name
		assertThat(content).contains("- orders:");  // fct_orders -> "orders" (first synonym)
		assertThat(content).contains("- customers:");  // dim_customer -> "customers" (first synonym)
		
		// Real table names are NOT exposed as the main identifier
		assertThat(content).doesNotContain("fct_orders:");
		assertThat(content).doesNotContain("dim_customer:");
		
		// Verify descriptions are still present
		assertThat(content).contains("Order transactions");
		assertThat(content).contains("Customer dimension");
		
		// Column with synonyms uses first synonym as token
		assertThat(content).contains("• value");  // order_value -> "value" (first synonym)
		assertThat(content).contains("• customer_name");  // name -> "customer_name" (first synonym)
		
		// Column without synonyms uses cryptic token
		assertThat(content).contains("c_");  // customer_id and id have no synonyms
		
		// Verify real column names with synonyms are NOT exposed as main identifier
		assertThat(content).doesNotContain("• order_value");
		assertThat(content).doesNotContain("• name (");  // "name" alone might appear in descriptions
		
		// FK references: uses token (first synonym or cryptic)
		assertThat(content).contains("fk:customers");  // FK to dim_customer uses "customers" token
		
		// Remaining synonyms shown as "also:"
		assertThat(content).contains("(also: sales)");  // "orders" is token, "sales" is remaining
		assertThat(content).contains("(also: cust)");   // "customers" is token, "cust" is remaining
		
		// Column remaining synonyms
		assertThat(content).contains("also=amount");         // "value" is token, "amount" is remaining
		assertThat(content).contains("also=cust_name");      // "customer_name" is token, "cust_name" is remaining
	}

	@Test
	void tokenizedCatalogUsesSameFooterAsStandard() {
		// LLM is unaware of tokenization - sees same catalog format
		InMemorySqlCatalog tokenizedCatalog = new InMemorySqlCatalog()
				.withTokenization(true)
				.addTable("orders", "Orders", "fact");

		SqlCatalogContextContributor tokenizedContributor = new SqlCatalogContextContributor(tokenizedCatalog);
		Optional<String> contribution = tokenizedContributor.contribute(null);

		assertThat(contribution).isPresent();
		String content = contribution.get();

		// Same footer as non-tokenized catalog (LLM doesn't know about tokenization)
		assertThat(content).contains("SQL table/column names MUST be taken from this catalog exactly as shown");
		assertThat(content).doesNotContain("token");  // No mention of "tokens"
	}

	@Test
	void tokenizedCatalogUsesCrypticTokensWhenNoSynonyms() {
		// When no synonyms are defined, cryptic hash-based tokens are used
		InMemorySqlCatalog tokenizedCatalog = new InMemorySqlCatalog()
				.withTokenization(true)
				.addTable("fct_orders", "Orders", "fact")
				.addColumn("fct_orders", "customer_id", "FK to customer", "integer", null, null)
				.addTable("dim_customer", "Customers", "dimension");
		// No synonyms added

		SqlCatalogContextContributor tokenizedContributor = new SqlCatalogContextContributor(tokenizedCatalog);
		Optional<String> contribution = tokenizedContributor.contribute(null);

		assertThat(contribution).isPresent();
		String content = contribution.get();

		// Cryptic tokens are used (prefix + hash)
		assertThat(content).contains("ft_");  // fact table
		assertThat(content).contains("dt_");  // dimension table
		assertThat(content).contains("c_");   // column
		
		// Real names not exposed
		assertThat(content).doesNotContain("fct_orders");
		assertThat(content).doesNotContain("dim_customer");
		assertThat(content).doesNotContain("customer_id");
		
		// No "also:" since no synonyms
		assertThat(content).doesNotContain("(also:");
	}

	@Test
	void nonTokenizedCatalogUsesStandardFormat() {
		// Default catalog without tokenization
		Optional<String> contribution = contributor.contribute(null);

		assertThat(contribution).isPresent();
		String content = contribution.get();

		// Verify standard format
		assertThat(content).contains("SQL CATALOG:");
		assertThat(content).doesNotContain("TOKENIZED");
		
		// Verify real table names are used
		assertThat(content).contains("orders:");
		assertThat(content).contains("facts_sales:");
	}

	@Test
	void includesTableAndColumnSynonymsInStandardFormat() {
		InMemorySqlCatalog catalogWithSynonyms = new InMemorySqlCatalog()
				.addTable("fct_orders", "Order transactions", "fact")
				.withSynonyms("fct_orders", "orders", "sales")
				.addColumn("fct_orders", "order_value", "Order total", "decimal", null, null)
				.withColumnSynonyms("fct_orders", "order_value", "value", "amount")
				.addColumn("fct_orders", "customer_id", "FK to customer", "integer", null, null);

		SqlCatalogContextContributor synonymContributor = new SqlCatalogContextContributor(catalogWithSynonyms);
		Optional<String> contribution = synonymContributor.contribute(null);

		assertThat(contribution).isPresent();
		String content = contribution.get();

		// Verify table synonyms are included
		assertThat(content).contains("(aka: orders, sales)");
		
		// Verify column synonyms are included for order_value
		assertThat(content).contains("aka=value,amount");
		
		// Verify customer_id column is present (without synonyms)
		assertThat(content).contains("• customer_id");
	}

	private static class SqlActions {
		@Action(description = "Display SQL query")
		public void displaySqlQuery(@ActionParam(description = "SQL query to display") Query query) {
		}
	}
}
