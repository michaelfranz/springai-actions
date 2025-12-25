package org.javai.springai.dsl.exec;

import static org.assertj.core.api.Assertions.assertThat;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.javai.springai.dsl.act.ActionBinding;
import org.javai.springai.dsl.act.ActionParameterDescriptor;
import org.junit.jupiter.api.Test;

class DefaultPlanExecutorTest {

	@Test
	void executesActionStep() throws Exception {
		AtomicBoolean invoked = new AtomicBoolean(false);
		SampleActions actions = new SampleActions(invoked);

		Method method = SampleActions.class.getMethod("hello", String.class);
		ActionBinding binding = new ActionBinding(
				"hello",
				"Say hello",
				actions,
				method,
				List.of(new ActionParameterDescriptor("name", String.class.getName(), "string", "Name", null,
						new String[0], "", false))
		);

		ResolvedStep.ActionStep step = new ResolvedStep.ActionStep(binding,
				List.of(new ResolvedArgument("name", "world", String.class)));
		ResolvedPlan plan = new ResolvedPlan(List.of(step));

		DefaultPlanExecutor executor = new DefaultPlanExecutor();
		PlanExecutionResult result = executor.execute(plan);

		assertThat(result.success()).isTrue();
		assertThat(result.steps()).hasSize(1);
		assertThat(invoked.get()).isTrue();
	}

	@Test
	void stopsOnErrorStep() {
		ResolvedStep.ErrorStep error = new ResolvedStep.ErrorStep("missing info");
		ResolvedPlan plan = new ResolvedPlan(List.of(error));

		DefaultPlanExecutor executor = new DefaultPlanExecutor();
		PlanExecutionResult result = executor.execute(plan);

		assertThat(result.success()).isFalse();
		assertThat(result.steps()).hasSize(1);
		assertThat(result.steps().getFirst().message()).contains("missing info");
	}

	private record SampleActions(AtomicBoolean invoked) {

		@SuppressWarnings("unused") // invoked via reflection in executor
			public void hello(String name) {
				invoked.set(true);
			}
		}
}

