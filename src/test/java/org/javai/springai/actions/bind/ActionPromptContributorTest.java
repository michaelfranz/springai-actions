package org.javai.springai.actions.bind;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionParam;
import org.javai.springai.actions.internal.bind.ActionPromptContributor;
import org.javai.springai.actions.internal.bind.ActionRegistry;
import org.javai.springai.actions.sql.Query;
import org.junit.jupiter.api.Test;

/**
 * Tests for ActionPromptContributor.
 */
class ActionPromptContributorTest {

	@Test
	void emitsJsonPrompt() {
		ActionRegistry registry = new ActionRegistry();
		registry.registerActions(new SampleActions());
		registry.registerActions(new OtherActions());

		String prompt = ActionPromptContributor.emit(registry, spec -> spec.id().endsWith("runQuery"), null);

		assertThat(prompt).contains("runQuery");
		assertThat(prompt).doesNotContain("otherAction");
	}

	@Test
	void emitsJsonPromptWithSchema() throws Exception {
		ActionRegistry registry = new ActionRegistry();
		registry.registerActions(new SampleActions());
		registry.registerActions(new OtherActions());

		String prompt = ActionPromptContributor.emit(registry, spec -> spec.id().endsWith("runQuery"), null);

		ObjectMapper mapper = new ObjectMapper();
		JsonNode node = mapper.readTree(prompt);
		assertThat(node.isArray()).isTrue();
		assertThat(node).hasSize(1);
		JsonNode first = node.get(0);
		assertThat(first.get("id").asText()).isEqualTo("runQuery");
		assertThat(first.get("parameters")).isNotNull();
		assertThat(first.get("schema")).isNotNull();
	}

	@Test
	void emitsJsonPromptWithCollectionExample() {
		ActionRegistry registry = new ActionRegistry();
		registry.registerActions(new ListActions());

		String prompt = ActionPromptContributor.emit(registry, spec -> spec.id().equals("processBundleIds"), null);

		assertThat(prompt).contains("processBundleIds");
		assertThat(prompt).contains("bundleIds");
	}

	@Test
	void emitsJsonPromptWithConstrainedParameters() {
		ActionRegistry registry = new ActionRegistry();
		registry.registerActions(new ConstrainedActions());

		String prompt = ActionPromptContributor.emit(registry, 
				spec -> spec.id().equals("constrainedAction"), null);

		assertThat(prompt).contains("constrainedAction");
		// JSON schema includes parameter names
		assertThat(prompt).contains("priority").contains("code").contains("note");
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

	private static class ListActions {
		@Action(description = "Process bundle ids")
		public void processBundleIds(List<String> bundleIds) {
		}
	}

	private static class ConstrainedActions {
		@Action(description = "Constrained action")
		public void constrainedAction(
				@ActionParam(description = "priority", allowedValues = { "HIGH", "MEDIUM", "LOW" }) Priority priority,
				@ActionParam(description = "code", allowedRegex = "^A[0-9]{3}$") String code,
				@ActionParam(description = "note") String note
		) {
		}
	}

	private enum Priority {
		HIGH, MEDIUM, LOW;

		@Override
		public String toString() {
			return name().toUpperCase(Locale.ROOT);
		}
	}
}
