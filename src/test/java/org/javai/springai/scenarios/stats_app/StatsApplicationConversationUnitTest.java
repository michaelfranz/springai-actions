package org.javai.springai.scenarios.stats_app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Map;
import org.javai.springai.actions.Plan;
import org.javai.springai.actions.PlanStep;
import org.javai.springai.actions.Planner;
import org.javai.springai.actions.conversation.ConversationPromptBuilder;
import org.javai.springai.actions.conversation.ConversationState;
import org.javai.springai.actions.internal.bind.ActionBinding;
import org.javai.springai.actions.internal.plan.PlanArgument;
import org.javai.springai.actions.internal.plan.PlanFormulationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit-style conversation test using mocked planner/LLM responses to simulate
 * a two-turn flow: turn 1 yields a pending bundleId; turn 2 provides it and
 * yields an executable action.
 */
@SuppressWarnings("NullAway")
class StatsApplicationConversationUnitTest {

	@Mock
	private Planner mockPlanner;

	@Mock
	private ActionBinding mockBinding;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		when(mockBinding.id()).thenReturn("exportControlChartToExcel");
	}

	@Test
	@SuppressWarnings("NullAway")
	void conversationResolvesPendingWithFollowUp() {
		String initialInstruction = "export control chart to excel for displacement values";

		// Turn 1: planner returns pending step for missing bundleId
		Plan pendingPlan = new Plan("Export control chart",
				List.of(new PlanStep.PendingActionStep("",
						"exportControlChartToExcel",
						new PlanStep.PendingParam[] { new PlanStep.PendingParam("bundleId", "Provide bundle id") },
						Map.of("domainEntity", "displacement", "measurementType", "values"))));
		PlanFormulationResult pendingResult = new PlanFormulationResult(
				"", pendingPlan, null, false, null);

		// Turn 2: planner returns resolved action with binding
		Plan resolvedPlan = new Plan("Export control chart",
				List.of(new PlanStep.ActionStep(mockBinding, List.of(
						new PlanArgument("domainEntity", "displacement", String.class),
						new PlanArgument("measurementType", "values", String.class),
						new PlanArgument("bundleId", "A12345", String.class)
				))));
		PlanFormulationResult resolvedResult = new PlanFormulationResult(
				"", resolvedPlan, null, false, null);

		when(mockPlanner.formulatePlan(eq("export control chart to excel for displacement values"), any(ConversationState.class)))
				.thenReturn(pendingResult);
		when(mockPlanner.formulatePlan(eq("bundle id is A12345"), any(ConversationState.class)))
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
				Map.of("domainEntity", "displacement", "measurementType", "values"),
				"bundle id is A12345",
				null,
				List.of());
		String addendum = ConversationPromptBuilder.buildRetryAddendum(retryState).orElseThrow();
		assertThat(addendum).contains("bundleId").contains("bundle id is A12345");

		PlanFormulationResult secondTurn = mockPlanner.formulatePlan("bundle id is A12345", retryState);
		assertThat(secondTurn.plan().planSteps().getFirst()).isInstanceOf(PlanStep.ActionStep.class);
	}
}
