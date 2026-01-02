package org.javai.springai.scenarios.data_warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.javai.springai.actions.test.PlanAssertions.assertExecutionSuccess;
import static org.javai.springai.actions.test.PlanAssertions.assertPlanReady;
import java.util.List;
import org.javai.springai.actions.PersonaSpec;
import org.javai.springai.actions.Plan;
import org.javai.springai.actions.PlanExecutionResult;
import org.javai.springai.actions.PlanStep;
import org.javai.springai.actions.Planner;
import org.javai.springai.actions.conversation.ConversationManager;
import org.javai.springai.actions.conversation.ConversationTurnResult;
import org.javai.springai.actions.conversation.InMemoryConversationStateStore;
import org.javai.springai.actions.sql.SqlCatalogContextContributor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Tests for aggregate query planning with complex JSON record parameters.
 * 
 * <p>Tests the ability to correctly parse and pass structured parameters
 * like OrderValueQuery with nested Period records.</p>
 * 
 * <p>Uses a dedicated {@link AggregateActions} class to avoid semantic
 * confusion with SQL query actions. Uses a more capable model (gpt-4o) 
 * because complex nested record parameters require better JSON structure 
 * comprehension.</p>
 */
@DisplayName("Data Warehouse - Aggregate Queries")
@EnabledIfEnvironmentVariable(named = "RUN_LLM_TESTS", matches = "true")
class DataWarehouseAggregateTest extends AbstractDataWarehouseScenarioTest {

	private AggregateActions aggregateActions;
	private Planner aggregatePlanner;
	private ConversationManager aggregateConversationManager;

	@Override
	@BeforeEach
	void setUp() {
		super.setUp();
		
		// Use dedicated AggregateActions - not DataWarehouseActions
		// This avoids semantic confusion between SQL query actions and aggregate actions
		aggregateActions = new AggregateActions();
		
		// Use a more capable model for complex nested JSON structures
		PersonaSpec persona = PersonaSpec.builder()
				.name("OrderValueAnalyst")
				.role("Aggregate order value calculator with structured parameter handling")
				.principles(List.of(
						"For order value calculations, use the aggregateOrderValue action",
						"The orderValueQuery parameter MUST be a nested object with customer_name and period",
						"period contains start and end dates in YYYY-MM-DD format"))
				.constraints(List.of(
						"aggregateOrderValue requires: {\"orderValueQuery\":{\"customer_name\":\"...\",\"period\":{\"start\":\"YYYY-MM-DD\",\"end\":\"YYYY-MM-DD\"}}}",
						"Never flatten nested parameters - use the exact structure required"))
				.build();

		aggregatePlanner = Planner.builder()
				.defaultChatClient(capableChatClient, 2, CAPABLE_CHAT_MODEL_VERSION) // gpt-4o
				.fallbackChatClient(mostCapableChatClient, 2, MOST_CAPABLE_CHAT_MODEL_VERSION) // gpt-4-turbo
				.persona(persona)
				.actions(aggregateActions)  // Only aggregate action - no SQL query actions
				.promptContributor(new SqlCatalogContextContributor(catalog))
				.addPromptContext("sql", catalog)
				.build();

		aggregateConversationManager = new ConversationManager(aggregatePlanner, new InMemoryConversationStateStore());
	}

	@Test
	@Disabled("Known issue: LLMs flatten nested record parameters (customer_name, start, end) instead of nesting as orderValueQuery")
	@DisplayName("aggregate request with full parameters invokes aggregateOrderValue")
	void aggregateWithFullParametersInvokesCorrectAction() {
		String request = """
				calculate the total order value for customer Mike between 2024-01-01 and 2024-01-31
				""";

		ConversationTurnResult turn = aggregateConversationManager.converse(request, "aggregate-session");
		Plan plan = turn.plan();

		assertThat(plan).isNotNull();
		assertPlanReady(plan);
		assertThat(plan.planSteps()).hasSize(1);
		assertThat(plan.planSteps().getFirst()).isInstanceOf(PlanStep.ActionStep.class);

		PlanExecutionResult executed = executor.execute(plan);

		assertExecutionSuccess(executed);
		
		// Verify correct action was invoked
		assertThat(aggregateActions.aggregateOrderValueInvoked()).isTrue();
	}

}

