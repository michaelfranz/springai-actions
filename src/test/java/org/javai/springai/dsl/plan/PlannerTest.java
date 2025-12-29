package org.javai.springai.dsl.plan;

import static org.assertj.core.api.Assertions.assertThat;
import org.javai.springai.actions.api.Action;
import org.javai.springai.dsl.prompt.InMemorySqlCatalog;
import org.javai.springai.dsl.prompt.SqlCatalogContextContributor;
import org.javai.springai.dsl.sql.Query;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;

@SuppressWarnings("NullAway")
class PlannerTest {

	@Test
	void previewIncludesActionsInJsonFormat() {
		Planner planner = Planner.builder()
				.addPromptContribution("system-extra")
				.actions(new DemoActions())
				.enablePromptCapture()
				.build();

		PromptPreview preview = planner.preview("do something");

		// Actions are included (JSON plans reference action catalog)
		assertThat(preview.systemMessages())
				.anySatisfy(msg -> assertThat(msg).contains("demoAction"));
		assertThat(preview.userMessages()).contains("do something");
		assertThat(preview.actionNames()).contains("demoAction");
	}

	@Test
	void systemPromptUsesJsonPlanFormat() {
		Planner planner = Planner.builder()
				.addPromptContribution("system-extra")
				.actions(new DemoActions())
				.enablePromptCapture()
				.build();

		PromptPreview preview = planner.preview("do something");
		// Should have prompt contributions + planning directive
		assertThat(preview.systemMessages()).hasSizeGreaterThanOrEqualTo(2);

		// Check the planning directive uses JSON format
		String planningDirective = preview.systemMessages().getLast();
		assertThat(planningDirective).contains("JSON ONLY");
		assertThat(planningDirective).contains("\"message\"");
		assertThat(planningDirective).contains("\"steps\"");
		assertThat(planningDirective).contains("\"actionId\"");
		assertThat(planningDirective).contains("demoAction");
	}

	@Test
	void dryRunReturnsEmptyPlanAndPreview() {
		Planner planner = Planner.builder()
				.actions(new DemoActions())
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
		// Mock ChatClient to return an unparsable response
		ChatClient mockClient = Mockito.mock(ChatClient.class, Mockito.RETURNS_DEEP_STUBS);
		Mockito.when(mockClient.prompt().call().content()).thenReturn("{{{"); // malformed JSON

		Planner planner = Planner.builder()
				.withChatClient(mockClient)
				.actions(new DemoActions())
				.build();

		PlanFormulationResult result = planner.formulatePlan("irrelevant input", PlannerOptions.defaults());

		assertThat(result.plan()).isNotNull();
		assertThat(result.plan().planSteps()).hasSize(1);
		assertThat(result.plan().planSteps().getFirst()).isInstanceOf(PlanStep.ErrorStep.class);
		PlanStep.ErrorStep error = (PlanStep.ErrorStep) result.plan().planSteps().getFirst();
		assertThat(error.assistantMessage()).contains("Failed to parse");
	}

	@Test
	@SuppressWarnings("NullAway")
	void parsesJsonPlanResponse() {
		String jsonResponse = """
				{
					"message": "Executing demo action",
					"steps": [
						{
							"actionId": "demoAction",
							"description": "Run the demo",
							"parameters": { "input": "test value" }
						}
					]
				}
				""";

		ChatClient mockClient = Mockito.mock(ChatClient.class, Mockito.RETURNS_DEEP_STUBS);
		Mockito.when(mockClient.prompt().call().content()).thenReturn(jsonResponse);

		Planner planner = Planner.builder()
				.withChatClient(mockClient)
				.actions(new DemoActions())
				.build();

		PlanFormulationResult result = planner.formulatePlan("do something", PlannerOptions.defaults());

		assertThat(result.plan()).isNotNull();
		assertThat(result.plan().status()).isEqualTo(PlanStatus.READY);
		assertThat(result.plan().planSteps()).hasSize(1);

		PlanStep.ActionStep step = (PlanStep.ActionStep) result.plan().planSteps().getFirst();
		assertThat(step.actionId()).isEqualTo("demoAction");
		assertThat(step.actionArguments()).containsExactly("test value");
	}

	@Test
	@SuppressWarnings("NullAway")
	void parsesJsonInMarkdownCodeBlock() {
		String markdownResponse = """
				```json
				{
					"message": "Executing demo action",
					"steps": [
						{
							"actionId": "demoAction",
							"description": "Run the demo",
							"parameters": { "input": "from markdown" }
						}
					]
				}
				```
				""";

		ChatClient mockClient = Mockito.mock(ChatClient.class, Mockito.RETURNS_DEEP_STUBS);
		Mockito.when(mockClient.prompt().call().content()).thenReturn(markdownResponse);

		Planner planner = Planner.builder()
				.withChatClient(mockClient)
				.actions(new DemoActions())
				.build();

		PlanFormulationResult result = planner.formulatePlan("do something", PlannerOptions.defaults());

		assertThat(result.plan()).isNotNull();
		assertThat(result.plan().status()).isEqualTo(PlanStatus.READY);

		PlanStep.ActionStep step = (PlanStep.ActionStep) result.plan().planSteps().getFirst();
		assertThat(step.actionId()).isEqualTo("demoAction");
		assertThat(step.actionArguments()).containsExactly("from markdown");
	}

	@Test
	@SuppressWarnings("NullAway")
	void fallsBackToSExpressionParsing() {
		// S-expression response (for backwards compatibility)
		String sxlResponse = """
				(P "Demo plan" (PS demoAction (PA input "sxl value")))
				""";

		ChatClient mockClient = Mockito.mock(ChatClient.class, Mockito.RETURNS_DEEP_STUBS);
		Mockito.when(mockClient.prompt().call().content()).thenReturn(sxlResponse);

		Planner planner = Planner.builder()
				.withChatClient(mockClient)
				.actions(new DemoActions())
				.build();

		PlanFormulationResult result = planner.formulatePlan("do something", PlannerOptions.defaults());

		assertThat(result.plan()).isNotNull();
		assertThat(result.plan().status()).isEqualTo(PlanStatus.READY);

		PlanStep.ActionStep step = (PlanStep.ActionStep) result.plan().planSteps().getFirst();
		assertThat(step.actionId()).isEqualTo("demoAction");
		assertThat(step.actionArguments()).containsExactly("sxl value");
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
				.actions(new QueryActions())
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
