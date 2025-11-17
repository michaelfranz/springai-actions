package org.javai.springai.actions.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.javai.springai.actions.planning.ActionPlan;
import org.javai.springai.actions.planning.ActionStep;
import org.junit.jupiter.api.Test;

class ActionPlanCompilerTest {

	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	void compileDelegatesEveryStepToFactory() {
		ExecutableActionFactory factory = mock(ExecutableActionFactory.class);
		ActionPlanCompiler compiler = new ActionPlanCompiler(factory);

		ActionStep step1 = new ActionStep("first", mapper.createObjectNode());
		ActionStep step2 = new ActionStep("second", mapper.createObjectNode());
		ActionPlan plan = new ActionPlan(List.of(step1, step2));

		ExecutableAction action1 = ctx -> {};
		ExecutableAction action2 = ctx -> {};
		when(factory.from(step1)).thenReturn(action1);
		when(factory.from(step2)).thenReturn(action2);

		List<ExecutableAction> result = compiler.compile(plan);

		assertEquals(List.of(action1, action2), result);
		verify(factory).from(step1);
		verify(factory).from(step2);
	}
}

