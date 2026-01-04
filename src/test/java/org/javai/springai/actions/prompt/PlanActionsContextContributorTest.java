package org.javai.springai.actions.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Optional;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionParam;
import org.javai.springai.actions.internal.bind.ActionRegistry;
import org.javai.springai.actions.internal.prompt.PlanActionsContextContributor;
import org.javai.springai.actions.internal.prompt.SystemPromptContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for PlanActionsContextContributor.
 * 
 * <p>Verifies that the prompt contribution includes ActionParam constraints:
 * <ul>
 *   <li>Enum constraints from allowedValues</li>
 *   <li>Descriptions</li>
 *   <li>Guidance for normalizing user input to allowed values</li>
 * </ul>
 */
class PlanActionsContextContributorTest {

	@Nested
	@DisplayName("ActionParam Constraints in Prompt")
	class ActionParamConstraints {

		@Test
		@DisplayName("should include allowedValues with MUST be constraint")
		void includesAllowedValuesAsEnumOptions() {
			ActionRegistry registry = new ActionRegistry();
			registry.registerActions(new ActionsWithAllowedValues());

			PlanActionsContextContributor contributor = new PlanActionsContextContributor();
			SystemPromptContext context = new SystemPromptContext(registry, null, null, null);
			Optional<String> result = contributor.contribute(context);

			assertThat(result).isPresent();
			String prompt = result.get();

			// Verify parameter name and allowed values
			assertThat(prompt).contains("measurementType");
			assertThat(prompt).contains("MUST be");
			assertThat(prompt).contains("\"force\"");
			assertThat(prompt).contains("\"displacement\"");
		}

		@Test
		@DisplayName("should include parameter description as comment")
		void includesParameterDescriptionAsComment() {
			ActionRegistry registry = new ActionRegistry();
			registry.registerActions(new ActionsWithDescription());

			PlanActionsContextContributor contributor = new PlanActionsContextContributor();
			SystemPromptContext context = new SystemPromptContext(registry, null, null, null);
			Optional<String> result = contributor.contribute(context);

			assertThat(result).isPresent();
			String prompt = result.get();

			// Verify description is included
			assertThat(prompt).contains("The measurement type to be charted");
		}

		@Test
		@DisplayName("should include all constraints together")
		void includesAllConstraints() {
			ActionRegistry registry = new ActionRegistry();
			registry.registerActions(new ActionsWithAllConstraints());

			PlanActionsContextContributor contributor = new PlanActionsContextContributor();
			SystemPromptContext context = new SystemPromptContext(registry, null, null, null);
			Optional<String> result = contributor.contribute(context);

			assertThat(result).isPresent();
			String prompt = result.get();

			// Verify actionId
			assertThat(prompt).contains("exportControlChartToExcel");
			
			// Verify measurement parameter with MUST be constraint
			assertThat(prompt).contains("measurementType");
			assertThat(prompt).contains("MUST be");
			assertThat(prompt).contains("\"force\"");
			assertThat(prompt).contains("\"displacement\"");

			// Verify bundleId parameter
			assertThat(prompt).contains("bundleId");
		}
	}

	@Nested
	@DisplayName("Input Normalization Guidance")
	class InputNormalizationGuidance {

		@Test
		@DisplayName("should include value mapping guidance for user input variations")
		void includesValueMappingGuidance() {
			ActionRegistry registry = new ActionRegistry();
			registry.registerActions(new ActionsWithAllowedValues());

			PlanActionsContextContributor contributor = new PlanActionsContextContributor();
			SystemPromptContext context = new SystemPromptContext(registry, null, null, null);
			Optional<String> result = contributor.contribute(context);

			assertThat(result).isPresent();
			String prompt = result.get();

			// Verify value mapping guidance is present at the top
			assertThat(prompt).containsIgnoringCase("IMPORTANT");
			assertThat(prompt).contains("\"displacements\"");
			assertThat(prompt).contains("\"displacement\"");
		}

		@Test
		@DisplayName("should show allowed values with MUST be constraint")
		void showsMustBeConstraint() {
			ActionRegistry registry = new ActionRegistry();
			registry.registerActions(new ActionsWithAllowedValues());

			PlanActionsContextContributor contributor = new PlanActionsContextContributor();
			SystemPromptContext context = new SystemPromptContext(registry, null, null, null);
			Optional<String> result = contributor.contribute(context);

			assertThat(result).isPresent();
			String prompt = result.get();

			// Verify MUST be constraint
			assertThat(prompt).contains("MUST be");
			assertThat(prompt).contains("\"force\"");
			assertThat(prompt).contains("\"displacement\"");
		}
	}

	@Nested
	@DisplayName("Edge Cases")
	class EdgeCases {

		@Test
		@DisplayName("should return empty for null context")
		void returnsEmptyForNullContext() {
			PlanActionsContextContributor contributor = new PlanActionsContextContributor();
			Optional<String> result = contributor.contribute(null);

			assertThat(result).isEmpty();
		}

		@Test
		@DisplayName("should return empty for null registry")
		void returnsEmptyForNullRegistry() {
			PlanActionsContextContributor contributor = new PlanActionsContextContributor();
			SystemPromptContext context = new SystemPromptContext(null, null, null, null);
			Optional<String> result = contributor.contribute(context);

			assertThat(result).isEmpty();
		}
	}

	// Test actions with different constraint configurations

	private static class ActionsWithAllowedValues {
		@Action(description = "Export control chart")
		public void exportControlChart(
				@ActionParam(allowedValues = {"force", "displacement"}) String measurementType) {
		}
	}

	private static class ActionsWithDescription {
		@Action(description = "Display chart")
		public void displayChart(
				@ActionParam(description = "The measurement type to be charted") String measurementType) {
		}
	}

	private static class ActionsWithAllConstraints {
		@Action(description = "Export control chart to Excel")
		public void exportControlChartToExcel(
				@ActionParam(description = "The measurement type to be charted", allowedValues = {"force", "displacement"}) String measurementType,
				@ActionParam(description = "Bundle ID like A12345") String bundleId) {
		}
	}
}

