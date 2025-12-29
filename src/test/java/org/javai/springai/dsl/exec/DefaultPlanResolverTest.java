package org.javai.springai.dsl.exec;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.javai.springai.actions.api.Action;
import org.javai.springai.dsl.act.ActionRegistry;
import org.javai.springai.dsl.plan.Plan;
import org.javai.springai.dsl.plan.PlanStep;
import org.javai.springai.sxl.SxlNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
		assertThat(result.status()).isEqualTo(org.javai.springai.dsl.plan.PlanStatus.ERROR);
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
		assertThat(result.status()).isEqualTo(org.javai.springai.dsl.plan.PlanStatus.ERROR);
	}

	@Test
	void failsOnTypeConversionError() {
		Plan plan = new Plan(
				"",
				List.of(new PlanStep.ActionStep("", "greet", new Object[] { "Bob", "not-a-number" }))
		);

		ResolvedPlan result = resolver.resolve(plan, registry);
		assertThat(result.status()).isEqualTo(org.javai.springai.dsl.plan.PlanStatus.ERROR);
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
		assertThat(result.status()).isEqualTo(org.javai.springai.dsl.plan.PlanStatus.PENDING);
		assertThat(result.steps().getFirst()).isInstanceOf(ResolvedStep.PendingActionStep.class);
	}

	@Test
	void convertsStringToNumericTypes() {
		Plan plan = new Plan(
				"",
				List.of(new PlanStep.ActionStep("", "useNumbers", new Object[] { "3.14", "42" }))
		);

		ResolvedPlan result = resolver.resolve(plan, registry);
		assertThat(result.status()).isEqualTo(org.javai.springai.dsl.plan.PlanStatus.READY);
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
		assertThat(result.status()).isEqualTo(org.javai.springai.dsl.plan.PlanStatus.READY);
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
		assertThat(result.status()).isEqualTo(org.javai.springai.dsl.plan.PlanStatus.READY);
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
		assertThat(result.status()).isEqualTo(org.javai.springai.dsl.plan.PlanStatus.ERROR);
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
		assertThat(result.status()).isEqualTo(org.javai.springai.dsl.plan.PlanStatus.READY);

		ResolvedStep.ActionStep step = (ResolvedStep.ActionStep) result.steps().getFirst();
		assertThat(step.arguments()).hasSize(1);
		assertThat(step.arguments().getFirst().value()).isInstanceOf(SampleActions.OrderValueQuery.class);

		SampleActions.OrderValueQuery query = (SampleActions.OrderValueQuery) step.arguments().getFirst().value();
		assertThat(query.customer_name()).isEqualTo("Mike");
		assertThat(query.period().start()).isEqualTo(LocalDate.parse("2024-01-01"));
		assertThat(query.period().end()).isEqualTo(LocalDate.parse("2024-01-31"));
	}

	@Test
	void convertsSxlNodeToRecordWithNestedPeriod() {
		// S-expression: (customer_name "Mike" period (start "2024-01-01" end "2024-01-31"))
		SxlNode periodNode = SxlNode.symbol("period", List.of(
				SxlNode.symbol("start"), SxlNode.literal("2024-01-01"),
				SxlNode.symbol("end"), SxlNode.literal("2024-01-31")
		));
		SxlNode payload = SxlNode.symbol("orderValueQuery", List.of(
				SxlNode.symbol("customer_name"), SxlNode.literal("Mike"),
				SxlNode.symbol("period"), periodNode
		));

		Plan plan = new Plan(
				"",
				List.of(new PlanStep.ActionStep("", "useOrderValue", new Object[] { payload }))
		);

		ResolvedPlan result = resolver.resolve(plan, registry);
		assertThat(result.status()).isEqualTo(org.javai.springai.dsl.plan.PlanStatus.READY);

		ResolvedStep.ActionStep step = (ResolvedStep.ActionStep) result.steps().getFirst();
		assertThat(step.arguments()).hasSize(1);
		assertThat(step.arguments().getFirst().value()).isInstanceOf(SampleActions.OrderValueQuery.class);

		SampleActions.OrderValueQuery query = (SampleActions.OrderValueQuery) step.arguments().getFirst().value();
		assertThat(query.customer_name()).isEqualTo("Mike");
		assertThat(query.period()).isNotNull();
		assertThat(query.period().start()).isEqualTo(LocalDate.parse("2024-01-01"));
		assertThat(query.period().end()).isEqualTo(LocalDate.parse("2024-01-31"));
	}

	@Test
	void convertsSqlStyleSxlNodeToRecord() {
		// This test captures the actual LLM response pattern where the LLM returned:
		// (EMBED sxl-sql (Q (F fct_orders o) (S (AND (= o.customer_name "Mike") ...))))
		// for a parameter that expects OrderValueQuery (not a DSL type).
		//
		// The conversion should:
		// 1. Detect this is an S-expression for a non-SExpressionType parameter
		// 2. Convert it to JSON structure
		// 3. Attempt to map to OrderValueQuery
		//
		// Since the SQL AST structure doesn't match OrderValueQuery fields,
		// this documents the current behavior when there's a structural mismatch.

		// Build: (EMBED sxl-sql (Q (F fct_orders o) (S (AND (= o.customer_name "Mike")
		//                                                   (>= o.order_date "2024-01-01")
		//                                                   (<= o.order_date "2024-01-31")))))
		SxlNode eqCustomer = SxlNode.symbol("=", List.of(
				SxlNode.symbol("o.customer_name"),
				SxlNode.literal("Mike")
		));
		SxlNode geStart = SxlNode.symbol(">=", List.of(
				SxlNode.symbol("o.order_date"),
				SxlNode.literal("2024-01-01")
		));
		SxlNode leEnd = SxlNode.symbol("<=", List.of(
				SxlNode.symbol("o.order_date"),
				SxlNode.literal("2024-01-31")
		));
		SxlNode andNode = SxlNode.symbol("AND", List.of(eqCustomer, geStart, leEnd));
		SxlNode sNode = SxlNode.symbol("S", List.of(andNode));
		SxlNode fNode = SxlNode.symbol("F", List.of(
				SxlNode.symbol("fct_orders"),
				SxlNode.symbol("o")
		));
		SxlNode qNode = SxlNode.symbol("Q", List.of(fNode, sNode));
		SxlNode embedNode = SxlNode.symbol("EMBED", List.of(
				SxlNode.symbol("sxl-sql"),
				qNode
		));

		Plan plan = new Plan(
				"",
				List.of(new PlanStep.ActionStep("", "useOrderValue", new Object[] { embedNode }))
		);

		// The conversion will happen, but the structure won't match OrderValueQuery
		// (customer_name, period) - so this will result in an error
		ResolvedPlan result = resolver.resolve(plan, registry);
		
		// Document current behavior: structural mismatch results in ERROR
		// This is expected - the SQL AST structure cannot map to OrderValueQuery
		assertThat(result.status()).isEqualTo(org.javai.springai.dsl.plan.PlanStatus.ERROR);
		assertThat(result.steps().getFirst()).isInstanceOf(ResolvedStep.ErrorStep.class);
		
		// The error message should indicate the conversion/mapping failure
		ResolvedStep.ErrorStep errorStep = (ResolvedStep.ErrorStep) result.steps().getFirst();
		assertThat(errorStep.reason()).contains("Failed to convert");
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

		enum Priority {
			HIGH, MEDIUM, LOW
		}

		record OrderValueQuery(String customer_name, Period period) {
		}

		record Period(LocalDate start, LocalDate end) {
		}
	}
}

