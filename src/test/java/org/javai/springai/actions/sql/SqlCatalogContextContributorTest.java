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
		assertThat(content).contains("Use ONLY the exact table and column names");
		assertThat(content).contains("JOINs based on FK relationships");
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

	private static class SqlActions {
		@Action(description = "Display SQL query")
		public void displaySqlQuery(@ActionParam(description = "SQL query to display") Query query) {
		}
	}
}
