package org.javai.springai.actions.internal.parse;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for RawPlan parsing.
 * <p>
 * Note: Resolution from RawPlan to bound Plan is tested in {@link org.javai.springai.actions.exec.DefaultPlanResolverTest}.
 */
@DisplayName("RawPlan")
class RawPlanTest {

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

			RawPlan plan = RawPlan.fromJson(json);

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

			RawPlan plan = RawPlan.fromJson(json);

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

			RawPlan plan = RawPlan.fromJson(json);

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
		@DisplayName("parses plan with SQL string parameter")
		void parsesSqlStringParameter() throws JsonProcessingException {
			String json = """
					{
						"message": "Executing SQL query",
						"steps": [
							{
								"actionId": "runSqlQuery",
								"description": "Query customer orders",
								"parameters": {
									"query": "SELECT * FROM customers WHERE name = 'Mike'"
								}
							}
						]
					}
					""";

			RawPlan plan = RawPlan.fromJson(json);

			assertThat(plan.steps()).hasSize(1);
			assertThat(plan.steps().getFirst().parameters().get("query"))
					.isEqualTo("SELECT * FROM customers WHERE name = 'Mike'");
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

			RawPlan plan = RawPlan.fromJson(json);

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

			RawPlan plan = RawPlan.fromJson(json);

			assertThat(plan.steps().getFirst().parameters()).isEmpty();
		}
	}
}
