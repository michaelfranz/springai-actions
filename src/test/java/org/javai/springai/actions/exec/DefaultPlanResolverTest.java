package org.javai.springai.actions.exec;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.bind.ActionRegistry;
import org.javai.springai.actions.exec.DefaultPlanResolver;
import org.javai.springai.actions.exec.ResolvedPlan;
import org.javai.springai.actions.exec.ResolvedStep;
import org.javai.springai.actions.plan.PlanStatus;
import org.javai.springai.actions.plan.Plan;
import org.javai.springai.actions.plan.PlanStep;
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
		Plan plan = new Plan(
				"",
				List.of(new PlanStep.ActionStep("", "greet", new Object[] { "Bob", 3 }))
		);

		ResolvedPlan result = resolver.resolve(plan, registry);

		assertThat(result.steps()).hasSize(1);
		assertThat(result.steps().getFirst()).isInstanceOf(ResolvedStep.ActionStep.class);
		ResolvedStep.ActionStep step = (ResolvedStep.ActionStep) result.steps().getFirst();
		assertThat(step.binding().id()).isEqualTo("greet");
		assertThat(step.arguments()).hasSize(2);
		assertThat(step.arguments().get(0).value()).isEqualTo("Bob");
		assertThat(step.arguments().get(1).value()).isEqualTo(3);
	}

	@Test
	void failsOnUnknownAction() {
		Plan plan = new Plan(
				"",
				List.of(new PlanStep.ActionStep("", "unknownAction", new Object[] {}))
		);

		ResolvedPlan result = resolver.resolve(plan, registry);
		assertThat(result.status()).isEqualTo(PlanStatus.ERROR);
		assertThat(result.steps()).hasSize(1);
		assertThat(result.steps().getFirst()).isInstanceOf(ResolvedStep.ErrorStep.class);
	}

	@Test
	void failsOnArityMismatch() {
		Plan plan = new Plan(
				"",
				List.of(new PlanStep.ActionStep("", "greet", new Object[] { "Bob" }))
		);

		ResolvedPlan result = resolver.resolve(plan, registry);
		assertThat(result.status()).isEqualTo(PlanStatus.ERROR);
	}

	@Test
	void failsOnTypeConversionError() {
		Plan plan = new Plan(
				"",
				List.of(new PlanStep.ActionStep("", "greet", new Object[] { "Bob", "not-a-number" }))
		);

		ResolvedPlan result = resolver.resolve(plan, registry);
		assertThat(result.status()).isEqualTo(PlanStatus.ERROR);
	}

	@Test
	void resolvesListParameterValue() {
		Plan plan = new Plan(
				"",
				List.of(new PlanStep.ActionStep("", "handleList", new Object[] { List.of("A12345", "A3145") }))
		);

		ResolvedPlan result = resolver.resolve(plan, registry);

		ResolvedStep.ActionStep step = (ResolvedStep.ActionStep) result.steps().getFirst();
		assertThat(step.arguments()).hasSize(1);
		assertThat(step.arguments().getFirst().value()).isInstanceOf(List.class);
		assertThat(step.arguments().getFirst().value())
				.asInstanceOf(InstanceOfAssertFactories.list(String.class))
				.containsExactly("A12345", "A3145");
	}

	@Test
	void convertsListToArrayParameterValue() {
		Plan plan = new Plan(
				"",
				List.of(new PlanStep.ActionStep("", "handleArray", new Object[] { List.of("A12345", "A3145", "B4323") }))
		);

		ResolvedPlan result = resolver.resolve(plan, registry);

		ResolvedStep.ActionStep step = (ResolvedStep.ActionStep) result.steps().getFirst();
		Object value = step.arguments().getFirst().value();
		assertThat(value).isInstanceOf(String[].class);
		assertThat((String[]) value).containsExactly("A12345", "A3145", "B4323");
	}

	@Test
	void failsOnPendingStep() {
		Plan plan = new Plan(
				"",
				List.of(new PlanStep.PendingActionStep("", "greet",
						new PlanStep.PendingParam[] { new PlanStep.PendingParam("name", "missing name") },
						Map.of()))
		);

		ResolvedPlan result = resolver.resolve(plan, registry);
		assertThat(result.status()).isEqualTo(PlanStatus.PENDING);
		assertThat(result.steps().getFirst()).isInstanceOf(ResolvedStep.PendingActionStep.class);
	}

	@Test
	void convertsStringToNumericTypes() {
		Plan plan = new Plan(
				"",
				List.of(new PlanStep.ActionStep("", "useNumbers", new Object[] { "3.14", "42" }))
		);

		ResolvedPlan result = resolver.resolve(plan, registry);
		assertThat(result.status()).isEqualTo(PlanStatus.READY);
		ResolvedStep.ActionStep step = (ResolvedStep.ActionStep) result.steps().getFirst();
		assertThat(step.arguments().get(0).value()).isInstanceOf(Double.class).isEqualTo(3.14d);
		assertThat(step.arguments().get(1).value()).isInstanceOf(Integer.class).isEqualTo(42);
	}

	@Test
	void convertsStringToEnum() {
		Plan plan = new Plan(
				"",
				List.of(new PlanStep.ActionStep("", "usePriority", new Object[] { "HIGH" }))
		);

		ResolvedPlan result = resolver.resolve(plan, registry);
		assertThat(result.status()).isEqualTo(PlanStatus.READY);
		ResolvedStep.ActionStep step = (ResolvedStep.ActionStep) result.steps().getFirst();
		assertThat(step.arguments().getFirst().value()).isInstanceOf(SampleActions.Priority.class)
				.isEqualTo(SampleActions.Priority.HIGH);
	}

	@Test
	void convertsStringsToEnumArray() {
		Plan plan = new Plan(
				"",
				List.of(new PlanStep.ActionStep("", "usePriorityArray", new Object[] { List.of("LOW", "MEDIUM") }))
		);

		ResolvedPlan result = resolver.resolve(plan, registry);
		assertThat(result.status()).isEqualTo(PlanStatus.READY);
		ResolvedStep.ActionStep step = (ResolvedStep.ActionStep) result.steps().getFirst();
		Object value = step.arguments().getFirst().value();
		assertThat(value).isInstanceOf(SampleActions.Priority[].class);
		assertThat((SampleActions.Priority[]) value).containsExactly(SampleActions.Priority.LOW, SampleActions.Priority.MEDIUM);
	}

	@Test
	void failsOnInvalidEnumValue() {
		Plan plan = new Plan(
				"",
				List.of(new PlanStep.ActionStep("", "usePriority", new Object[] { "BLUE" }))
		);

		ResolvedPlan result = resolver.resolve(plan, registry);
		assertThat(result.status()).isEqualTo(PlanStatus.ERROR);
		assertThat(result.steps().getFirst()).isInstanceOf(ResolvedStep.ErrorStep.class);
	}

	@Test
	void convertsMapToRecordWithNestedPeriod() {
		Map<String, Object> period = Map.of("start", "2024-01-01", "end", "2024-01-31");
		Map<String, Object> payload = Map.of("customer_name", "Mike", "period", period);
		Plan plan = new Plan(
				"",
				List.of(new PlanStep.ActionStep("", "useOrderValue", new Object[] { payload }))
		);

		ResolvedPlan result = resolver.resolve(plan, registry);
		assertThat(result.status()).isEqualTo(PlanStatus.READY);

		ResolvedStep.ActionStep step = (ResolvedStep.ActionStep) result.steps().getFirst();
		assertThat(step.arguments()).hasSize(1);
		assertThat(step.arguments().getFirst().value()).isInstanceOf(SampleActions.OrderValueQuery.class);

		SampleActions.OrderValueQuery query = (SampleActions.OrderValueQuery) step.arguments().getFirst().value();
		assertThat(query.customer_name()).isEqualTo("Mike");
		assertThat(query.period().start()).isEqualTo(LocalDate.parse("2024-01-01"));
		assertThat(query.period().end()).isEqualTo(LocalDate.parse("2024-01-31"));
	}

	@Test
	void convertsSqlStringToQuery() {
		// This test verifies that when a JSON plan contains a SQL string for a Query parameter,
		// the resolver correctly parses it into a Query object.
		
		String sqlString = "SELECT customer_name FROM customers WHERE id = '123'";
		
		Plan plan = new Plan(
				"",
				List.of(new PlanStep.ActionStep("", "runQuery", new Object[] { sqlString }))
		);

		ResolvedPlan result = resolver.resolve(plan, registry);
		assertThat(result.status()).isEqualTo(PlanStatus.READY);

		ResolvedStep.ActionStep step = (ResolvedStep.ActionStep) result.steps().getFirst();
		assertThat(step.arguments()).hasSize(1);
		assertThat(step.arguments().getFirst().value()).isInstanceOf(Query.class);

		Query query = (Query) step.arguments().getFirst().value();
		assertThat(query).isNotNull();
		// Verify the Query can generate SQL (proves it was parsed correctly)
		assertThat(query.sqlString()).contains("customers").contains("customer_name");
	}

	@Test
	void convertsComplexSqlStringToQuery() {
		// More complex SQL string as would appear in a JSON plan
		String sqlString = "SELECT o.id, o.status, c.name FROM orders o JOIN customers c ON o.customer_id = c.id WHERE o.status = 'PENDING'";
		
		Plan plan = new Plan(
				"",
				List.of(new PlanStep.ActionStep("", "runQuery", new Object[] { sqlString }))
		);

		ResolvedPlan result = resolver.resolve(plan, registry);
		assertThat(result.status()).isEqualTo(PlanStatus.READY);

		ResolvedStep.ActionStep step = (ResolvedStep.ActionStep) result.steps().getFirst();
		Query query = (Query) step.arguments().getFirst().value();
		assertThat(query).isNotNull();
		assertThat(query.sqlString()).contains("orders").contains("customers");
	}

	@Test
	void failsOnInvalidSqlString() {
		// Invalid SQL should result in an error
		String invalidSql = "SELECT * FROM WHERE"; // Invalid SQL
		
		Plan plan = new Plan(
				"",
				List.of(new PlanStep.ActionStep("", "runQuery", new Object[] { invalidSql }))
		);

		ResolvedPlan result = resolver.resolve(plan, registry);
		assertThat(result.status()).isEqualTo(PlanStatus.ERROR);
		assertThat(result.steps().getFirst()).isInstanceOf(ResolvedStep.ErrorStep.class);
	}

	@Test
	void failsOnNonSelectStatement() {
		// Non-SELECT statements should result in an error
		String insertSql = "INSERT INTO customers (name) VALUES ('Test')";
		
		Plan plan = new Plan(
				"",
				List.of(new PlanStep.ActionStep("", "runQuery", new Object[] { insertSql }))
		);

		ResolvedPlan result = resolver.resolve(plan, registry);
		assertThat(result.status()).isEqualTo(PlanStatus.ERROR);
		assertThat(result.steps().getFirst()).isInstanceOf(ResolvedStep.ErrorStep.class);
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
