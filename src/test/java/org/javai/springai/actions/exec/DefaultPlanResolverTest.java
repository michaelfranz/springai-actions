package org.javai.springai.actions.exec;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.internal.bind.ActionRegistry;
import org.javai.springai.actions.internal.parse.RawPlan;
import org.javai.springai.actions.internal.resolve.DefaultPlanResolver;
import org.javai.springai.actions.internal.parse.RawPlanStep;
import org.javai.springai.actions.Plan;
import org.javai.springai.actions.PlanStatus;
import org.javai.springai.actions.PlanStep;
import org.javai.springai.actions.sql.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for DefaultPlanResolver.
 * Plans use JSON format with standard SQL strings for Query parameters.
 */
class DefaultPlanResolverTest {

	private ActionRegistry registry;
	private DefaultPlanResolver resolver;

	@BeforeEach
	void setup() {
		registry = new ActionRegistry();
		registry.registerActions(new SampleActions());
		resolver = new DefaultPlanResolver();
	}

	@Test
	void resolvesHappyPath() {
		RawPlan jsonPlan = new RawPlan(
				"greeting plan",
				List.of(new RawPlanStep("greet", "Say hello", Map.of("name", "Bob", "times", 3)))
		);

		Plan result = resolver.resolve(jsonPlan, registry);

		assertThat(result.planSteps()).hasSize(1);
		assertThat(result.planSteps().getFirst()).isInstanceOf(PlanStep.ActionStep.class);
		PlanStep.ActionStep step = (PlanStep.ActionStep) result.planSteps().getFirst();
		assertThat(step.binding().id()).isEqualTo("greet");
		assertThat(step.arguments()).hasSize(2);
		assertThat(step.arguments().get(0).value()).isEqualTo("Bob");
		assertThat(step.arguments().get(1).value()).isEqualTo(3);
	}

	@Test
	void failsOnUnknownAction() {
		RawPlan jsonPlan = new RawPlan(
				"",
				List.of(new RawPlanStep("unknownAction", "Unknown", Map.of()))
		);

		Plan result = resolver.resolve(jsonPlan, registry);
		assertThat(result.status()).isEqualTo(PlanStatus.ERROR);
		assertThat(result.planSteps()).hasSize(1);
		assertThat(result.planSteps().getFirst()).isInstanceOf(PlanStep.ErrorStep.class);
	}

	@Test
	void failsOnArityMismatch() {
		RawPlan jsonPlan = new RawPlan(
				"",
				List.of(new RawPlanStep("greet", "Say hello", Map.of("name", "Bob")))  // missing 'times'
		);

		Plan result = resolver.resolve(jsonPlan, registry);
		assertThat(result.status()).isEqualTo(PlanStatus.ERROR);
	}

	@Test
	void failsOnTypeConversionError() {
		RawPlan jsonPlan = new RawPlan(
				"",
				List.of(new RawPlanStep("greet", "Say hello", Map.of("name", "Bob", "times", "not-a-number")))
		);

		Plan result = resolver.resolve(jsonPlan, registry);
		assertThat(result.status()).isEqualTo(PlanStatus.ERROR);
	}

	@Test
	void resolvesListParameterValue() {
		RawPlan jsonPlan = new RawPlan(
				"",
				List.of(new RawPlanStep("handleList", "Handle bundles", Map.of("bundleIds", List.of("A12345", "A3145"))))
		);

		Plan result = resolver.resolve(jsonPlan, registry);

		PlanStep.ActionStep step = (PlanStep.ActionStep) result.planSteps().getFirst();
		assertThat(step.arguments()).hasSize(1);
		assertThat(step.arguments().getFirst().value()).isInstanceOf(List.class);
		assertThat(step.arguments().getFirst().value())
				.asInstanceOf(InstanceOfAssertFactories.list(String.class))
				.containsExactly("A12345", "A3145");
	}

