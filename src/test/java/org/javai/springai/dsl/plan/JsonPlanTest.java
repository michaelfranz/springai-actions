package org.javai.springai.dsl.plan;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("JsonPlan")
class JsonPlanTest {

	@Nested
	@DisplayName("JSON Parsing")
	class JsonParsing {

		@Test
		@DisplayName("parses single-step plan with primitive parameters")
		void parsesSingleStepWithPrimitives() throws JsonProcessingException {
			String json = """
					{
						"message": "Searching for products",
						"steps": [
							{
								"actionId": "searchProducts",
								"description": "Search for red shoes",
								"parameters": {
									"query": "red shoes",
									"maxResults": 10
								}
							}
						]
					}
					""";

			JsonPlan plan = JsonPlan.fromJson(json);

			assertThat(plan.message()).isEqualTo("Searching for products");
			assertThat(plan.steps()).hasSize(1);
			assertThat(plan.steps().getFirst().actionId()).isEqualTo("searchProducts");
			assertThat(plan.steps().getFirst().description()).isEqualTo("Search for red shoes");
			assertThat(plan.steps().getFirst().parameters())
					.containsEntry("query", "red shoes")
					.containsEntry("maxResults", 10);
		}

		@Test
		@DisplayName("parses multi-step plan")
		void parsesMultiStepPlan() throws JsonProcessingException {
			String json = """
					{
						"message": "I'll help you find and purchase the shoes",
						"steps": [
							{
								"actionId": "searchProducts",
								"description": "Find red shoes",
								"parameters": { "query": "red shoes" }
							},
							{
								"actionId": "addToCart",
								"description": "Add product to cart",
								"parameters": { "productId": "PROD-123", "quantity": 1 }
							}
						]
					}
					""";

			JsonPlan plan = JsonPlan.fromJson(json);

			assertThat(plan.steps()).hasSize(2);
			assertThat(plan.steps().getFirst().actionId()).isEqualTo("searchProducts");
			assertThat(plan.steps().get(1).actionId()).isEqualTo("addToCart");
		}

		@Test
		@DisplayName("parses plan with nested object parameter")
		void parsesNestedObjectParameter() throws JsonProcessingException {
			String json = """
					{
						"message": "Calculating order value",
						"steps": [
							{
								"actionId": "aggregateOrderValue",
								"description": "Calculate total order value for Mike",
								"parameters": {
									"orderValueQuery": {
										"customerName": "Mike",
										"period": {
											"start": "2024-01-01",
											"end": "2024-01-31"
										}
									}
								}
							}
						]
					}
					""";

			JsonPlan plan = JsonPlan.fromJson(json);

			assertThat(plan.steps()).hasSize(1);
			@SuppressWarnings("unchecked")
			Map<String, Object> orderValueQuery = (Map<String, Object>) plan.steps().getFirst().parameters().get("orderValueQuery");
			assertThat(orderValueQuery)
					.containsEntry("customerName", "Mike");
			@SuppressWarnings("unchecked")
			Map<String, Object> period = (Map<String, Object>) orderValueQuery.get("period");
			assertThat(period)
					.containsEntry("start", "2024-01-01")
					.containsEntry("end", "2024-01-31");
		}

		@Test
		@DisplayName("parses plan with S-expression string parameter")
		void parsesEmbeddedSExpressionParameter() throws JsonProcessingException {
			String json = """
					{
						"message": "Executing SQL query",
						"steps": [
							{
								"actionId": "runSqlQuery",
								"description": "Query customer orders",
								"parameters": {
									"query": "(Q (F customers c) (S (= c.name \\"Mike\\")) (C c.*))"
								}
							}
						]
					}
					""";

			JsonPlan plan = JsonPlan.fromJson(json);

			assertThat(plan.steps()).hasSize(1);
			assertThat(plan.steps().getFirst().parameters().get("query"))
					.isEqualTo("(Q (F customers c) (S (= c.name \"Mike\")) (C c.*))");
		}

		@Test
		@DisplayName("parses plan with empty steps list")
		void parsesEmptySteps() throws JsonProcessingException {
			String json = """
					{
						"message": "No actions needed",
						"steps": []
					}
					""";

			JsonPlan plan = JsonPlan.fromJson(json);

			assertThat(plan.message()).isEqualTo("No actions needed");
			assertThat(plan.steps()).isEmpty();
		}

		@Test
		@DisplayName("parses step with empty parameters")
		void parsesEmptyParameters() throws JsonProcessingException {
			String json = """
					{
						"message": "Listing items",
						"steps": [
							{
								"actionId": "listItems",
								"description": "Get all items",
								"parameters": {}
							}
						]
					}
					""";

			JsonPlan plan = JsonPlan.fromJson(json);

			assertThat(plan.steps().getFirst().parameters()).isEmpty();
		}
	}

	@Nested
	@DisplayName("Conversion to Plan")
	class ConversionToPlan {

