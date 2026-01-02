package org.javai.springai.scenarios.data_warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.javai.springai.actions.DefaultPlanExecutor;
import org.javai.springai.actions.Plan;
import org.javai.springai.actions.PlanExecutionResult;
import org.javai.springai.actions.PlanStatus;
import org.javai.springai.actions.PlanStep;
import org.javai.springai.actions.api.ActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Demonstrates the PendingPlanHandler and ErrorPlanHandler mechanism
 * for graceful handling of non-READY plans.
 *
 * <h2>The Problem This Solves</h2>
 * <p>Previously, developers had to branch before calling {@code executor.execute(plan)}
 * to check the plan status. If the plan was PENDING or ERROR, calling execute() would
 * throw an {@link IllegalStateException}.</p>
 *
 * <h2>The Solution</h2>
 * <p>Developers can now register handlers when building the executor:</p>
 * <pre>{@code
 * DefaultPlanExecutor executor = DefaultPlanExecutor.builder()
 *     .onPending((plan, context) -> {
 *         respondToUser("I need more information: " + plan.pendingParameterNames());
 *         return PlanExecutionResult.notExecuted(plan, context, "Awaiting user input");
 *     })
 *     .onError((plan, context) -> {
 *         logger.warn("Plan error occurred");
 *         return PlanExecutionResult.notExecuted(plan, context, "Plan parsing failed");
 *     })
 *     .build();
 *
 * // Now execute() is always safe to call - no branching required
 * PlanExecutionResult result = executor.execute(plan);
 * if (result.wasExecuted()) {
 *     // plan ran successfully
 * } else {
 *     // handlers dealt with PENDING/ERROR gracefully
 * }
 * }</pre>
 *
 * <h2>Scenario: Sales Analysis with Missing Date Range</h2>
 * <p>A data analyst asks "Show me Mike's total sales" but the {@code aggregateOrderValue}
 * action requires a date range. The handler prompts for the missing information.</p>
 */
@DisplayName("Plan State Handlers Scenario")
class PlanStateHandlersScenarioTest {

	// Simulates application response buffer (what would be sent to the user)
	private List<String> applicationResponses;

	// Pre-populated action context with request-scoped data
	private ActionContext requestContext;

	@BeforeEach
	void setUp() {
		applicationResponses = new ArrayList<>();
		requestContext = new ActionContext();
		requestContext.put("tenantId", "acme-corp");
		requestContext.put("userId", "analyst-42");
		requestContext.put("requestId", "req-abc-123");
	}

	// ========================================================================
	// Unit Tests for Handler Mechanism
	// ========================================================================

	@Nested
	@DisplayName("Pending Plan Handler")
	class PendingPlanHandlerTests {

		@Test
		@DisplayName("handler receives plan and context, returns graceful result")
		void handlerReceivesPlanAndContext() {
			DefaultPlanExecutor executor = DefaultPlanExecutor.builder()
					.onPending((plan, context) -> {
						// Verify we receive the plan
						assertThat(plan.status()).isEqualTo(PlanStatus.PENDING);
						assertThat(plan.pendingParameterNames()).contains("period");

						// Verify we receive the pre-populated context
						assertThat(context.get("tenantId")).isEqualTo("acme-corp");
						assertThat(context.get("userId")).isEqualTo("analyst-42");

						// Build user-friendly clarification
						String clarification = buildClarificationMessage(plan);
						applicationResponses.add(clarification);

						return PlanExecutionResult.notExecuted(plan, context, "Awaiting user input for: period");
					})
					.build();

			// Create a PENDING plan (missing date range for aggregateOrderValue)
			Plan pendingPlan = createPendingPlanForMissingPeriod();

			// Execute - handler should be invoked, NOT throw exception
			PlanExecutionResult result = executor.execute(pendingPlan, requestContext);

			// Verify graceful result
			assertThat(result.wasExecuted()).isFalse();
			assertThat(result.terminatedWith()).isEqualTo(PlanStatus.PENDING);
			assertThat(result.terminationReason()).contains("period");
			assertThat(result.success()).isFalse();

			// Context was preserved
			assertThat(result.context().get("tenantId")).isEqualTo("acme-corp");

			// Application captured a clarification message
			assertThat(applicationResponses).hasSize(1);
			assertThat(applicationResponses.getFirst())
					.contains("date range")
					.contains("time period");

			System.out.println("Assistant response: " + applicationResponses.getFirst());
		}

		@Test
		@DisplayName("multiple pending parameters are all accessible")
		void multiplePendingParametersAccessible() {
			DefaultPlanExecutor executor = DefaultPlanExecutor.builder()
					.onPending((plan, context) -> {
						List<String> missing = plan.pendingParameterNames();
						applicationResponses.add("Missing: " + String.join(", ", missing));
						return PlanExecutionResult.notExecuted(plan, context, "Multiple params missing");
					})
					.build();

			// Plan with multiple missing params
			Plan multiPendingPlan = new Plan(
					"I need more information to calculate the sales total",
					List.of(new PlanStep.PendingActionStep(
							"Calculate total sales",
							"aggregateOrderValue",
							new PlanStep.PendingParam[] {
									new PlanStep.PendingParam("customer_name", "Which customer?"),
									new PlanStep.PendingParam("period", "What time period?")
							},
							Map.of())));

			executor.execute(multiPendingPlan);

			assertThat(applicationResponses.getFirst()).contains("customer_name", "period");
		}
	}

	@Nested
	@DisplayName("Error Plan Handler")
	class ErrorPlanHandlerTests {

