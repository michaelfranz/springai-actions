package org.javai.springai.dsl.act;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionParam;
import org.javai.springai.dsl.bind.TypeFactoryBootstrap;
import org.javai.springai.dsl.sql.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActionPromptEmitterTest {

	@BeforeEach
	void setup() {
		TypeFactoryBootstrap.registerBuiltIns();
	}

	@Test
	void emitsSxlPrompt() {
		ActionRegistry registry = new ActionRegistry();
		registry.registerActions(new SampleActions());
		registry.registerActions(new OtherActions());

		String prompt = ActionPromptEmitter.emit(registry, ActionPromptEmitter.Mode.SXL, spec -> spec.id().endsWith("runQuery"));

		assertThat(prompt).contains("(PS").contains("runQuery").contains("EMBED sxl-sql");
		assertThat(prompt).doesNotContain("otherAction");
	}

	@Test
	void emitsJsonPromptWithSchema() throws Exception {
		ActionRegistry registry = new ActionRegistry();
		registry.registerActions(new SampleActions());
		registry.registerActions(new OtherActions());

		String prompt = ActionPromptEmitter.emit(registry, ActionPromptEmitter.Mode.JSON, spec -> spec.id().endsWith("runQuery"));

		ObjectMapper mapper = new ObjectMapper();
		JsonNode node = mapper.readTree(prompt);
		assertThat(node.isArray()).isTrue();
		assertThat(node).hasSize(1);
		JsonNode first = node.get(0);
		assertThat(first.get("id").asText()).endsWith(".runQuery");
		assertThat(first.get("parameters")).isNotNull();
		assertThat(first.get("schema")).isNotNull();
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
