package org.javai.springai.dsl.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Optional;
import org.javai.springai.dsl.exec.PlanResolutionResult;
import org.javai.springai.dsl.exec.PlanResolver;
import org.javai.springai.dsl.exec.ResolvedPlan;
import org.javai.springai.dsl.exec.ResolvedStep;
import org.javai.springai.dsl.plan.Plan;
import org.javai.springai.dsl.plan.PlanFormulationResult;
import org.javai.springai.dsl.plan.PlanStep;
import org.javai.springai.dsl.plan.Planner;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Skeleton unit test for ConversationManager. This will fail until
 * ConversationManager is fully implemented.
 */
class ConversationManagerUnitTest {

	@Mock
	private Planner mockPlanner;

	@Mock
	private PlanResolver mockResolver;

	@Mock
	private ConversationStateStore mockStore;

	ConversationManagerUnitTest() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	void constructorRequiresDependencies() {
		assertThatThrownBy(() -> new ConversationManager(null, mockResolver, mockStore)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new ConversationManager(mockPlanner, null, mockStore)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new ConversationManager(mockPlanner, mockResolver, null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void conversationFlowPendingThenResolved() {
		// Illustrative API showing desired flow; will not compile/run until ConversationManager,
		// Planner, and PlanResolver support ConversationState-based planning.

		ConversationManager manager = new ConversationManager(mockPlanner, mockResolver, mockStore);

		// Turn 1: missing bundleId -> pending
		Plan pendingPlan = new Plan("desc",
				List.of(new PlanStep.PendingActionStep("",
						"exportControlChartToExcel",
						new PlanStep.PendingParam[] { new PlanStep.PendingParam("bundleId", "Provide bundle id") },
						new Object[] { "displacement", "values" })));
		when(mockPlanner.formulatePlan(anyString(), any(ConversationState.class)))
				.thenReturn(new PlanFormulationResult("", pendingPlan, null, false, null));
		when(mockStore.load("session-1")).thenReturn(Optional.of(ConversationState.initial("export control chart to excel for displacement values")));

		ConversationTurnResult first = manager.converse("export control chart to excel for displacement values", "session-1");
		assertThat(first.state().pendingParams()).hasSize(1);
		assertThat(first.plan().planSteps().getFirst()).isInstanceOf(PlanStep.PendingActionStep.class);

		// Turn 2: user provides bundleId -> action
		Plan resolvedPlan = new Plan("desc",
				List.of(new PlanStep.ActionStep("",
						"exportControlChartToExcel",
						new Object[] { "displacement", "values", "A12345" })));
		when(mockPlanner.formulatePlan(anyString(), any(ConversationState.class)))
				.thenReturn(new PlanFormulationResult("", resolvedPlan, null, false, null));
		when(mockResolver.resolve(resolvedPlan, null))
				.thenReturn(PlanResolutionResult.success(new ResolvedPlan(
						List.of(new ResolvedStep.ActionStep(null, List.of())))));

		ConversationTurnResult second = manager.converse("bundle id is A12345", "session-1");
		assertThat(second.plan().planSteps().getFirst()).isInstanceOf(PlanStep.ActionStep.class);
		assertThat(second.state().pendingParams()).isEmpty();
	}
}