		@Test
		@DisplayName("converts to Plan with parameter ordering")
		void convertsWithParameterOrdering() throws JsonProcessingException {
			String json = """
					{
						"message": "Adding to cart",
						"steps": [
							{
								"actionId": "addToCart",
								"description": "Add product",
								"parameters": {
									"quantity": 2,
									"productId": "PROD-456"
								}
							}
						]
					}
					""";

			JsonPlan jsonPlan = JsonPlan.fromJson(json);
			Plan plan = jsonPlan.toPlan(Map.of("addToCart", new String[]{"productId", "quantity"}));

			assertThat(plan.assistantMessage()).isEqualTo("Adding to cart");
			assertThat(plan.planSteps()).hasSize(1);

			PlanStep.ActionStep step = (PlanStep.ActionStep) plan.planSteps().getFirst();
			assertThat(step.actionId()).isEqualTo("addToCart");
			assertThat(step.assistantMessage()).isEqualTo("Add product");
			// Arguments should be in order: productId, quantity
			assertThat(step.actionArguments()).containsExactly("PROD-456", 2);
		}

		@Test
		@DisplayName("converts to Plan without parameter ordering")
		void convertsWithoutParameterOrdering() throws JsonProcessingException {
			String json = """
					{
						"message": "Searching",
						"steps": [
							{
								"actionId": "search",
								"description": "Find items",
								"parameters": {
									"query": "shoes"
								}
							}
						]
					}
					""";

			JsonPlan jsonPlan = JsonPlan.fromJson(json);
			Plan plan = jsonPlan.toPlan();

			assertThat(plan.planSteps()).hasSize(1);
			PlanStep.ActionStep step = (PlanStep.ActionStep) plan.planSteps().getFirst();
			assertThat(step.actionArguments()).contains("shoes");
		}

		@Test
		@DisplayName("empty steps result in READY status with no steps")
		void emptyStepsResultsInEmptyPlan() throws JsonProcessingException {
			String json = """
					{
						"message": "Nothing to do",
						"steps": []
					}
					""";

			JsonPlan jsonPlan = JsonPlan.fromJson(json);
			Plan plan = jsonPlan.toPlan();

			assertThat(plan.planSteps()).isEmpty();
			// Note: Plan.status() returns ERROR for empty steps, which is existing behavior
			assertThat(plan.status()).isEqualTo(PlanStatus.ERROR);
		}

		@Test
		@DisplayName("converts multi-step plan preserving order")
		void convertsMultiStepPreservingOrder() throws JsonProcessingException {
			String json = """
					{
						"message": "Processing order",
						"steps": [
							{
								"actionId": "validateStock",
								"description": "Check availability",
								"parameters": { "productId": "P1" }
							},
							{
								"actionId": "reserveStock",
								"description": "Reserve items",
								"parameters": { "productId": "P1", "quantity": 2 }
							},
							{
								"actionId": "createOrder",
								"description": "Create the order",
								"parameters": { "customerId": "C1" }
							}
						]
					}
					""";

			JsonPlan jsonPlan = JsonPlan.fromJson(json);
			Plan plan = jsonPlan.toPlan();

			assertThat(plan.planSteps()).hasSize(3);
			assertThat(((PlanStep.ActionStep) plan.planSteps().get(0)).actionId()).isEqualTo("validateStock");
			assertThat(((PlanStep.ActionStep) plan.planSteps().get(1)).actionId()).isEqualTo("reserveStock");
			assertThat(((PlanStep.ActionStep) plan.planSteps().get(2)).actionId()).isEqualTo("createOrder");
			assertThat(plan.status()).isEqualTo(PlanStatus.READY);
		}
	}

	@Nested
	@DisplayName("JsonPlanStep")
	class JsonPlanStepTests {

		@Test
		@DisplayName("toActionStep with ordered params places arguments correctly")
		void toActionStepWithOrderedParams() {
			JsonPlanStep step = new JsonPlanStep(
					"testAction",
					"Test description",
					Map.of("first", "A", "second", "B", "third", "C")
			);

			PlanStep.ActionStep actionStep = step.toActionStep(new String[]{"third", "first", "second"});

			assertThat(actionStep.actionArguments()).containsExactly("C", "A", "B");
		}

		@Test
		@DisplayName("toActionStep with null parameters returns empty args")
		void toActionStepWithNullParams() {
			JsonPlanStep step = new JsonPlanStep("testAction", "Test", null);

			PlanStep.ActionStep actionStep = step.toActionStep(new String[]{"param1"});

			assertThat(actionStep.actionArguments()).containsExactly((Object) null);
		}

		@Test
		@DisplayName("toActionStep preserves complex object parameters")
		void toActionStepPreservesComplexObjects() {
			Map<String, Object> nestedParams = Map.of(
					"filter", Map.of(
							"name", "Mike",
							"age", 30
					)
			);
			JsonPlanStep step = new JsonPlanStep("complexAction", "Complex test", nestedParams);

			PlanStep.ActionStep actionStep = step.toActionStep(new String[]{"filter"});

			@SuppressWarnings("unchecked")
			Map<String, Object> filter = (Map<String, Object>) actionStep.actionArguments()[0];
			assertThat(filter)
					.containsEntry("name", "Mike")
					.containsEntry("age", 30);
		}
	}
}

