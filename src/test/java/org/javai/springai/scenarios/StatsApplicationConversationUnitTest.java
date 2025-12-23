package org.javai.springai.scenarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import org.javai.springai.dsl.conversation.ConversationPromptBuilder;
import org.javai.springai.dsl.conversation.ConversationState;
import org.javai.springai.dsl.conversation.PendingParamSnapshot;
import org.javai.springai.dsl.plan.Plan;
import org.javai.springai.dsl.plan.PlanExecutionResult;
import org.javai.springai.dsl.plan.PlanStep;
import org.javai.springai.dsl.plan.Planner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit-style conversation test using mocked planner/LLM responses to simulate
 * a two-turn flow: turn 1 yields a pending bundleId; turn 2 provides it and
 * yields an executable action.
 */
class StatsApplicationConversationUnitTest {

	@Mock
	private Planner mockPlanner;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	void conversationResolvesPendingWithFollowUp() {
		// Turn 1: planner returns pending step for missing bundleId
		Plan pendingPlan = new Plan("Export control chart",
				List.of(new PlanStep.PendingActionStep("",
						"exportControlChartToExcel",
						new PlanStep.PendingParam[] { new PlanStep.PendingParam("bundleId", "Provide bundle id") },
						new Object[] { "displacement", "values" })));
		PlanExecutionResult pendingResult = new PlanExecutionResult(
				"", pendingPlan, null, false, null);
		when(mockPlanner.planWithDetails(anyString())).thenReturn(pendingResult);

		// Simulate first turn
		PlanExecutionResult firstTurn = mockPlanner.planWithDetails("export control chart to excel for displacement values");
		assertThat(firstTurn.plan().planSteps().getFirst()).isInstanceOf(PlanStep.PendingActionStep.class);

		// Build conversation state and retry addendum
		ConversationState state = new ConversationState(
				"export control chart to excel for displacement values",
				List.of(new PendingParamSnapshot("exportControlChartToExcel", "bundleId", "Provide bundle id")),
				java.util.Map.of("domainEntity", "displacement", "measurementConcept", "values"),
				"bundle id is A12345"
		);
		String addendum = ConversationPromptBuilder.buildRetryAddendum(state);
		assertThat(addendum).contains("bundleId").contains("bundle id is A12345");

		// Turn 2: planner returns resolved action
		Plan resolvedPlan = new Plan("Export control chart",
				List.of(new PlanStep.ActionStep("",
						"exportControlChartToExcel",
						new Object[] { "displacement", "values", "A12345" })));
		PlanExecutionResult resolvedResult = new PlanExecutionResult(
				"", resolvedPlan, null, false, null);
		when(mockPlanner.planWithDetails("bundle id is A12345")).thenReturn(resolvedResult);

		PlanExecutionResult secondTurn = mockPlanner.planWithDetails("bundle id is A12345");
		assertThat(secondTurn.plan().planSteps().getFirst()).isInstanceOf(PlanStep.ActionStep.class);
	}
}

