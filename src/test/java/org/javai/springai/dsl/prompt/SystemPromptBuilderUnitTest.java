package org.javai.springai.dsl.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.javai.springai.actions.api.Action;
import org.javai.springai.dsl.act.ActionRegistry;
import org.javai.springai.dsl.bind.TypeFactoryBootstrap;
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
				List.of("META-INF/sxl-meta-grammar-universal.yml", "META-INF/sxl-meta-grammar-sql.yml"),
				getClass().getClassLoader());
	}

	@Test
	void noDslGuidanceWhenNoSExpressionParams() {
		// Actions with simple params (no S-expression types) should not include DSL guidance
		registry.registerActions(new SimpleActions());

		String prompt = SystemPromptBuilder.build(registry, ad -> true, guidanceProvider, SystemPromptBuilder.Mode.SXL);

		assertThat(prompt).doesNotContain("DSL GUIDANCE:");
		assertThat(prompt).doesNotContain("DSL sxl-sql:");
	}

	@Test
	void includesSqlDslGuidanceWhenQueryParamPresent() {
		registry.registerActions(new SqlActions());

		String prompt = SystemPromptBuilder.build(registry, ad -> true, guidanceProvider, SystemPromptBuilder.Mode.SXL);

		assertThat(prompt).contains("DSL sxl-sql:");
		assertThat(prompt).contains("DSL sxl-universal:");
	}

	@Test
	void includesSqlDslGuidanceWhenQueryParamPresent_viaGrammarBackedProvider() {
		// mirror prior dedicated test to ensure grammar-backed provider works with sxl-sql
		registry.registerActions(new SqlActions());

		String prompt = SystemPromptBuilder.build(registry,
				actionDescriptor -> true,
				guidanceProvider,
				SystemPromptBuilder.Mode.SXL);

		assertThat(prompt).contains("DSL sxl-sql:");
		assertThat(prompt).contains("DSL sxl-universal:");
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

		assertThat(prompt).contains("DSL sxl-sql:");
		assertThat(prompt).contains("DSL sxl-universal:");
	}

	@Test
	void ordersGuidanceUniversalBeforeSql() {
		registry.registerActions(new SqlActions());

		String prompt = SystemPromptBuilder.build(
				registry,
				ad -> true,
				guidanceProvider,
				SystemPromptBuilder.Mode.SXL
		);

		int idxUniversal = prompt.indexOf("DSL sxl-universal:");
		int idxSql = prompt.indexOf("DSL sxl-sql:");

		assertThat(idxUniversal).isNotNegative();
		assertThat(idxSql).isGreaterThan(idxUniversal);
	}

	private static class SimpleActions {
		@Action(description = "Do something simple")
		public void simpleAction(String message) {
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

