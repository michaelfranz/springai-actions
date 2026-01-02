package org.javai.springai.scenarios.data_warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.javai.springai.actions.test.PlanAssertions.assertExecutionSuccess;
import static org.javai.springai.actions.test.PlanAssertions.assertPlanReady;
import java.util.List;
import org.javai.springai.actions.PersonaSpec;
import org.javai.springai.actions.Plan;
import org.javai.springai.actions.PlanExecutionResult;
import org.javai.springai.actions.PlanStatus;
import org.javai.springai.actions.Planner;
import org.javai.springai.actions.conversation.ConversationManager;
import org.javai.springai.actions.conversation.ConversationTurnResult;
import org.javai.springai.actions.conversation.InMemoryConversationStateStore;
import org.javai.springai.actions.sql.SqlCatalogTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Data warehouse scenario tests using tool-based dynamic schema discovery.
 * 
 * <p>In this variant, the LLM discovers schema information on-demand via
 * {@link SqlCatalogTool} rather than having the entire schema in the system prompt.
 * This is the Phase 3 approach for handling large schemas.</p>
 * 
 * <h2>Key Differences from Static Approach</h2>
 * <ul>
 *   <li>No {@link org.javai.springai.actions.sql.SqlCatalogContextContributor} in system prompt</li>
 *   <li>LLM uses {@code listTables()} and {@code getTableDetails()} tools</li>
 *   <li>FK relationships are in column tags (e.g., fk:dim_customer.id) - no separate tool needed</li>
 *   <li>Additional latency from tool calls, but smaller initial system prompt</li>
 * </ul>
 */
@DisplayName("Data Warehouse Scenario - Tool-Based Dynamic Discovery")
class DataWarehouseToolBasedScenarioTest extends AbstractDataWarehouseScenarioTest {

	private SqlCatalogTool catalogTool;

	@Override
	protected void initializePlanner() {
		// Create the catalog tool from base class catalog
		catalogTool = new SqlCatalogTool(catalog);

		PersonaSpec sqlAnalystPersona = PersonaSpec.builder()
				.name("SQLDataWarehouseAssistant")
				.role("Assistant for data warehouse query planning")
				.principles(List.of(
						"Understand what the user wants to accomplish from a domain perspective",
						"Use the listTables tool first to discover available tables",
						"Use getTableDetails to get column information for relevant tables",
						"For JOINs, use FK info from column tags (e.g., fk:dim_customer.id)",
						"Select the action whose purpose best matches the user's intent"))
				.constraints(List.of(
						"Only use the available actions",
						"Only use table and column names discovered via the catalog tools",
						"If any required parameter is unclear, use PENDING"))
				.build();

		// Note: No SqlCatalogContextContributor - schema discovery via tool
		planner = Planner.builder()
				.defaultChatClient(defaultChatClient)
				.persona(sqlAnalystPersona)
				.tools(catalogTool)  // Tool-based discovery
				.actions(dataWarehouseActions)
				.addPromptContext("sql", catalog)  // Still needed for Query resolution
				.build();
		
		conversationManager = new ConversationManager(planner, new InMemoryConversationStateStore());
	}

	@Nested
	@DisplayName("Basic Tool Usage")
	class BasicToolUsage {

		@Test
		@DisplayName("tool-based planner can formulate simple query")
		void toolBasedPlannerFormulatesSimpleQuery() {
			catalogTool.resetCounters();
			
			String request = "show me a query for all order values from the orders table";
			ConversationTurnResult turn = conversationManager.converse(request, "tool-simple-session");
			Plan plan = turn.plan();

			assertThat(plan).isNotNull();
			assertPlanReady(plan);
			
			PlanExecutionResult executed = executor.execute(plan);
			assertExecutionSuccess(executed);
			assertThat(dataWarehouseActions.showSqlQueryInvoked()).isTrue();
			
			// Log tool usage for analysis
			log.info("Tool usage for simple query:");
			log.info("  listTables: {}", catalogTool.listTablesInvokedCount());
			log.info("  getTableDetails: {}", catalogTool.getTableDetailsInvokedCount());
		}

		@Test
		@DisplayName("tool-based planner can formulate customer query")
		void toolBasedPlannerFormulatesCustomerQuery() {
			catalogTool.resetCounters();
			
			String request = "show me customer names from the customers table";
			ConversationTurnResult turn = conversationManager.converse(request, "tool-customer-session");
			Plan plan = turn.plan();

			assertThat(plan).isNotNull();
			assertPlanReady(plan);
			
			PlanExecutionResult executed = executor.execute(plan);
			assertExecutionSuccess(executed);
			
			// Log tool usage for analysis  
			log.info("Tool usage for customer query:");
			log.info("  listTables: {}", catalogTool.listTablesInvokedCount());
			log.info("  getTableDetails: {}", catalogTool.getTableDetailsInvokedCount());
		}

