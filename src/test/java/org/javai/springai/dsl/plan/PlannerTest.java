package org.javai.springai.dsl.plan;

import static org.assertj.core.api.Assertions.assertThat;
import org.javai.springai.actions.api.Action;
import org.javai.springai.dsl.prompt.InMemorySqlCatalog;
import org.javai.springai.dsl.prompt.SqlCatalogContextContributor;
import org.javai.springai.dsl.sql.Query;
import org.javai.springai.sxl.grammar.SxlGrammar;
import org.javai.springai.sxl.grammar.SxlGrammarParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;

@SuppressWarnings("NullAway")
class PlannerTest {

	private SxlGrammar planGrammar;

	@BeforeEach
	void setup() {
		SxlGrammarParser parser = new SxlGrammarParser();
		planGrammar = parser.parse(
				PlannerTest.class.getClassLoader().getResourceAsStream("META-INF/sxl-meta-grammar-plan.yml"));
	}

	@Test
	void previewIncludesGrammarAndActions() {
		Planner planner = Planner.builder()
				.addGrammar(planGrammar)
				.addPromptContribution("system-extra")
				.addActions(new DemoActions())
				.enablePromptCapture()
				.build();

		PromptPreview preview = planner.preview("do something");

		assertThat(preview.systemMessages())
				.anySatisfy(msg -> assertThat(msg).contains("DSL sxl-plan"));
		assertThat(preview.systemMessages())
				.anySatisfy(msg -> assertThat(msg).contains("demoAction"));
		assertThat(preview.userMessages()).contains("do something");
		assertThat(preview.grammarIds()).contains("sxl-plan");
		assertThat(preview.actionNames()).contains("demoAction");
	}

	@Test
	void systemPromptMatchesExpectedStructure() {
		Planner planner = Planner.builder()
				.addGrammar(planGrammar)
				.addPromptContribution("system-extra")
				.addActions(new DemoActions())
				.enablePromptCapture()
				.build();

		PromptPreview preview = planner.preview("do something");
		assertThat(preview.systemMessages()).hasSize(2);

		String system = normalize(preview.systemMessages().get(0));

		// Should combine DSL guidance + grammar summary + action catalog.
		assertThat(system).contains("DSL GUIDANCE");
		assertThat(system).contains("DSL sxl-plan");
		assertThat(system).contains("PLAN DSL root"); // guidance from llm_specs
		assertThat(system).contains("P") // grammar summary includes symbol names
				.contains("PS");
		assertThat(system).contains("ACTIONS");
		assertThat(system).contains("demoAction");

		assertThat(normalize(preview.systemMessages().get(1)))
				.isEqualTo("system-extra");
	}

	@Test
	void dryRunReturnsEmptyPlanAndPreview() {
		Planner planner = Planner.builder()
				.addGrammar(planGrammar)
				.addActions(new DemoActions())
				.build();

		PlanFormulationResult result = planner.formulatePlan("dry run request", PlannerOptions.dryRunOptions());

		Plan plan = result.plan();
		assertThat(result.dryRun()).isTrue();
		assertThat(plan).isNotNull();
		assertThat(plan.planSteps()).isEmpty();
		assertThat(result.promptPreview()).isNotNull();
		assertThat(result.promptPreview().userMessages()).contains("dry run request");
	}

	@Test
	@SuppressWarnings("NullAway")
	void unparsableResponseReturnsErrorPlan() {
		// Mock ChatClient to return an unparsable S-expression
		ChatClient mockClient = Mockito.mock(ChatClient.class, Mockito.RETURNS_DEEP_STUBS);
		Mockito.when(mockClient.prompt().call().content()).thenReturn("((("); // malformed plan

		Planner planner = Planner.builder()
				.addGrammar(planGrammar)
				.withChatClient(mockClient)
				.addActions(new DemoActions())
				.build();

		PlanFormulationResult result = planner.formulatePlan("irrelevant input", PlannerOptions.defaults());

		assertThat(result.plan()).isNotNull();
		assertThat(result.plan().planSteps()).hasSize(1);
		assertThat(result.plan().planSteps().getFirst()).isInstanceOf(PlanStep.ErrorStep.class);
		PlanStep.ErrorStep error = (PlanStep.ErrorStep) result.plan().planSteps().getFirst();
		assertThat(error.assistantMessage()).contains("Failed to parse");
	}

	@Test
	void systemPromptIncludesSqlCatalogMetadata() {
		InMemorySqlCatalog catalog = new InMemorySqlCatalog()
				.addTable("fct_orders", "Fact table for orders", "fact")
				.addColumn("fct_orders", "customer_id", "FK to dim_customer", "string",
						new String[] { "fk:dim_customer.id" }, null)
				.addColumn("fct_orders", "date_id", "FK to dim_date", "string",
						new String[] { "fk:dim_date.id" }, null)
				.addColumn("fct_orders", "order_value", "Order amount", "double",
						new String[] { "measure" }, null)
				.addTable("dim_customer", "Customer dimension", "dimension")
				.addColumn("dim_customer", "id", "PK", "string",
						new String[] { "pk" }, new String[] { "unique" })
				.addColumn("dim_customer", "customer_name", "Customer name", "string",
						new String[] { "attribute" }, null)
				.addTable("dim_date", "Date dimension", "dimension")
				.addColumn("dim_date", "id", "PK", "string",
						new String[] { "pk" }, new String[] { "unique" })
				.addColumn("dim_date", "date", "Calendar date", "date",
						new String[] { "attribute" }, null);

		Planner planner = Planner.builder()
				.addGrammar(planGrammar)
				.addActions(new QueryActions())
				.addDslContextContributor(new SqlCatalogContextContributor(catalog))
				.addDslContext("sxl-sql", catalog)
				.enablePromptCapture()
				.build();

		PromptPreview preview = planner.preview("sum order_value by customer for last 30 days");
		String system = String.join("\n", preview.systemMessages());

		assertThat(system).contains("SQL CATALOG");
		assertThat(system).contains("fct_orders").contains("order_value");
		assertThat(system).contains("dim_customer").contains("customer_name");
		assertThat(system).contains("dim_date").contains("id");
		assertThat(system).contains("fk:dim_customer.id");
		assertThat(system).contains("fk:dim_date.id");
	}

	static class DemoActions {
		@Action
		public void demoAction(String input) {
			// no-op
		}
	}

	static class QueryActions {
		@Action
		public void runQuery(Query query) {
			// no-op
		}
	}

	private static String normalize(String text) {
		return text == null ? "" : text.replaceAll("\\s+", " ").trim();
	}
}

