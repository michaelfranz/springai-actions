package org.javai.springai.scenarios;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.api.ActionParam;
import org.javai.springai.actions.api.ContextKey;
import org.javai.springai.actions.api.FromContext;
import org.javai.springai.actions.api.Mutability;
import org.javai.springai.actions.execution.DefaultPlanExecutor;
import org.javai.springai.actions.execution.ExecutablePlan;
import org.javai.springai.actions.execution.PlanExecutionException;
import org.javai.springai.actions.execution.PlanExecutor;
import org.javai.springai.actions.planning.PlanningChatClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

class CustomerOrderCancellationScenarioTest {

	private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");

	private static final ContextKey<Order> LATEST_ORDER_KEY = ContextKey.of("latestOrder", Order.class);
	private static final ContextKey<Order> UPDATED_ORDER_KEY = ContextKey.of("updatedOrder", Order.class);
	private static final ContextKey<String> CANCELLATION_EMAIL_KEY = ContextKey.of("cancellationEmail", String.class);

	private PlanningChatClient chatClient;
	private final MockOrderRepository repository = new MockOrderRepository();

	@BeforeEach
	void setUp() {
		if (OPENAI_API_KEY == null || OPENAI_API_KEY.isBlank()) {
			throw new IllegalStateException("Missing OPENAI_API_KEY environment variable. Please export OPENA_API_KEY before running the tests.");
		}
		OpenAiApi openAiApi = OpenAiApi.builder().apiKey(OPENAI_API_KEY).build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder().openAiApi(openAiApi).build();
		OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder().model("gpt-4.1-mini");
		optionsBuilder.temperature(0.2);

		ChatClient springAiChatClient = ChatClient.builder(chatModel)
				.defaultOptions(optionsBuilder.build())
				.build();

		this.chatClient = new PlanningChatClient(springAiChatClient);
	}

	@Test
	void cancelMostRecentOrderForCustomer() throws PlanExecutionException {
		ExecutablePlan plan = chatClient
				.prompt()
				.user("""
						You are a customer service agent. For customer 123, fetch the most recent order and cancel it.
						After cancellation, send an email confirming the change and mention the updated status.
						Always use the fetchLatestOrder action before cancelOrderAndNotify.
						""")
				.actions(this)
				.plan();

		assertThat(plan.executables())
				.hasSize(2);

		PlanExecutor executor = new DefaultPlanExecutor();
		ActionContext context = executor.execute(plan);

		Order updatedOrder = context.get(UPDATED_ORDER_KEY);
		assertThat(updatedOrder.status()).isEqualTo("CANCELLED");
		assertThat(updatedOrder.customerId()).isEqualTo("123");

		String email = context.get(CANCELLATION_EMAIL_KEY);
		assertThat(email)
				.contains("Order " + updatedOrder.orderId())
				.contains("status set to CANCELLED");
	}

	@Action(description = "Fetch the most recent order for a customer",
			contextKey = "latestOrder",
			mutability = Mutability.READ_ONLY,
			affinity = "customer:{customerId}")
	public Order fetchLatestOrder(
			@ActionParam(description = "The customer identifier") String customerId
	) {
		return repository.findLatestOrder(customerId)
				.map(order -> {
					repository.log("Fetched order %s for customer %s".formatted(order.orderId(), customerId));
					return order;
				})
				.orElseThrow(() -> new IllegalStateException("No orders found for customer " + customerId));
	}

	@Action(description = "Cancel an order and notify the customer",
			contextKey = "updatedOrder",
			additionalContextKeys = { "cancellationEmail" },
			mutability = Mutability.MUTATE,
			affinity = "order:{latestOrder.orderId}")
	public Order cancelOrderAndNotify(
			@ActionParam(description = "Reason for cancellation") String reason,
			@FromContext("latestOrder") Order latestOrder,
			ActionContext ctx
	) {
		Order cancelled = latestOrder.withStatus("CANCELLED");
		repository.log("Cancelled order %s with reason %s".formatted(cancelled.orderId(), reason));
		repository.save(cancelled);

		String email = """
				To: %s
				Subject: Order %s Cancelled

				Hello %s,

				We have set the status of order %s to CANCELLED for the following reason: %s.
				The status set to CANCELLED will be reflected immediately.
				""".formatted(cancelled.customerEmail(), cancelled.orderId(), cancelled.customerId(), cancelled.orderId(), reason);

		ctx.put(CANCELLATION_EMAIL_KEY, email);
		return cancelled;
	}

	public record Order(
			String orderId,
			String customerId,
			String status,
			LocalDateTime createdAt,
			String customerEmail
	) {
		public Order withStatus(String updatedStatus) {
			return new Order(orderId, customerId, updatedStatus, createdAt, customerEmail);
		}
	}

	static class MockOrderRepository {

		private final Map<String, List<Order>> orders = new ConcurrentHashMap<>();

		MockOrderRepository() {
			orders.put("123", List.of(
					new Order("A-1", "123", "PLACED", LocalDateTime.now().minusDays(3), "customer123@example.com"),
					new Order("A-2", "123", "PLACED", LocalDateTime.now().minusDays(1), "customer123@example.com")
			));
		}

		Optional<Order> findLatestOrder(String customerId) {
			return Optional.ofNullable(orders.get(customerId))
					.flatMap(list -> list.stream().max(Comparator.comparing(Order::createdAt)));
		}

		void save(Order order) {
			orders.compute(order.customerId(), (k, list) -> {
				List<Order> existing = list != null ? list : List.of();
				return existing.stream()
						.map(o -> o.orderId().equals(order.orderId()) ? order : o)
						.toList();
			});
		}

		void log(String message) {
			System.out.println("[MockOrderRepository] " + message);
		}
	}
}

