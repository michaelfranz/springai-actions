package org.javai.springai.actions.execution;

import static org.mockito.Mockito.*;

import java.util.List;
import org.javai.springai.actions.planning.ActionPlan;
import org.junit.jupiter.api.Test;

class ActionPlanResultTest {

	@Test
	void executeDelegatesToExecutor() throws Exception {
		ActionPlan plan = new ActionPlan(List.of());
		ExecutableAction action = mock(ExecutableAction.class);
		List<ExecutableAction> actions = List.of(action);
		ActionPlanResult result = new ActionPlanResult(plan, actions);
		ActionExecutor executor = mock(ActionExecutor.class);

		result.execute(executor);

		verify(executor).execute(actions);
	}
}

