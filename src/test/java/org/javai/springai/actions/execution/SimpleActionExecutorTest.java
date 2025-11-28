package org.javai.springai.actions.execution;

import static org.assertj.core.api.Assertions.assertThat;
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

		ExecutableAction action1 = ctx -> {
			firstContext.set(ctx);
			return new ActionResult.Success(null);
		};
		ExecutableAction action2 = ctx -> {
			secondContext.set(ctx);
			ctx.put("executed", true);
			return new ActionResult.Success(null);
		};
		ExecutablePlan plan = new ExecutablePlan(List.of(action1, action2));

		executor.execute(plan);

		assertThat(firstContext.get()).isNotNull();
		assertThat(firstContext.get()).isSameAs(secondContext.get());
		assertThat(secondContext.get().contains("executed")).isTrue();
	}
}

