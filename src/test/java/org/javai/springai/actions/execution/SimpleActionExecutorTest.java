package org.javai.springai.actions.execution;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.javai.springai.actions.api.ActionContext;
import org.junit.jupiter.api.Test;

class SimpleActionExecutorTest {

	@Test
	void executeRunsActionsWithSharedContext() throws Exception {
		DefaultPlanExecutor executor = new DefaultPlanExecutor();
		AtomicReference<ActionContext> firstContext = new AtomicReference<>();
		AtomicReference<ActionContext> secondContext = new AtomicReference<>();

		ExecutableAction action1 = firstContext::set;
		ExecutableAction action2 = ctx -> {
			secondContext.set(ctx);
			ctx.put("executed", true);
		};
		ExecutablePlan plan = new ExecutablePlan(List.of(action1, action2));

		executor.execute(plan);

		assertNotNull(firstContext.get());
		assertSame(firstContext.get(), secondContext.get());
		assertTrue(secondContext.get().contains("executed"));
	}
}

