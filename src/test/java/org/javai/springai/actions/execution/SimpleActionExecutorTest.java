package org.javai.springai.actions.execution;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.javai.springai.actions.api.ActionContext;
import org.junit.jupiter.api.Test;

class SimpleActionExecutorTest {

	@Test
	void executeRunsActionsWithSharedContext() throws Exception {
		SimpleActionExecutor executor = new SimpleActionExecutor();
		AtomicReference<ActionContext> firstContext = new AtomicReference<>();
		AtomicReference<ActionContext> secondContext = new AtomicReference<>();

		ExecutableAction action1 = ctx -> firstContext.set(ctx);
		ExecutableAction action2 = ctx -> {
			secondContext.set(ctx);
			ctx.put("executed", true);
		};

		executor.execute(List.of(action1, action2));

		assertNotNull(firstContext.get());
		assertSame(firstContext.get(), secondContext.get());
		assertTrue(secondContext.get().contains("executed"));
	}
}

