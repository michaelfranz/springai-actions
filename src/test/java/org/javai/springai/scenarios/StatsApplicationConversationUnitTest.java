package org.javai.springai.scenarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Map;
import org.javai.springai.dsl.conversation.ConversationPromptBuilder;
import org.javai.springai.dsl.conversation.ConversationState;
import org.javai.springai.dsl.plan.Plan;
import org.javai.springai.dsl.plan.PlanFormulationResult;
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
		String initialInstruction = "export control chart to excel for displacement values";

		// Turn 1: planner returns pending step for missing bundleId
		Plan pendingPlan = new Plan("Export control chart",
				List.of(new PlanStep.PendingActionStep("",
						"exportControlChartToExcel",
						new PlanStep.PendingParam[] { new PlanStep.PendingParam("bundleId", "Provide bundle id") },
						new Object[] { "displacement", "values" })));
		PlanFormulationResult pendingResult = new PlanFormulationResult(
				"", pendingPlan, null, false, null);
		// Turn 2: planner returns resolved action
		Plan resolvedPlan = new Plan("Export control chart",
				List.of(new PlanStep.ActionStep("",
						"exportControlChartToExcel",
						new Object[] { "displacement", "values", "A12345" })));
		PlanFormulationResult resolvedResult = new PlanFormulationResult(
				"", resolvedPlan, null, false, null);

		when(mockPlanner.formulatePlan(anyString(), any(ConversationState.class)))
				.thenReturn(pendingResult)
				.thenReturn(resolvedResult);

		// Simulate first turn
		ConversationState initialState = ConversationState.initial(initialInstruction);
		PlanFormulationResult firstTurn = mockPlanner.formulatePlan(initialInstruction, initialState);
		assertThat(firstTurn.plan().planSteps().getFirst()).isInstanceOf(PlanStep.PendingActionStep.class);

		// Build conversation state and retry addendum
		List<PlanStep.PendingParam> pendingParams = firstTurn.plan().pendingParams();
		ConversationState retryState = new ConversationState(
				initialInstruction,
				pendingParams,
				Map.of("domainEntity", "displacement", "measurementConcept", "values"),
				"bundle id is A12345");
		String addendum = ConversationPromptBuilder.buildRetryAddendum(retryState);
		assertThat(addendum).contains("bundleId").contains("bundle id is A12345");

		PlanFormulationResult secondTurn = mockPlanner.formulatePlan("bundle id is A12345", retryState);
		assertThat(secondTurn.plan().planSteps().getFirst()).isInstanceOf(PlanStep.ActionStep.class);
	}
}

