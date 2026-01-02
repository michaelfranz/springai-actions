package org.javai.springai.scenarios.data_warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.javai.springai.actions.test.PlanAssertions.assertExecutionSuccess;
import static org.javai.springai.actions.test.PlanAssertions.assertPlanReady;
import org.javai.springai.actions.Plan;
import org.javai.springai.actions.PlanExecutionResult;
import org.javai.springai.actions.PlanStep;
import org.javai.springai.actions.conversation.ConversationTurnResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Tests for basic SELECT query planning and execution.
 * 
 * <p>Tests the ability to generate and execute simple SQL SELECT queries
 * without complex JOINs or aggregations.</p>
 */
@DisplayName("Data Warehouse - Basic SELECT Queries")
@EnabledIfEnvironmentVariable(named = "RUN_LLM_TESTS", matches = "true")
class DataWarehouseBasicSelectTest extends AbstractDataWarehouseScenarioTest {

	@Test
	@DisplayName("'show' request invokes showSqlQuery, not runSqlQuery")
	void showRequestInvokesShowSqlQuery() {
		String request = "show me a query for customer names from dim_customer";
		ConversationTurnResult turn = conversationManager.converse(request, "show-query-session");
		Plan plan = turn.plan();

		assertThat(plan).isNotNull();
		assertPlanReady(plan);
		assertThat(plan.planSteps()).hasSize(1);
		PlanStep step = plan.planSteps().getFirst();
		assertThat(step).isInstanceOf(PlanStep.ActionStep.class);

		PlanExecutionResult executed = executor.execute(plan);
		assertExecutionSuccess(executed);
		
		// Verify correct action was invoked AND wrong action was NOT invoked
		assertThat(dataWarehouseActions.showSqlQueryInvoked()).isTrue();
		assertThat(dataWarehouseActions.runSqlQueryInvoked())
				.as("runSqlQuery should NOT be invoked when user asks to 'show' a query")
				.isFalse();
	}

	@Test
	@DisplayName("'run' request invokes runSqlQuery, not showSqlQuery")
	void runRequestInvokesRunSqlQuery() {
		String request = "run query: select order_value from fct_orders";
		ConversationTurnResult turn = conversationManager.converse(request, "run-query-session");
		Plan plan = turn.plan();
		
		assertThat(plan).isNotNull();
		assertPlanReady(plan);
		assertThat(plan.planSteps()).hasSize(1);
		PlanStep step = plan.planSteps().getFirst();
		assertThat(step).isInstanceOf(PlanStep.ActionStep.class);

		PlanExecutionResult executed = executor.execute(plan);
		assertExecutionSuccess(executed);
		
		// Verify correct action was invoked AND wrong action was NOT invoked
		assertThat(dataWarehouseActions.runSqlQueryInvoked()).isTrue();
		assertThat(dataWarehouseActions.showSqlQueryInvoked())
				.as("showSqlQuery should NOT be invoked when user asks to 'run' a query")
				.isFalse();
	}
}

