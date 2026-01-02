package org.javai.springai.scenarios.data_warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.javai.springai.actions.test.PlanAssertions.assertExecutionSuccess;
import static org.javai.springai.actions.test.PlanAssertions.assertPlanReady;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.javai.springai.actions.DefaultPlanExecutor;
import org.javai.springai.actions.Plan;
import org.javai.springai.actions.PlanExecutionResult;
import org.javai.springai.actions.conversation.ConversationTurnResult;
import org.javai.springai.actions.internal.instrument.InvocationEmitter;
import org.javai.springai.actions.internal.instrument.InvocationEvent;
import org.javai.springai.actions.internal.instrument.InvocationEventType;
import org.javai.springai.actions.internal.instrument.InvocationListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Tests for execution instrumentation and event capture.
 * 
 * <p>Tests that InvocationEmitter correctly captures execution events
 * including timing, correlation IDs, and action attributes.</p>
 */
@DisplayName("Data Warehouse - Instrumentation")
@EnabledIfEnvironmentVariable(named = "RUN_LLM_TESTS", matches = "true")
class DataWarehouseInstrumentationTest extends AbstractDataWarehouseScenarioTest {

	@Test
	@DisplayName("execution events are captured via InvocationEmitter")
	void executionEventsCapturedViaEmitter() {
		// Reset actions for clean state
		dataWarehouseActions.reset();
		
		// Create a listener that captures all events
		List<InvocationEvent> capturedEvents = new CopyOnWriteArrayList<>();
		InvocationListener testListener = capturedEvents::add;
		
		// Create an emitter with our test listener
		InvocationEmitter emitter = InvocationEmitter.of("test-correlation-id", testListener);
		
		// Create executor with instrumentation
		DefaultPlanExecutor instrumentedExecutor = DefaultPlanExecutor.builder()
				.withEmitter(emitter)
				.build();

		// Execute a plan
		String request = "run query: select order_value from fct_orders";
		ConversationTurnResult turn = conversationManager.converse(request, "instrumentation-session");
		Plan plan = turn.plan();
		
		assertPlanReady(plan);
		
		PlanExecutionResult executed = instrumentedExecutor.execute(plan);
		assertExecutionSuccess(executed);
		
		// Verify events were captured
		assertThat(capturedEvents)
				.as("At least REQUESTED, STARTED, and SUCCEEDED events should be captured")
				.hasSizeGreaterThanOrEqualTo(3);
		
		// Verify event types
		List<InvocationEventType> eventTypes = capturedEvents.stream()
				.map(InvocationEvent::type)
				.toList();
		
		assertThat(eventTypes).contains(
				InvocationEventType.REQUESTED,
				InvocationEventType.STARTED,
				InvocationEventType.SUCCEEDED
		);
		
		// Verify correlation ID is propagated
		assertThat(capturedEvents)
				.allMatch(event -> "test-correlation-id".equals(event.correlationId()));
		
		// Verify action name is captured
		InvocationEvent succeededEvent = capturedEvents.stream()
				.filter(e -> e.type() == InvocationEventType.SUCCEEDED)
				.findFirst()
				.orElseThrow();
		
		assertThat(succeededEvent.name()).isEqualTo("runSqlQuery");
		
		// Verify duration is recorded for SUCCEEDED event
		assertThat(succeededEvent.durationMs())
				.as("Duration should be recorded for completed actions")
				.isNotNull()
				.isGreaterThanOrEqualTo(0L);
		
		// Verify attributes contain action ID
		assertThat(succeededEvent.attributes())
				.containsEntry("actionId", "runSqlQuery");
	}

	@Test
	@DisplayName("execution timing is captured accurately")
	void executionTimingCaptured() {
		dataWarehouseActions.reset();
		
		List<InvocationEvent> events = new CopyOnWriteArrayList<>();
		InvocationEmitter emitter = InvocationEmitter.of("timing-test", events::add);
		
		DefaultPlanExecutor instrumentedExecutor = DefaultPlanExecutor.builder()
				.withEmitter(emitter)
				.build();

		String request = "show me a query for customer names from dim_customer";
		ConversationTurnResult turn = conversationManager.converse(request, "timing-session");
		
		assertPlanReady(turn.plan());
		instrumentedExecutor.execute(turn.plan());
		
		// Find the SUCCEEDED event
		InvocationEvent succeeded = events.stream()
				.filter(e -> e.type() == InvocationEventType.SUCCEEDED)
				.findFirst()
				.orElseThrow();
		
		// Timing should be reasonable (not negative, not absurdly long)
		assertThat(succeeded.durationMs())
				.isNotNull()
				.isBetween(0L, 5000L); // Action should complete in under 5 seconds
		
		// Verify timestamp is present
		assertThat(succeeded.timestamp()).isNotNull();
	}
}