		@Test
		@DisplayName("handler provides graceful fallback for parse failures")
		void handlerProvidesGracefulFallback() {
			DefaultPlanExecutor executor = DefaultPlanExecutor.builder()
					.onError((plan, context) -> {
						String errorReason = extractErrorReason(plan);
						System.out.println("DEBUG - Plan error: " + errorReason);

						applicationResponses.add(
								"I couldn't understand that request. Could you rephrase it?");

						return PlanExecutionResult.notExecuted(plan, context, errorReason);
					})
					.build();

			// Create an ERROR plan (simulating parse failure)
			Plan errorPlan = new Plan(
					"I encountered an issue processing your request",
					List.of(new PlanStep.ErrorStep("Failed to parse LLM response: invalid JSON at position 42")));

			assertThat(errorPlan.status()).isEqualTo(PlanStatus.ERROR);

			PlanExecutionResult result = executor.execute(errorPlan, requestContext);

			// Verify graceful handling
			assertThat(result.wasExecuted()).isFalse();
			assertThat(result.terminatedWith()).isEqualTo(PlanStatus.ERROR);
			assertThat(result.terminationReason()).contains("invalid JSON");

			// User got a friendly message
			assertThat(applicationResponses).hasSize(1);
			assertThat(applicationResponses.getFirst())
					.contains("couldn't understand")
					.contains("rephrase");
		}

	}

	@Nested
	@DisplayName("Backward Compatibility")
	class BackwardCompatibilityTests {

		@Test
		@DisplayName("executor without handlers throws exception on PENDING")
		void executorWithoutHandlersThrowsOnPending() {
			DefaultPlanExecutor legacyExecutor = new DefaultPlanExecutor();

			Plan pendingPlan = createPendingPlanForMissingPeriod();

			assertThatThrownBy(() -> legacyExecutor.execute(pendingPlan))
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("PENDING")
					.hasMessageContaining("no handler");
		}

		@Test
		@DisplayName("executor without handlers throws exception on ERROR")
		void executorWithoutHandlersThrowsOnError() {
			DefaultPlanExecutor legacyExecutor = new DefaultPlanExecutor();

			Plan errorPlan = new Plan("error",
					List.of(new PlanStep.ErrorStep("parse failure")));

			assertThatThrownBy(() -> legacyExecutor.execute(errorPlan))
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("ERROR")
					.hasMessageContaining("no handler");
		}

		@Test
		@DisplayName("existing constructor signatures still work")
		void existingConstructorsWork() {
			// No-arg constructor
			DefaultPlanExecutor executor1 = new DefaultPlanExecutor();
			assertThat(executor1).isNotNull();

			// Emitter constructor
			DefaultPlanExecutor executor2 = new DefaultPlanExecutor(null);
			assertThat(executor2).isNotNull();
		}
	}

	@Nested
	@DisplayName("Combined Handlers")
	class CombinedHandlerTests {

		@Test
		@DisplayName("both handlers can be registered simultaneously")
		void bothHandlersCanBeRegistered() {
			List<String> handlerLog = new ArrayList<>();

			DefaultPlanExecutor executor = DefaultPlanExecutor.builder()
					.onPending((plan, context) -> {
						handlerLog.add("PENDING handler invoked");
						return PlanExecutionResult.notExecuted(plan, context, "pending");
					})
					.onError((plan, context) -> {
						handlerLog.add("ERROR handler invoked");
						return PlanExecutionResult.notExecuted(plan, context, "error");
					})
					.build();

			// Test PENDING plan
			Plan pendingPlan = createPendingPlanForMissingPeriod();
			executor.execute(pendingPlan);
			assertThat(handlerLog).containsExactly("PENDING handler invoked");

			handlerLog.clear();

			// Test ERROR plan
			Plan errorPlan = new Plan("error", List.of(new PlanStep.ErrorStep("oops")));
			executor.execute(errorPlan);
			assertThat(handlerLog).containsExactly("ERROR handler invoked");
		}
	}

	// ========================================================================
	// Helper Methods
	// ========================================================================

	private Plan createPendingPlanForMissingPeriod() {
		return new Plan(
				"I can look up Mike's total sales, but I need to know the date range.",
				List.of(new PlanStep.PendingActionStep(
						"Calculate total order value for Mike",
						"aggregateOrderValue",
						new PlanStep.PendingParam[] {
								new PlanStep.PendingParam("period", "What time period are you interested in?")
						},
						Map.of("customer_name", "Mike"))));
	}

	private String buildClarificationMessage(Plan plan) {
		StringBuilder sb = new StringBuilder();

		// Use the assistant message as context
		if (plan.assistantMessage() != null && !plan.assistantMessage().isBlank()) {
			sb.append(plan.assistantMessage()).append(" ");
		}

		// Build parameter-specific prompts
		for (PlanStep.PendingParam param : plan.pendingParams()) {
			if ("period".equals(param.name()) || param.name().toLowerCase().contains("date")) {
				sb.append("What date range or time period would you like to analyze?");
			} else if ("customer_name".equals(param.name())) {
				sb.append("Which customer are you interested in?");
			} else {
				sb.append(param.message());
			}
		}

		return sb.toString().trim();
	}

	private String extractErrorReason(Plan plan) {
		return plan.planSteps().stream()
				.filter(step -> step instanceof PlanStep.ErrorStep)
				.map(step -> ((PlanStep.ErrorStep) step).reason())
				.findFirst()
				.orElse("Unknown error");
	}
}

