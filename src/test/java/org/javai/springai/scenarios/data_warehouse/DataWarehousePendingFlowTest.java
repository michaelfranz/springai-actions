package org.javai.springai.scenarios.data_warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.javai.springai.actions.DefaultPlanExecutor;
import org.javai.springai.actions.PersonaSpec;
import org.javai.springai.actions.Plan;
import org.javai.springai.actions.PlanStatus;
import org.javai.springai.actions.Planner;
import org.javai.springai.actions.conversation.ConversationManager;
import org.javai.springai.actions.conversation.ConversationTurnResult;
import org.javai.springai.actions.conversation.InMemoryConversationStateStore;
import org.javai.springai.actions.sql.InMemorySqlCatalog;
import org.javai.springai.actions.sql.SqlCatalogContextContributor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Tests for PENDING parameter flow.
 * 
 * <p>Tests that the LLM correctly uses PENDING when required parameters
 * are unclear or missing, and that the conversation can recover when
 * the user provides the missing information.</p>
 */
@DisplayName("Data Warehouse - PENDING Parameter Flow")
@EnabledIfEnvironmentVariable(named = "RUN_LLM_TESTS", matches = "true")
class DataWarehousePendingFlowTest extends AbstractDataWarehouseScenarioTest {

	private Planner pendingAwarePlanner;
	private ConversationManager pendingAwareConversationManager;
	private AggregateActions aggregateActions;
	private DefaultPlanExecutor localExecutor;

	@BeforeEach
	void setUpPendingTests() {
		// Use dedicated AggregateActions - not DataWarehouseActions
		// This avoids semantic confusion between SQL query actions and aggregate actions
		aggregateActions = new AggregateActions();
		localExecutor = DefaultPlanExecutor.builder().build();

		// Create SQL catalog (same as main setup)
		InMemorySqlCatalog pendingCatalog = new InMemorySqlCatalog()
				.addTable("fct_orders", "Fact table for orders", "fact")
				.withSynonyms("fct_orders", "orders", "order", "sales")
				.addColumn("fct_orders", "customer_id", "FK to dim_customer", "string",
						new String[] { "fk:dim_customer.id" }, null)
				.addColumn("fct_orders", "order_value", "Order amount", "double",
						new String[] { "measure" }, null)
				.addTable("dim_customer", "Customer dimension", "dimension")
				.withSynonyms("dim_customer", "customers", "customer", "cust")
				.addColumn("dim_customer", "id", "PK", "string",
						new String[] { "pk" }, new String[] { "unique" })
				.addColumn("dim_customer", "customer_name", "Customer name", "string",
						new String[] { "attribute" }, null);

		// Persona with EXPLICIT PENDING guidance per expert feedback
		PersonaSpec pendingAwarePersona = PersonaSpec.builder()
				.name("SQLDataWarehouseAssistant")
				.role("Assistant for data warehouse query planning and order value analysis")
				.principles(List.of(
						"Understand what the user wants to accomplish from a domain perspective",
						"Select the action whose purpose best matches the user's intent",
						"NEVER assume a period/date range. If the user doesn't give a range, you MUST return a PENDING step asking for it."))
				.constraints(List.of(
						"🚨 RULES FOR aggregateOrderValue:",
						"- parameters MUST be exactly: {\"orderValueQuery\":{\"customer_name\":\"<string>\",\"period\":{\"start\":\"YYYY-MM-DD\",\"end\":\"YYYY-MM-DD\"}}}",
						"- If period is missing → MUST return PENDING (do not assume 'all time').",
						"- Forbidden keys anywhere in parameters: customerName, customer, customer_id",
						"PENDING EXAMPLE when user says 'calculate total order value for Mike':",
						"{\"message\":\"Need a date range.\",\"steps\":[{\"actionId\":\"aggregateOrderValue\",\"description\":\"Aggregate order value for Mike.\",\"status\":\"pending\",\"pendingParams\":[{\"name\":\"orderValueQuery\",\"prompt\":\"Provide period as {\\\"start\\\":\\\"YYYY-MM-DD\\\",\\\"end\\\":\\\"YYYY-MM-DD\\\"}.\"}],\"providedParams\":{\"orderValueQuery\":{\"customer_name\":\"Mike\"}}}]}"))
				.build();

		pendingAwarePlanner = Planner.builder()
				.defaultChatClient(modestChatClient)
				.persona(pendingAwarePersona)
				.actions(aggregateActions)  // Only aggregate action - no SQL query actions
				.promptContributor(new SqlCatalogContextContributor(pendingCatalog))
				.addPromptContext("sql", pendingCatalog)
				.build();

		pendingAwareConversationManager = new ConversationManager(
				pendingAwarePlanner, new InMemoryConversationStateStore());
	}

	@Test
	@DisplayName("LLM returns PENDING when required date range is missing")
	void pendingWhenDateRangeMissing() {
		// User asks for aggregate without specifying dates - should trigger PENDING
		String ambiguousRequest = "calculate total order value for Mike";

		ConversationTurnResult turn = pendingAwareConversationManager.converse(
				ambiguousRequest, "pending-test-session");
		Plan plan = turn.plan();

		assertThat(plan).isNotNull();

		// The LLM should recognize that the aggregateOrderValue action requires a period
		// and return PENDING since no date range was provided
		assertThat(plan.status())
				.as("Plan should be PENDING when required date range is missing")
				.isEqualTo(PlanStatus.PENDING);

		// Verify pending params include the missing orderValueQuery (which contains period)
		assertThat(plan.pendingParameterNames())
				.as("Missing 'orderValueQuery' parameter should be identified")
				.anyMatch(name -> name.toLowerCase().contains("ordervaluequery")
						|| name.toLowerCase().contains("period")
						|| name.toLowerCase().contains("date"));

		// Verify no actions were invoked (plan wasn't executed)
		assertThat(aggregateActions.aggregateOrderValueInvoked()).isFalse();
	}

}