	@Test
	void convertsListToArrayParameterValue() {
		RawPlan jsonPlan = new RawPlan(
				"",
				List.of(new RawPlanStep("handleArray", "Handle array", Map.of("bundleIds", List.of("A12345", "A3145", "B4323"))))
		);

		Plan result = resolver.resolve(jsonPlan, registry);

		PlanStep.ActionStep step = (PlanStep.ActionStep) result.planSteps().getFirst();
		Object value = step.arguments().getFirst().value();
		assertThat(value).isInstanceOf(String[].class);
		assertThat((String[]) value).containsExactly("A12345", "A3145", "B4323");
	}

	@Test
	void convertsStringToNumericTypes() {
		RawPlan jsonPlan = new RawPlan(
				"",
				List.of(new RawPlanStep("useNumbers", "Use numbers", Map.of("amount", "3.14", "count", "42")))
		);

		Plan result = resolver.resolve(jsonPlan, registry);
		assertThat(result.status()).isEqualTo(PlanStatus.READY);
		PlanStep.ActionStep step = (PlanStep.ActionStep) result.planSteps().getFirst();
		assertThat(step.arguments().get(0).value()).isInstanceOf(Double.class).isEqualTo(3.14d);
		assertThat(step.arguments().get(1).value()).isInstanceOf(Integer.class).isEqualTo(42);
	}

	@Test
	void convertsStringToEnum() {
		RawPlan jsonPlan = new RawPlan(
				"",
				List.of(new RawPlanStep("usePriority", "Set priority", Map.of("priority", "HIGH")))
		);

		Plan result = resolver.resolve(jsonPlan, registry);
		assertThat(result.status()).isEqualTo(PlanStatus.READY);
		PlanStep.ActionStep step = (PlanStep.ActionStep) result.planSteps().getFirst();
		assertThat(step.arguments().getFirst().value()).isInstanceOf(SampleActions.Priority.class)
				.isEqualTo(SampleActions.Priority.HIGH);
	}

	@Test
	void convertsStringsToEnumArray() {
		RawPlan jsonPlan = new RawPlan(
				"",
				List.of(new RawPlanStep("usePriorityArray", "Set priorities", Map.of("priorities", List.of("LOW", "MEDIUM"))))
		);

		Plan result = resolver.resolve(jsonPlan, registry);
		assertThat(result.status()).isEqualTo(PlanStatus.READY);
		PlanStep.ActionStep step = (PlanStep.ActionStep) result.planSteps().getFirst();
		Object value = step.arguments().getFirst().value();
		assertThat(value).isInstanceOf(SampleActions.Priority[].class);
		assertThat((SampleActions.Priority[]) value).containsExactly(SampleActions.Priority.LOW, SampleActions.Priority.MEDIUM);
	}

	@Test
	void failsOnInvalidEnumValue() {
		RawPlan jsonPlan = new RawPlan(
				"",
				List.of(new RawPlanStep("usePriority", "Set priority", Map.of("priority", "BLUE")))
		);

		Plan result = resolver.resolve(jsonPlan, registry);
		assertThat(result.status()).isEqualTo(PlanStatus.ERROR);
		assertThat(result.planSteps().getFirst()).isInstanceOf(PlanStep.ErrorStep.class);
	}

	@Test
	void convertsMapToRecordWithNestedPeriod() {
		Map<String, Object> period = Map.of("start", "2024-01-01", "end", "2024-01-31");
		Map<String, Object> payload = Map.of("customer_name", "Mike", "period", period);
		RawPlan jsonPlan = new RawPlan(
				"",
				List.of(new RawPlanStep("useOrderValue", "Calculate order value", Map.of("orderValueQuery", payload)))
		);

		Plan result = resolver.resolve(jsonPlan, registry);
		assertThat(result.status()).isEqualTo(PlanStatus.READY);

		PlanStep.ActionStep step = (PlanStep.ActionStep) result.planSteps().getFirst();
		assertThat(step.arguments()).hasSize(1);
		assertThat(step.arguments().getFirst().value()).isInstanceOf(SampleActions.OrderValueQuery.class);

		SampleActions.OrderValueQuery query = (SampleActions.OrderValueQuery) step.arguments().getFirst().value();
		assertThat(query.customer_name()).isEqualTo("Mike");
		assertThat(query.period().start()).isEqualTo(LocalDate.parse("2024-01-01"));
		assertThat(query.period().end()).isEqualTo(LocalDate.parse("2024-01-31"));
	}

