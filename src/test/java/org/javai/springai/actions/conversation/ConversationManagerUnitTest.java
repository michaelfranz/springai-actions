package org.javai.springai.actions.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.javai.springai.actions.internal.bind.ActionBinding;
import org.javai.springai.actions.Plan;
import org.javai.springai.actions.internal.plan.PlanFormulationResult;
import org.javai.springai.actions.PlanStatus;
import org.javai.springai.actions.PlanStep;
import org.javai.springai.actions.Planner;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SuppressWarnings({ "DataFlowIssue", "NullAway" })
class ConversationManagerUnitTest {

	@Mock
	private Planner mockPlanner;

	@Mock
	private ConversationStateStore mockStore;

	@Mock
	private ActionBinding mockBinding;

	ConversationManagerUnitTest() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	void constructorRequiresDependencies() {
		assertThatThrownBy(() -> new ConversationManager(null, mockStore)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new ConversationManager(mockPlanner, null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	@SuppressWarnings("NullAway")
	void conversationFlowPendingThenResolved() {
		ConversationManager manager = new ConversationManager(mockPlanner, mockStore);

		// Turn 1: missing bundleId -> pending
		Plan pendingPlan = new Plan("desc",
				List.of(new PlanStep.PendingActionStep("",
						"exportControlChartToExcel",
						new PlanStep.PendingParam[] { new PlanStep.PendingParam("bundleId", "Provide bundle id") },
						Map.of("domainEntity", "displacement", "measurementConcept", "values"))));
		when(mockPlanner.formulatePlan(eq("export control chart to excel for displacement values"), argThat(Objects::nonNull)))
				.thenReturn(new PlanFormulationResult("", pendingPlan, null, false, null));
		when(mockStore.load("session-1")).thenReturn(Optional.of(ConversationState.initial("export control chart to excel for displacement values")));

		ConversationTurnResult first = manager.converse("export control chart to excel for displacement values", "session-1");
		assertThat(first.state().pendingParams()).hasSize(1);
		assertThat(first.plan().status()).isEqualTo(PlanStatus.PENDING);

		// Turn 2: user provides bundleId -> action step with binding
		when(mockBinding.id()).thenReturn("exportControlChartToExcel");
		Plan resolvedPlan = new Plan("desc",
				List.of(new PlanStep.ActionStep(mockBinding, List.of())));
		when(mockPlanner.formulatePlan(eq("bundle id is A12345"), argThat(Objects::nonNull)))
				.thenReturn(new PlanFormulationResult("", resolvedPlan, null, false, null));

		ConversationTurnResult second = manager.converse("bundle id is A12345", "session-1");
		assertThat(second.plan().status()).isEqualTo(PlanStatus.READY);
		assertThat(second.state().pendingParams()).isEmpty();
	}
}
