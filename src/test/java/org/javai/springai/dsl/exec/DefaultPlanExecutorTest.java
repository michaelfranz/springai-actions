package org.javai.springai.dsl.exec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.execution.ActionResult;
import org.javai.springai.actions.execution.ExecutableAction;
import org.javai.springai.actions.execution.ExecutablePlan;
import org.javai.springai.actions.execution.PlanExecutionException;
import org.junit.jupiter.api.Test;

class DefaultPlanExecutorTest {

	@Test
	void executesActionStep() throws Exception {
		AtomicBoolean invoked = new AtomicBoolean(false);
		ExecutableAction action = ctx -> {
			invoked.set(true);
			return new ActionResult.Success("ok");
		};

		ExecutablePlan plan = new ExecutablePlan(List.of(action));

		org.javai.springai.actions.execution.DefaultPlanExecutor executor = new org.javai.springai.actions.execution.DefaultPlanExecutor();
		ActionContext context = executor.execute(plan);

		assertThat(invoked.get()).isTrue();
		assertThat(context).isNotNull();
	}

	@Test
	void stopsOnActionFailure() {
		ExecutableAction failing = ctx -> {
			throw new IllegalStateException("boom");
		};

		ExecutablePlan plan = new ExecutablePlan(List.of(failing));

		org.javai.springai.actions.execution.DefaultPlanExecutor executor = new org.javai.springai.actions.execution.DefaultPlanExecutor();
		assertThatThrownBy(() -> executor.execute(plan))
				.isInstanceOf(PlanExecutionException.class)
				.hasMessageContaining("Plan execution failed");
	}
}