	@Test
	void convertsSqlStringToQuery() {
		String sqlString = "SELECT customer_name FROM customers WHERE id = '123'";
		
		RawPlan jsonPlan = new RawPlan(
				"",
				List.of(new RawPlanStep("runQuery", "Execute query", Map.of("query", sqlString)))
		);

		Plan result = resolver.resolve(jsonPlan, registry);
		assertThat(result.status()).isEqualTo(PlanStatus.READY);

		PlanStep.ActionStep step = (PlanStep.ActionStep) result.planSteps().getFirst();
		assertThat(step.arguments()).hasSize(1);
		assertThat(step.arguments().getFirst().value()).isInstanceOf(Query.class);

		Query query = (Query) step.arguments().getFirst().value();
		assertThat(query).isNotNull();
		assertThat(query.sqlString()).contains("customers").contains("customer_name");
	}

	@Test
	void convertsComplexSqlStringToQuery() {
		String sqlString = "SELECT o.id, o.status, c.name FROM orders o JOIN customers c ON o.customer_id = c.id WHERE o.status = 'PENDING'";
		
		RawPlan jsonPlan = new RawPlan(
				"",
				List.of(new RawPlanStep("runQuery", "Execute query", Map.of("query", sqlString)))
		);

		Plan result = resolver.resolve(jsonPlan, registry);
		assertThat(result.status()).isEqualTo(PlanStatus.READY);

		PlanStep.ActionStep step = (PlanStep.ActionStep) result.planSteps().getFirst();
		Query query = (Query) step.arguments().getFirst().value();
		assertThat(query).isNotNull();
		assertThat(query.sqlString()).contains("orders").contains("customers");
	}

	@Test
	void failsOnInvalidSqlString() {
		String invalidSql = "SELECT * FROM WHERE"; // Invalid SQL
		
		RawPlan jsonPlan = new RawPlan(
				"",
				List.of(new RawPlanStep("runQuery", "Execute query", Map.of("query", invalidSql)))
		);

		Plan result = resolver.resolve(jsonPlan, registry);
		assertThat(result.status()).isEqualTo(PlanStatus.ERROR);
		assertThat(result.planSteps().getFirst()).isInstanceOf(PlanStep.ErrorStep.class);
	}

	@Test
	void failsOnNonSelectStatement() {
		String insertSql = "INSERT INTO customers (name) VALUES ('Test')";
		
		RawPlan jsonPlan = new RawPlan(
				"",
				List.of(new RawPlanStep("runQuery", "Execute query", Map.of("query", insertSql)))
		);

		Plan result = resolver.resolve(jsonPlan, registry);
		assertThat(result.status()).isEqualTo(PlanStatus.ERROR);
		assertThat(result.planSteps().getFirst()).isInstanceOf(PlanStep.ErrorStep.class);
	}

	private static class SampleActions {
		@Action(description = "Say hello")
		public void greet(String name, Integer times) {
		}

		@Action(description = "Handle bundle list")
		public void handleList(List<String> bundleIds) {
		}

		@Action(description = "Handle bundle array")
		public void handleArray(String[] bundleIds) {
		}

		@Action(description = "Use numeric types")
		public void useNumbers(double amount, int count) {
		}

		@Action(description = "Use enum type")
		public void usePriority(Priority priority) {
		}

		@Action(description = "Use enum array")
		public void usePriorityArray(Priority[] priorities) {
		}

		@Action(description = "Use order value record")
		public void useOrderValue(OrderValueQuery orderValueQuery) {
		}

		@Action(description = "Run a SQL query")
		public void runQuery(Query query) {
		}

		enum Priority {
			HIGH, MEDIUM, LOW
		}

		record OrderValueQuery(String customer_name, Period period) {
		}

		record Period(LocalDate start, LocalDate end) {
		}
	}
}
