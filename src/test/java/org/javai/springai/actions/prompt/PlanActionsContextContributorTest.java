package org.javai.springai.actions.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Optional;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionParam;
import org.javai.springai.actions.internal.bind.ActionRegistry;
import org.javai.springai.actions.internal.prompt.PlanActionsContextContributor;
import org.javai.springai.actions.internal.prompt.SystemPromptContext;
import org.junit.jupiter.api.Test;

/**
 * Tests for PlanActionsContextContributor.
 * Verifies that action parameters include all relevant metadata in the system prompt.
 */
class PlanActionsContextContributorTest {

	@Test
	void includesAllowedValuesInParameterDescription() {
		ActionRegistry registry = new ActionRegistry();
		registry.registerActions(new ActionsWithAllowedValues());

		PlanActionsContextContributor contributor = new PlanActionsContextContributor();
		SystemPromptContext context = new SystemPromptContext(registry, null, null, null);
		Optional<String> result = contributor.contribute(context);

		assertThat(result).isPresent();
		String prompt = result.get();

		// Verify the parameter includes allowed values and REQUIRED marker
		assertThat(prompt).contains("measurementType [REQUIRED]");
		assertThat(prompt).contains("force");
		assertThat(prompt).contains("displacement");
	}

	@Test
	void includesAllowedRegexInParameterDescription() {
		ActionRegistry registry = new ActionRegistry();
		registry.registerActions(new ActionsWithAllowedRegex());

		PlanActionsContextContributor contributor = new PlanActionsContextContributor();
		SystemPromptContext context = new SystemPromptContext(registry, null, null, null);
		Optional<String> result = contributor.contribute(context);

		assertThat(result).isPresent();
		String prompt = result.get();

		// Verify the parameter includes regex pattern
		assertThat(prompt).contains("bundleId");
		assertThat(prompt).contains("[A-Z0-9]+");
	}

	@Test
	void includesParameterDescriptionInPrompt() {
		ActionRegistry registry = new ActionRegistry();
		registry.registerActions(new ActionsWithDescription());

		PlanActionsContextContributor contributor = new PlanActionsContextContributor();
		SystemPromptContext context = new SystemPromptContext(registry, null, null, null);
		Optional<String> result = contributor.contribute(context);

		assertThat(result).isPresent();
		String prompt = result.get();

		// Verify the parameter includes its description
		assertThat(prompt).contains("The measurement type to be charted");
	}

	@Test
	void includesAllConstraintsForComplexParameters() {
		ActionRegistry registry = new ActionRegistry();
		registry.registerActions(new ActionsWithAllConstraints());

		PlanActionsContextContributor contributor = new PlanActionsContextContributor();
		SystemPromptContext context = new SystemPromptContext(registry, null, null, null);
		Optional<String> result = contributor.contribute(context);

		assertThat(result).isPresent();
		String prompt = result.get();

		// Verify measurement parameter with allowed values
		assertThat(prompt).contains("measurementType");
		assertThat(prompt).contains("force");
		assertThat(prompt).contains("displacement");

		// Verify bundleId parameter with regex
		assertThat(prompt).contains("bundleId");
		assertThat(prompt).contains("[A-Z0-9]+");
	}

	@Test
	void returnsEmptyForNullContext() {
		PlanActionsContextContributor contributor = new PlanActionsContextContributor();
		Optional<String> result = contributor.contribute(null);

		assertThat(result).isEmpty();
	}

	@Test
	void returnsEmptyForNullRegistry() {
		PlanActionsContextContributor contributor = new PlanActionsContextContributor();
		SystemPromptContext context = new SystemPromptContext(null, null, null, null);
		Optional<String> result = contributor.contribute(context);

		assertThat(result).isEmpty();
	}

	// Test actions with different constraint configurations

	private static class ActionsWithAllowedValues {
		@Action(description = "Export control chart")
		public void exportControlChart(
				@ActionParam(allowedValues = {"force", "displacement"}) String measurementType) {
		}
	}

	private static class ActionsWithAllowedRegex {
		@Action(description = "Process bundle")
		public void processBundle(
				@ActionParam(allowedRegex = "[A-Z0-9]+") String bundleId) {
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
				@ActionParam(description = "Bundle ID like A12345", allowedRegex = "[A-Z0-9]+") String bundleId) {
		}
	}
}

