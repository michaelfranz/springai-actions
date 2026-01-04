package org.javai.springai.actions.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import org.javai.springai.actions.Planner;
import org.javai.springai.actions.internal.plan.PromptPreview;
import org.javai.springai.scenarios.data_warehouse.DataWarehouseActions;
import org.junit.jupiter.api.Test;

/**
 * Captures a prompt preview to verify action specs are emitted as JSON Schema
 * with per-action ActionStep definitions. This is a read-only prompt build;
 * it does not call the LLM.
 */
class PromptPreviewCaptureTest {

	@Test
	void capturePromptPreviewForSqlActions() {
		Planner planner = Planner.builder()
				.actions(new DataWarehouseActions())
				.build();

		PromptPreview preview = planner.preview("show me the total order value for Mike in January 2024");

		assertThat(preview).isNotNull();
		assertThat(preview.systemMessages()).isNotEmpty();

		// Surface the system prompt in test output so we can inspect the schema.
		System.out.println("=== SYSTEM PROMPT START ===");
		preview.systemMessages().forEach(System.out::println);
		System.out.println("=== SYSTEM PROMPT END ===");

		String fullPrompt = String.join("\n", preview.systemMessages());
		
		// Verify JSON Schema structure is present
		assertThat(fullPrompt).contains("$schema");
		assertThat(fullPrompt).contains("json-schema.org");
		
		// Verify actions from DataWarehouseActions are in schema
		assertThat(fullPrompt).contains("showSqlQuery");
		assertThat(fullPrompt).contains("runSqlQuery");
		
		// Verify per-action definitions with const binding
		assertThat(fullPrompt).contains("ShowSqlQueryAction");
		assertThat(fullPrompt).contains("\"const\"");
	}
}

