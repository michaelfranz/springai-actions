package org.javai.springai.dsl.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.javai.springai.actions.api.Action;
import org.javai.springai.dsl.act.ActionDescriptorFilter;
import org.javai.springai.dsl.act.ActionRegistry;
import org.javai.springai.dsl.bind.TypeFactoryBootstrap;
import org.javai.springai.dsl.sql.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SqlCatalogContextContributorTest {

	private DslGuidanceProvider guidanceProvider;

	@BeforeEach
	void setup() {
		TypeFactoryBootstrap.registerBuiltIns();
		guidanceProvider = new MapBackedDslGuidanceProvider(
				java.util.Map.of(
						"sxl-sql", "SQL grammar guidance here",
						"sxl-plan", "Plan grammar guidance here"
				)
		);
	}

	@Test
	void appendsCatalogAfterSqlGuidance() {
		ActionRegistry registry = new ActionRegistry();
		registry.registerActions(new SqlActions());

		InMemorySqlCatalog catalog = new InMemorySqlCatalog()
				.addTable("orders", "Orders placed by customers", "fact", "orders")
				.addColumn("orders", "id", "Primary key", "string", new String[] { "pk" }, null)
				.addColumn("orders", "status", "Order status", "string", new String[] { "dim" }, new String[] { "enum:SHIPPED,OPEN" })
				.addTable("facts_sales", "Sales fact table", "fact")
				.addColumn("facts_sales", "bundle_id", "Bundle identifier", "string", new String[] { "fk:orders.id" }, null)
				.addColumn("facts_sales", "value", "Sales amount", "number", new String[] { "measure" }, null);

		String prompt = SystemPromptBuilder.build(
				registry,
				ActionDescriptorFilter.ALL,
				guidanceProvider,
				SystemPromptBuilder.Mode.SXL,
				List.of(new PlanActionsContextContributor(), new SqlCatalogContextContributor(catalog)),
				java.util.Map.of(),
				null,
				null
		);

		int sqlIdx = prompt.indexOf("DSL sxl-sql");
		int catalogIdx = prompt.indexOf("SQL CATALOG");
		assertThat(sqlIdx).isNotNegative();
		assertThat(catalogIdx).isGreaterThan(sqlIdx);
		assertThat(prompt).contains("orders: Orders placed by customers");
		assertThat(prompt).contains("facts_sales: Sales fact table");
		assertThat(prompt).contains("• id (type=string; Primary key; tags=pk)");
		assertThat(prompt).contains("• status (type=string; Order status; tags=dim; constraints=enum:SHIPPED,OPEN)");
		assertThat(prompt).contains("• bundle_id (type=string; Bundle identifier; tags=fk:orders.id)");
		assertThat(prompt).contains("• value (type=number; Sales amount; tags=measure)");
	}

	private static class SqlActions {
		@Action(description = "Display SQL query")
		public void displaySqlQuery(Query query) {
		}
	}
}

