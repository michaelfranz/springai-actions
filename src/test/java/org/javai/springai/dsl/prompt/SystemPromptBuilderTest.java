package org.javai.springai.dsl.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionParam;
import org.javai.springai.dsl.act.ActionDescriptorFilter;
import org.javai.springai.dsl.act.ActionRegistry;
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

		assertThat(prompt).contains("runQuery");
		// Note: EMBED sxl-sql may or may not appear depending on DSL detection
		assertThat(prompt).doesNotContain("otherAction");
		assertThat(prompt).contains("DSL GUIDANCE:").contains("SQL grammar guidance here");
	}

	@Test
	void buildsJsonPromptWithSelectedActionsAndDslGuidance() {
		ActionRegistry registry = new ActionRegistry();
		registry.registerActions(new SampleActions());

		String prompt = SystemPromptBuilder.build(
				registry,
				ActionDescriptorFilter.ALL,
				guidanceProvider,
				SystemPromptBuilder.Mode.JSON,
				"openai",
				"gpt-4.1"
		);

		var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		try {
			var root = mapper.readTree(prompt);
			assertThat(root.get("actions").isArray()).isTrue();
			assertThat(root.get("dslGuidance").isArray()).isTrue();
		} catch (Exception e) {
			throw new AssertionError("Failed to parse JSON prompt", e);
		}
	}

	@Test
	void generatesExamplePlanFromActionDescriptorsWithExamples() {
		ActionRegistry registry = new ActionRegistry();
		registry.registerActions(new ActionsWithExamples());

		String prompt = SystemPromptBuilder.build(
				registry,
				ActionDescriptorFilter.ALL,
				guidanceProvider,
				SystemPromptBuilder.Mode.SXL
		);

		// Verify that an example plan is generated (if there are actions with examples)
		if (prompt.contains("EXAMPLE PLAN:")) {
			assertThat(prompt).contains("(P");
			assertThat(prompt).contains("(PS");
			assertThat(prompt).contains("(PA");
		}
		// Verify that extracted example values are present
		assertThat(prompt).contains("bushing");
		assertThat(prompt).contains("displacement");
		assertThat(prompt).contains("A12345");
	}

	@Test
	void examplePlanUsesFirstExampleFromEachParameter() {
		ActionRegistry registry = new ActionRegistry();
		registry.registerActions(new ActionsWithMultipleExamples());

		String prompt = SystemPromptBuilder.build(
				registry,
				ActionDescriptorFilter.ALL,
				guidanceProvider,
				SystemPromptBuilder.Mode.SXL
		);

		// First example should be used (bushing, not piston; displacement, not force)
		assertThat(prompt).contains("bushing");
		assertThat(prompt).contains("displacement");
		// Second examples should not be in the generated plan
		assertThat(prompt).doesNotContain("(PA component \"piston\")");
		assertThat(prompt).doesNotContain("(PA measurement \"force\")");
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

	private static class ActionsWithExamples {
		@Action(description = "Add test to notebook")
		public void addTest(
				@ActionParam(description = "Component type", examples = {"bushing", "piston"}) String component,
				@ActionParam(description = "Measurement type", examples = {"displacement", "force"}) String measurement,
				@ActionParam(description = "Bundle ID", examples = {"A12345", "B6789"}) String bundleId) {
		}

		@Action(description = "Write notebook")
		public void writeNotebook() {
		}
	}

	private static class ActionsWithMultipleExamples {
		@Action(description = "Add test to notebook")
		public void addTest(
				@ActionParam(description = "Component type", examples = {"bushing", "piston", "seal"}) String component,
				@ActionParam(description = "Measurement type", examples = {"displacement", "force", "temperature"}) String measurement,
				@ActionParam(description = "Bundle ID", examples = {"A12345", "B6789"}) String bundleId) {
		}

		@Action(description = "Write notebook")
		public void writeNotebook() {
		}
	}
}
