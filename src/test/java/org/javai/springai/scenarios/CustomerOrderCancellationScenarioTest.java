package org.javai.springai.scenarios;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import org.javai.springai.actions.tuning.LlmTuningConfig;
import org.javai.springai.actions.tuning.PlanSupplier;
import org.javai.springai.actions.tuning.ScenarioPlanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

class CustomerOrderCancellationScenarioTest implements ScenarioPlanSupplier {

	private static final String CUSTOMER_SERVICE_PERSONA = "You are a customer service agent.";

	private static final String OPENAI_API_KEY = System.getenv("OPENAI_API_KEY");

	private static final ContextKey<Order> UPDATED_ORDER_KEY = ContextKey.of("updatedOrder", Order.class);
	private static final ContextKey<String> CANCELLATION_EMAIL_KEY = ContextKey.of("cancellationEmail", String.class);

	private static final LlmTuningConfig BASELINE_CONFIG = new LlmTuningConfig(
			CUSTOMER_SERVICE_PERSONA,
			0.2,
			0.95
	);

	private final MockOrderRepository repository = new MockOrderRepository();
	private String lastCustomerNameToolResult;

	@BeforeEach
	void setUp() {
		ensureApiKeyPresent();
		this.lastCustomerNameToolResult = null;
	}

	@Override
	public String scenarioId() {
		return "customer-order-cancellation";
	}

	@Override
	public String description() {
		return "Fetch the most recent order for a customer and cancel it, sending a confirmation email. " +
				"The scenario must call the customerName tool to get an email-friendly name for the greeting, " +
				"then pass that to the cancelOrderAndNotify action with the reason 'At the customer's request'. " +
				"The fetchLatestOrder action must be called before cancelOrderAndNotify to retrieve order details.";
	}

	@Override
	public LlmTuningConfig defaultConfig() {
		return BASELINE_CONFIG;
	}

	@Override
	public PlanSupplier planSupplier(LlmTuningConfig config) {
		LlmTuningConfig effective = config != null ? config : defaultConfig();
		return () -> createPlan(effective);
	}

	@Test
	void cancelMostRecentOrderForCustomer() throws PlanExecutionException {
		ExecutablePlan plan = planSupplier().get();

		PlanExecutor executor = new DefaultPlanExecutor();
		ActionContext context = executor.execute(plan);

		Order updatedOrder = context.get(UPDATED_ORDER_KEY);
		assertThat(updatedOrder.status()).isEqualTo("CANCELLED");
		assertThat(updatedOrder.customerId()).isEqualTo("123");

		String email = context.get(CANCELLATION_EMAIL_KEY);
		assertThat(lastCustomerNameToolResult).isNotBlank();
		assertThat(email)
				.contains("Order " + updatedOrder.orderId())
				.contains("status set to CANCELLED")
				.contains(lastCustomerNameToolResult);
	}

	private ExecutablePlan createPlan(LlmTuningConfig config) {
		PlanningChatClient client = createPlanningClient(config);
		this.lastCustomerNameToolResult = null;

		String persona = (config.systemPrompt() == null || config.systemPrompt().isBlank())
				? CUSTOMER_SERVICE_PERSONA
				: config.systemPrompt();

		return client
				.prompt()
				.system("""
						You must call the customerName tool with the same customerId to obtain the email-friendly name.
						Use that tool result to populate the friendlyCustomerName argument of cancelOrderAndNotify.
						""")
				.user(persona)
				.user("""
						For customer 123, fetch the most recent order and cancel it.
						Provide the reason for cancellation as: 'At the customer's request'.
						After cancellation, send an email confirming the change and mention the updated status.
						Always use the fetchLatestOrder action before cancelOrderAndNotify.
						Ensure the email greeting uses the friendlyCustomerName argument that came from the customerName tool call.
						""")
				.tools(this)
				.actions(this)
				.plan();
	}

	private PlanningChatClient createPlanningClient(LlmTuningConfig config) {
		ensureApiKeyPresent();
		OpenAiApi openAiApi = OpenAiApi.builder().apiKey(OPENAI_API_KEY).build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder().openAiApi(openAiApi).build();
		OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder().model("gpt-4.1-mini");

		if (config.temperature() != null) {
			optionsBuilder.temperature(config.temperature());
		}
		if (config.topP() != null) {
			optionsBuilder.topP(config.topP());
		}

		ChatClient springAiChatClient = ChatClient.builder(Objects.requireNonNull(chatModel))
				.defaultOptions(Objects.requireNonNull(optionsBuilder.build()))
				.build();

		return new PlanningChatClient(springAiChatClient);
	}

	private void ensureApiKeyPresent() {
		if (OPENAI_API_KEY == null || OPENAI_API_KEY.isBlank()) {
			throw new IllegalStateException(
					"Missing OPENAI_API_KEY environment variable. Please export OPENA_API_KEY before running the tests.");
		}
	}

	@Tool(description = "Return an email-friendly customer name for the given customer id")
	public String customerName(
			@ToolParam(description = "The id of the customer") String customerId) {
		String friendlyName = repository.findFriendlyName(customerId)
				.orElse("Valued customer #" + customerId);
		this.lastCustomerNameToolResult = friendlyName;
		return friendlyName;
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
			@ActionParam(description = "Email-friendly customer name returned by the customerName tool") String friendlyCustomerName,
			@FromContext("latestOrder") Order latestOrder,
			ActionContext ctx
	) {
		Order cancelled = latestOrder.withStatus("CANCELLED");
		repository.log("Cancelled order %s with reason %s".formatted(cancelled.orderId(), reason));
		repository.save(cancelled);

		String greetingName = (friendlyCustomerName != null && !friendlyCustomerName.isBlank())
				? friendlyCustomerName
				: latestOrder.customerId();

		String email = """
				To: %s
				Subject: Order %s Cancelled

				Hello %s,

				We have set the status of order %s to CANCELLED for the following reason: %s.
				The status set to CANCELLED will be reflected immediately.
				""".formatted(cancelled.customerEmail(), cancelled.orderId(), greetingName, cancelled.orderId(), reason);

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
		private final Map<String, String> friendlyNames = new ConcurrentHashMap<>();

		MockOrderRepository() {
			orders.put("123", List.of(
					new Order("A-1", "123", "PLACED", LocalDateTime.now().minusDays(3), "customer123@example.com"),
					new Order("A-2", "123", "PLACED", LocalDateTime.now().minusDays(1), "customer123@example.com")
			));
			friendlyNames.put("123", "Alex \"Red\" Thompson");
		}

		Optional<Order> findLatestOrder(String customerId) {
			return Optional.ofNullable(orders.get(customerId))
					.flatMap(list -> list.stream().max(Comparator.comparing(Order::createdAt)));
		}

		Optional<String> findFriendlyName(String customerId) {
			return Optional.ofNullable(friendlyNames.get(customerId));
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

