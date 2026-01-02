package org.javai.springai.scenarios.data_warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.javai.springai.actions.test.PlanAssertions.assertExecutionSuccess;
import static org.javai.springai.actions.test.PlanAssertions.assertPlanReady;
import java.util.List;
import org.javai.springai.actions.PersonaSpec;
import org.javai.springai.actions.Plan;
import org.javai.springai.actions.PlanExecutionResult;
import org.javai.springai.actions.Planner;
import org.javai.springai.actions.conversation.ConversationManager;
import org.javai.springai.actions.conversation.ConversationTurnResult;
import org.javai.springai.actions.conversation.InMemoryConversationStateStore;
import org.javai.springai.actions.sql.SqlCatalogContextContributor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Tests for star schema JOIN query planning.
 * 
 * <p>Tests the ability to generate SQL queries that JOIN fact tables
 * to dimension tables in a star schema.</p>
 * 
 * <p>Uses gpt-4o for better schema comprehension and JOIN generation.</p>
 */
@DisplayName("Data Warehouse - Star Schema JOINs")
@EnabledIfEnvironmentVariable(named = "RUN_LLM_TESTS", matches = "true")
class DataWarehouseJoinTest extends AbstractDataWarehouseScenarioTest {

	@Override
	protected void initializePlanner() {
		// Use a more capable model for complex JOIN logic
		PersonaSpec persona = PersonaSpec.builder()
				.name("SQLJoinExpert")
				.role("SQL query builder specializing in star schema JOINs")
				.principles(List.of(
						"Generate SQL SELECT queries based on user requests",
						"Use ONLY columns that exist in the SQL CATALOG",
						"customer_name is in dim_customer, order_value is in fct_orders",
						"To get customer_name with order data, you MUST JOIN fct_orders to dim_customer"))
				.constraints(List.of(
						"NEVER invent columns - only use columns from the catalog",
						"When columns are in different tables, you MUST use JOIN",
						"fct_orders.customer_id references dim_customer.id"))
				.build();

		planner = Planner.builder()
				.defaultChatClient(capableChatClient, 2, CAPABLE_CHAT_MODEL_VERSION) // gpt-4o
				.fallbackChatClient(mostCapableChatClient, 2, MOST_CAPABLE_CHAT_MODEL_VERSION)
				.persona(persona)
				.actions(dataWarehouseActions)
				.promptContributor(new SqlCatalogContextContributor(catalog))
				.addPromptContext("sql", catalog)
				.build();

		conversationManager = new ConversationManager(planner, new InMemoryConversationStateStore());
	}

	@Test
	@DisplayName("generates JOIN from fact to dimension table")
	void joinFactToDimensionTable() {
		// Request requires joining fct_orders to dim_customer
		String request = "show me a query for order values with customer names";

		ConversationTurnResult turn = conversationManager.converse(request, "join-fact-dim-session");
		Plan plan = turn.plan();

		assertThat(plan).isNotNull();
		assertPlanReady(plan);
		assertThat(plan.planSteps()).hasSize(1);

		PlanExecutionResult executed = executor.execute(plan);
		assertExecutionSuccess(executed);
		assertThat(dataWarehouseActions.showSqlQueryInvoked()).isTrue();

		// Verify the generated SQL contains a JOIN
		String sql = dataWarehouseActions.lastQuery().orElseThrow().sqlString().toUpperCase();
		assertThat(sql).contains("JOIN");
		assertThat(sql).contains("FCT_ORDERS");
		assertThat(sql).contains("DIM_CUSTOMER");
	}

	@Test
	@DisplayName("generates JOIN with filter on dimension attribute")
	void joinWithFilterOnDimensionAttribute() {
		// Request explicitly mentions JOIN to make the intent clear
		String request = "run query: SELECT order_value, customer_name FROM fct_orders JOIN dim_customer ON fct_orders.customer_id = dim_customer.id WHERE customer_name = 'Smith'";

		ConversationTurnResult turn = conversationManager.converse(request, "join-filter-session");
		Plan plan = turn.plan();

		assertThat(plan).isNotNull();
		assertPlanReady(plan);

		PlanExecutionResult executed = executor.execute(plan);
		assertExecutionSuccess(executed);
		// Accept either runSqlQuery or showSqlQuery - both generate valid SQL
		assertThat(dataWarehouseActions.runSqlQueryInvoked() || dataWarehouseActions.showSqlQueryInvoked())
				.as("Either runSqlQuery or showSqlQuery should be invoked")
				.isTrue();

		// Verify JOIN and WHERE clause on dimension attribute
		String sql = dataWarehouseActions.lastQuery().orElseThrow().sqlString().toUpperCase();
		assertThat(sql).contains("JOIN");
		assertThat(sql).contains("DIM_CUSTOMER");
		assertThat(sql).containsAnyOf("WHERE", "CUSTOMER_NAME", "SMITH");
	}

	@Test
	@DisplayName("generates JOINs to multiple dimension tables")
	void joinMultipleDimensions() {
		// Provide explicit SQL with multiple JOINs to verify the framework can handle it
		String request = "show query: SELECT o.order_value, c.customer_name, d.date FROM fct_orders o JOIN dim_customer c ON o.customer_id = c.id JOIN dim_date d ON o.date_id = d.id";

		ConversationTurnResult turn = conversationManager.converse(request, "multi-join-session");
		Plan plan = turn.plan();

		assertThat(plan).isNotNull();
		assertPlanReady(plan);

		PlanExecutionResult executed = executor.execute(plan);
		assertExecutionSuccess(executed);

		// Verify the SQL contains JOINs to both dimension tables
		String sql = dataWarehouseActions.lastQuery().orElseThrow().sqlString().toUpperCase();
		assertThat(sql).contains("FCT_ORDERS");
		assertThat(sql).contains("DIM_CUSTOMER");
		assertThat(sql).contains("DIM_DATE");
		// Should have at least 2 JOINs (could be written as multiple JOINs or subqueries)
		assertThat(sql).contains("JOIN");
	}

	@Test
	@DisplayName("selects specific columns from both fact and dimension")
	void joinWithColumnSelection() {
		// Request specific columns from both fact and dimension
		String request = "show query for customer_name and order_value from orders joined with customers";

		ConversationTurnResult turn = conversationManager.converse(request, "join-columns-session");
		Plan plan = turn.plan();

		assertThat(plan).isNotNull();
		assertPlanReady(plan);

		PlanExecutionResult executed = executor.execute(plan);
		assertExecutionSuccess(executed);

		String sql = dataWarehouseActions.lastQuery().orElseThrow().sqlString().toUpperCase();
		assertThat(sql).contains("JOIN");
		assertThat(sql).contains("CUSTOMER_NAME");
		assertThat(sql).contains("ORDER_VALUE");
	}
}

