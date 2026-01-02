package org.javai.springai.actions.exec;

import static org.assertj.core.api.Assertions.assertThat;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.javai.springai.actions.DefaultPlanExecutor;
import org.javai.springai.actions.Plan;
import org.javai.springai.actions.PlanExecutionResult;
import org.javai.springai.actions.PlanStep;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.api.ActionParam;
import org.javai.springai.actions.api.FromContext;
import org.javai.springai.actions.internal.bind.ActionBinding;
import org.javai.springai.actions.internal.plan.PlanArgument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DefaultPlanExecutor}.
 */
@DisplayName("DefaultPlanExecutor")
class DefaultPlanExecutorTest {

	private DefaultPlanExecutor executor;

	@BeforeEach
	void setUp() {
		executor = DefaultPlanExecutor.builder()
				.onPending((plan, ctx) -> PlanExecutionResult.notExecuted(plan, ctx, "pending"))
				.onError((plan, ctx) -> PlanExecutionResult.notExecuted(plan, ctx, "error"))
				.onNoAction(PlanExecutionResult::notExecuted)
				.build();
	}

	@Nested
	@DisplayName("@FromContext annotation")
	class FromContextAnnotation {

		@Test
		@DisplayName("should inject value from context when @FromContext parameter is present")
		void shouldInjectFromContext() throws Exception {
			// Given: a context with a basket map
			ActionContext context = new ActionContext();
			Map<String, Integer> basket = Map.of("SKU-001", 3, "SKU-002", 5);
			context.put("basket", basket);

			// And: an action that uses @FromContext
			TestActionsWithFromContext actions = new TestActionsWithFromContext();
			Method method = TestActionsWithFromContext.class.getMethod("getBasketSize", Map.class);
			ActionBinding binding = new ActionBinding(
					"getBasketSize", "Get the number of items in the basket",
					actions, method, List.of(), "");

			PlanStep.ActionStep step = new PlanStep.ActionStep(binding, List.of());
			Plan plan = new Plan("test", List.of(step));

			// When: executing the plan
			PlanExecutionResult result = executor.execute(plan, context);

			// Then: the action received the basket from context
			assertThat(result.success()).isTrue();
			assertThat(actions.lastBasketSize).isEqualTo(2);
		}

		@Test
		@DisplayName("should fail gracefully when context key is missing")
		void shouldFailWhenContextKeyMissing() throws Exception {
			// Given: an empty context (no basket)
			ActionContext context = new ActionContext();

			// And: an action that requires @FromContext("basket")
			TestActionsWithFromContext actions = new TestActionsWithFromContext();
			Method method = TestActionsWithFromContext.class.getMethod("getBasketSize", Map.class);
			ActionBinding binding = new ActionBinding(
					"getBasketSize", "Get the number of items in the basket",
					actions, method, List.of(), "");

			PlanStep.ActionStep step = new PlanStep.ActionStep(binding, List.of());
			Plan plan = new Plan("test", List.of(step));

			// When: executing the plan
			PlanExecutionResult result = executor.execute(plan, context);

			// Then: execution fails with a clear error
			assertThat(result.success()).isFalse();
			assertThat(result.steps()).hasSize(1);
			assertThat(result.steps().getFirst().message())
					.contains("Missing context value for @FromContext key: basket");
		}

		@Test
		@DisplayName("should support mixed parameters: @FromContext with regular params")
		void shouldSupportMixedParameters() throws Exception {
			// Given: a context with basket
			ActionContext context = new ActionContext();
			Map<String, Integer> basket = Map.of("SKU-001", 3);
			context.put("basket", basket);

			// And: an action with mixed parameter types
			TestActionsWithFromContext actions = new TestActionsWithFromContext();
			Method method = TestActionsWithFromContext.class.getMethod(
					"addToBasket", Map.class, String.class, int.class);
			ActionBinding binding = new ActionBinding(
					"addToBasket", "Add a product to the basket",
					actions, method, List.of(), "");

			// Arguments from the plan (product and quantity)
			List<PlanArgument> arguments = List.of(
					new PlanArgument("product", "Coke Zero", String.class),
					new PlanArgument("quantity", 5, int.class));

			PlanStep.ActionStep step = new PlanStep.ActionStep(binding, arguments);
			Plan plan = new Plan("test", List.of(step));

			// When: executing the plan
			PlanExecutionResult result = executor.execute(plan, context);

			// Then: the action received both context and plan parameters
			assertThat(result.success()).isTrue();
			assertThat(actions.lastProduct).isEqualTo("Coke Zero");
			assertThat(actions.lastQuantity).isEqualTo(5);
			assertThat(actions.lastBasketSize).isEqualTo(1);
		}

		@Test
		@DisplayName("should support ActionContext alongside @FromContext")
		void shouldSupportActionContextWithFromContext() throws Exception {
			// Given: a context with session data
			ActionContext context = new ActionContext();
			context.put("sessionId", "SESSION-456");

			// And: an action that takes both ActionContext and @FromContext
			TestActionsWithFromContext actions = new TestActionsWithFromContext();
			Method method = TestActionsWithFromContext.class.getMethod(
					"logSession", ActionContext.class, String.class);
			ActionBinding binding = new ActionBinding(
					"logSession", "Log session information",
					actions, method, List.of(), "");

			PlanStep.ActionStep step = new PlanStep.ActionStep(binding, List.of());
			Plan plan = new Plan("test", List.of(step));

			// When: executing the plan
			PlanExecutionResult result = executor.execute(plan, context);

			// Then: both were injected correctly
			assertThat(result.success()).isTrue();
			assertThat(actions.lastSessionId).isEqualTo("SESSION-456");
			assertThat(actions.receivedContext).isSameAs(context);
		}
	}

	/**
	 * Test actions class with @FromContext parameters.
	 */
	public static class TestActionsWithFromContext {

		// Tracking fields for assertions
		int lastBasketSize;
		String lastProduct;
		int lastQuantity;
		String lastSessionId;
		ActionContext receivedContext;

		@Action(description = "Get the number of items in the basket")
		public int getBasketSize(@FromContext("basket") Map<String, Integer> basket) {
			lastBasketSize = basket.size();
			return basket.size();
		}

		@Action(description = "Add a product to the basket")
		public String addToBasket(
				@FromContext("basket") Map<String, Integer> basket,
				@ActionParam(description = "Product name") String product,
				@ActionParam(description = "Quantity") int quantity) {
			lastBasketSize = basket.size();
			lastProduct = product;
			lastQuantity = quantity;
			return "Added " + quantity + " x " + product;
		}

		@Action(description = "Log session information")
		public void logSession(
				ActionContext context,
				@FromContext("sessionId") String sessionId) {
			receivedContext = context;
			lastSessionId = sessionId;
		}
	}
}
