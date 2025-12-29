package org.javai.springai.actions.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionParam;
import org.javai.springai.actions.bind.ActionDescriptorFilter;
import org.javai.springai.actions.bind.ActionRegistry;
import org.javai.springai.actions.prompt.InMemorySqlCatalog;
import org.javai.springai.actions.prompt.SqlCatalogContextContributor;
import org.javai.springai.actions.prompt.SystemPromptBuilder;
import org.javai.springai.actions.prompt.SystemPromptContext;
import org.javai.springai.actions.sql.Query;
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
	void includesSqlQueryGuidelines() {
		Optional<String> contribution = contributor.contribute(null);

		assertThat(contribution).isPresent();
		String content = contribution.get();

		// Verify SQL guidance is included
		assertThat(content).contains("SQL QUERY GUIDELINES:");
		assertThat(content).contains("standard ANSI SQL SELECT statement");
		assertThat(content).contains("Use only SELECT statements");
		assertThat(content).contains("no INSERT, UPDATE, DELETE");
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
		ActionRegistry registry = new ActionRegistry();
		registry.registerActions(new SqlActions());

		// Build the system prompt with the SQL catalog contributor
		String prompt = SystemPromptBuilder.build(
				registry,
				ActionDescriptorFilter.ALL,
				List.of(new SqlCatalogContextContributor(catalog)),
				Map.of("sql", catalog)
		);

		// The SystemPromptBuilder returns JSON with action specs
		// The SqlCatalogContextContributor is called separately by Planner
		assertThat(prompt).contains("actions");
		assertThat(prompt).contains("displaySqlQuery");
	}

	private static class SqlActions {
		@Action(description = "Display SQL query")
		public void displaySqlQuery(@ActionParam(description = "SQL query to display") Query query) {
		}
	}
}