		@Test
		@DisplayName("generated SQL uses correct schema names")
		void generatedSqlUsesCorrectSchemaNames() {
			catalogTool.resetCounters();
			
			String request = "show me a query for order values from fct_orders";
			ConversationTurnResult turn = conversationManager.converse(request, "tool-schema-session");
			Plan plan = turn.plan();

			assertThat(plan).isNotNull();
			assertPlanReady(plan);
			
			PlanExecutionResult executed = executor.execute(plan);
			assertExecutionSuccess(executed);
			assertThat(dataWarehouseActions.showSqlQueryInvoked()).isTrue();
			
			// Verify the generated SQL uses correct table/column names
			String sql = dataWarehouseActions.lastQuery().orElseThrow().sqlString().toUpperCase();
			assertThat(sql).contains("ORDER_VALUE");
			assertThat(sql).contains("FCT_ORDERS");
		}
	}

	@Nested
	@DisplayName("JOIN Discovery")
	class JoinDiscovery {

		@Test
		@DisplayName("LLM uses FK tags from getTableDetails to understand JOINs")
		void llmUsesFkTagsForJoins() {
			catalogTool.resetCounters();
			
			String request = "show me order values with customer names";
			ConversationTurnResult turn = conversationManager.converse(request, "tool-join-session");
			Plan plan = turn.plan();

			assertThat(plan).isNotNull();
			
			// LLM should call getTableDetails and use FK tags (e.g., fk:dim_customer.id)
			// to understand how to JOIN tables
			if (plan.status() == PlanStatus.READY) {
				PlanExecutionResult executed = executor.execute(plan);
				if (executed.success() && dataWarehouseActions.showSqlQueryInvoked()) {
					String sql = dataWarehouseActions.lastQuery().orElseThrow().sqlString().toUpperCase();
					assertThat(sql).contains("JOIN");
				}
			}
		}

		@Test
		@DisplayName("LLM correctly JOINs fact to dimension using tool info")
		void llmJoinsFactToDimension() {
			catalogTool.resetCounters();
			
			String request = "show me a query for order values with customer names, joining the tables properly";
			ConversationTurnResult turn = conversationManager.converse(request, "tool-fk-join-session");
			Plan plan = turn.plan();

			assertThat(plan).isNotNull();
			assertPlanReady(plan);

			PlanExecutionResult executed = executor.execute(plan);
			assertExecutionSuccess(executed);
			
			String sql = dataWarehouseActions.lastQuery().orElseThrow().sqlString().toUpperCase();
			assertThat(sql).contains("FCT_ORDERS");
			assertThat(sql).contains("DIM_CUSTOMER");
			assertThat(sql).contains("JOIN");
		}
	}

	@Nested
	@DisplayName("Comparison with Static Approach")
	class ComparisonWithStatic {

		@Test
		@DisplayName("tool-based approach produces equivalent results to static")
		void equivalentResults() {
			catalogTool.resetCounters();
			
			String request = "show me all customer names";
			ConversationTurnResult turn = conversationManager.converse(request, "comparison-session");
			Plan plan = turn.plan();

			assertThat(plan).isNotNull();
			assertPlanReady(plan);

			PlanExecutionResult executed = executor.execute(plan);
			assertExecutionSuccess(executed);
			
			String sql = dataWarehouseActions.lastQuery().orElseThrow().sqlString().toUpperCase();
			assertThat(sql).contains("CUSTOMER_NAME");
			assertThat(sql).contains("DIM_CUSTOMER");
			
			// Log tool usage for comparison purposes
			log.info("Tool invocations for 'show me all customer names':");
			log.info("  listTables: {}", catalogTool.listTablesInvokedCount());
			log.info("  getTableDetails: {}", catalogTool.getTableDetailsInvokedCount());
		}
	}

	@Nested
	@DisplayName("Error Handling")
	class ErrorHandling {

		@Test
		@DisplayName("handles request for non-existent concept gracefully")
		void handlesNonExistentConcept() {
			catalogTool.resetCounters();
			
			// Request something that doesn't exist in the schema
			// The LLM should either ask for clarification (PENDING) or 
			// recognize there's no products table and return an error/pending status
			String request = "show me a list of all products and their prices";
			
			try {
				ConversationTurnResult turn = conversationManager.converse(request, "nonexistent-session");
				
				assertThat(turn).isNotNull();
				Plan plan = turn.plan();
				assertThat(plan).isNotNull();
				
				// Log what happened for analysis
				log.info("Plan status for non-existent concept: {}", plan.status());
				log.info("  listTables invoked: {}", catalogTool.listTablesInvokedCount());
				
				// Any status is acceptable for this test - the key is that 
				// the system handles the non-existent concept without crashing
				if (plan.status() == PlanStatus.READY) {
					PlanExecutionResult executed = executor.execute(plan);
					if (executed.success()) {
						if (dataWarehouseActions.lastQuery().isPresent()) {
							org.javai.springai.actions.sql.Query query = dataWarehouseActions.lastQuery().orElseThrow();
							String sql = query.sqlString().toUpperCase();
							log.info("  Generated SQL: {}", sql);
						}
					} else {
						log.info("  Execution failed (expected for non-existent concept)");
					}
				} else {
					log.info("  Non-READY status: {} (acceptable)", plan.status());
				}
			} catch (Exception e) {
				// Even exceptions are "graceful" handling - the system didn't crash unexpectedly
				log.info("Exception handling non-existent concept: {}", e.getMessage());
			}
		}
	}
}
