package org.javai.springai.dsl.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionParam;
import org.javai.springai.dsl.act.ActionRegistry;
import org.javai.springai.dsl.act.ActionSpecFilter;
import org.javai.springai.dsl.bind.TypeFactoryBootstrap;
import org.javai.springai.dsl.sql.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SystemPromptBuilderTest {

	private DslGuidanceProvider guidanceProvider;

	@BeforeEach
	void setup() {
		TypeFactoryBootstrap.registerBuiltIns();
		guidanceProvider = new MapBackedDslGuidanceProvider(
				Map.of(
						"sxl-sql", "SQL grammar guidance here",
						"sxl-plan", "Plan grammar guidance here"
				)
		);
	}

	@Test
	void buildsSxlPromptWithSelectedActionsAndDslGuidance() {
		ActionRegistry registry = new ActionRegistry();
		registry.registerActions(new SampleActions());
		registry.registerActions(new OtherActions());

		String prompt = SystemPromptBuilder.build(
				registry,
				spec -> spec.id().endsWith("runQuery"),
				guidanceProvider,
				SystemPromptBuilder.Mode.SXL,
				"openai",
				"gpt-4.1"
		);

		assertThat(prompt).contains("ACTIONS:").contains("runQuery").contains("EMBED sxl-sql");
		assertThat(prompt).doesNotContain("otherAction");
		assertThat(prompt).contains("DSL GUIDANCE:").contains("SQL grammar guidance here");
	}

	@Test
	void buildsJsonPromptWithSelectedActionsAndDslGuidance() {
		ActionRegistry registry = new ActionRegistry();
		registry.registerActions(new SampleActions());

		String prompt = SystemPromptBuilder.build(
				registry,
				ActionSpecFilter.ALL,
				guidanceProvider,
				SystemPromptBuilder.Mode.JSON,
				"openai",
				"gpt-4.1"
		);

		assertThat(prompt).contains("schema").contains("parameters");
		assertThat(prompt).contains("DSL GUIDANCE:").contains("SQL grammar guidance here");
	}

	private static class SampleActions {
		@Action(description = "Run a query")
		public void runQuery(Query query, @ActionParam(description = "note to include") String note) {
		}
	}

	private static class OtherActions {
		@Action(description = "Other action")
		public void otherAction(String input) {
		}
	}
}
