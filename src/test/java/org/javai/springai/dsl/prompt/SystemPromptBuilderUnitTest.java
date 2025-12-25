package org.javai.springai.dsl.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.javai.springai.actions.api.Action;
import org.javai.springai.dsl.act.ActionRegistry;
import org.javai.springai.dsl.bind.TypeFactoryBootstrap;
import org.javai.springai.dsl.plan.Plan;
import org.javai.springai.dsl.sql.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SystemPromptBuilderUnitTest {

	private ActionRegistry registry;
	private DslGuidanceProvider guidanceProvider;

	@BeforeEach
	void setUp() {
		TypeFactoryBootstrap.registerBuiltIns();
		registry = new ActionRegistry();
		guidanceProvider = new GrammarBackedDslGuidanceProvider(
				List.of("META-INF/sxl-meta-grammar-universal.yml", "META-INF/sxl-meta-grammar-plan.yml", "META-INF/sxl-meta-grammar-sql.yml"),
				getClass().getClassLoader());
	}

	@Test
	void includesPlanDslGuidanceByDefault() {
		registry.registerActions(new PlanOnlyActions());

		String prompt = SystemPromptBuilder.build(registry, ad -> true, guidanceProvider, SystemPromptBuilder.Mode.SXL);

		assertThat(prompt).contains("DSL sxl-plan:");
		assertThat(prompt).doesNotContain("DSL sxl-sql:");
	}

	@Test
	void includesSqlDslGuidanceWhenQueryParamPresent() {
		registry.registerActions(new SqlActions());

		String prompt = SystemPromptBuilder.build(registry, ad -> true, guidanceProvider, SystemPromptBuilder.Mode.SXL);

		assertThat(prompt).contains("DSL sxl-plan:");
		assertThat(prompt).contains("DSL sxl-sql:");
	}

	@Test
	void includesSqlDslGuidanceWhenQueryParamPresent_viaGrammarBackedProvider() {
		// mirror prior dedicated test to ensure grammar-backed provider works with sxl-sql
		registry.registerActions(new SqlActions());

		String prompt = SystemPromptBuilder.build(registry,
				actionDescriptor -> true,
				guidanceProvider,
				SystemPromptBuilder.Mode.SXL);

		assertThat(prompt).contains("DSL sxl-plan:");
		assertThat(prompt).contains("DSL sxl-sql:");
	}

	@Test
	void includesSqlDslGuidanceUsingCollectedActionsRegistry() {
		registry.registerActions(new SqlActions());

		String prompt = SystemPromptBuilder.build(
				registry,
				ad -> true,
				guidanceProvider,
				SystemPromptBuilder.Mode.SXL
		);

		assertThat(prompt).contains("DSL sxl-plan:");
		assertThat(prompt).contains("DSL sxl-sql:");
	}

	@Test
	void ordersGuidanceUniversalThenPlanThenOthersAlphabetically() {
		registry.registerActions(new SqlActions());

		String prompt = SystemPromptBuilder.build(
				registry,
				ad -> true,
				guidanceProvider,
				SystemPromptBuilder.Mode.SXL
		);

		int idxUniversal = prompt.indexOf("DSL sxl-universal:");
		int idxPlan = prompt.indexOf("DSL sxl-plan:");
		int idxSql = prompt.indexOf("DSL sxl-sql:");

		assertThat(idxUniversal).isNotNegative();
		assertThat(idxPlan).isGreaterThan(idxUniversal);
		assertThat(idxSql).isGreaterThan(idxPlan);
	}

	private static class PlanOnlyActions {
		@Action(description = "Do something with plan")
		public void planAction(Plan plan) {
			// no-op
		}
	}

	private static class SqlActions {
		@Action(description = "Display SQL query")
		public void displaySqlQuery(Query query) {
			// no-op
		}
	}
}

