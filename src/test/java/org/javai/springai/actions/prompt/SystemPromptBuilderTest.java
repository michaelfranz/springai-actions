package org.javai.springai.actions.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionParam;
import org.javai.springai.actions.bind.ActionDescriptorFilter;
import org.javai.springai.actions.bind.ActionRegistry;
import org.javai.springai.actions.prompt.SystemPromptBuilder;
import org.javai.springai.actions.sql.Query;
import org.junit.jupiter.api.Test;

/**
 * Tests for SystemPromptBuilder.
 * Plans use JSON format exclusively; SQL queries use standard ANSI SQL.
 */
class SystemPromptBuilderTest {

	@Test
	void buildsPromptWithSelectedActions() {
		ActionRegistry registry = new ActionRegistry();
		registry.registerActions(new SampleActions());
		registry.registerActions(new OtherActions());

		String prompt = SystemPromptBuilder.build(
				registry,
				spec -> spec.id().endsWith("runQuery")
		);

		assertThat(prompt).contains("runQuery");
		assertThat(prompt).doesNotContain("otherAction");
	}

	@Test
	void buildsJsonPromptWithActions() {
		ActionRegistry registry = new ActionRegistry();
		registry.registerActions(new SampleActions());

		String prompt = SystemPromptBuilder.build(
				registry,
				ActionDescriptorFilter.ALL
		);

		var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		try {
			var root = mapper.readTree(prompt);
			assertThat(root.get("actions").isArray()).isTrue();
		} catch (Exception e) {
			throw new AssertionError("Failed to parse JSON prompt", e);
		}
	}

	@Test
	void generatesExamplePlanFromActionDescriptorsWithExamples() {
		ActionRegistry registry = new ActionRegistry();
		registry.registerActions(new ActionsWithExamples());

		var descriptors = registry.getActionDescriptors();
		String examplePlan = SystemPromptBuilder.generateExamplePlan(descriptors);

		// Verify that an example plan is generated in JSON format
		assertThat(examplePlan).contains("EXAMPLE PLAN (JSON format):");
		assertThat(examplePlan).contains("\"message\"");
		assertThat(examplePlan).contains("\"steps\"");
		assertThat(examplePlan).contains("\"actionId\"");
		assertThat(examplePlan).contains("\"parameters\"");
		// Verify that extracted example values are present
		assertThat(examplePlan).contains("bushing");
		assertThat(examplePlan).contains("displacement");
		assertThat(examplePlan).contains("A12345");
	}

	@Test
	void examplePlanUsesFirstExampleFromEachParameter() {
		ActionRegistry registry = new ActionRegistry();
		registry.registerActions(new ActionsWithMultipleExamples());

		var descriptors = registry.getActionDescriptors();
		String examplePlan = SystemPromptBuilder.generateExamplePlan(descriptors);

		// First example should be used (bushing, not piston; displacement, not force)
		assertThat(examplePlan).contains("bushing");
		assertThat(examplePlan).contains("displacement");
		// JSON format should be used - verify it's not S-expression style
		assertThat(examplePlan).contains("\"component\":");
		assertThat(examplePlan).contains("\"measurement\":");
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
