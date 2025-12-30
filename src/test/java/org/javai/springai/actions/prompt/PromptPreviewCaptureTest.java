package org.javai.springai.actions.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import org.javai.springai.actions.Planner;
import org.javai.springai.actions.internal.plan.PromptPreview;
import org.javai.springai.scenarios.data_warehouse.DataWarehouseActions;
import org.junit.jupiter.api.Test;

/**
 * Captures a prompt preview to verify action specs (including JSON schema)
 * are emitted in SXL mode for non-DSL record parameters like OrderValueQuery.
 * This is a read-only prompt build; it does not call the LLM.
 */
class PromptPreviewCaptureTest {

	@Test
	void capturePromptPreviewForAggregateOrderValue() {
		Planner planner = Planner.builder()
				.actions(new DataWarehouseActions())
				.build();

		PromptPreview preview = planner.preview("calculate the total order value for Mike in January 2024");

		assertThat(preview).isNotNull();
		assertThat(preview.systemMessages()).isNotEmpty();

		// Surface the system prompt in test output so we can inspect the schema for OrderValueQuery.
		System.out.println("=== SYSTEM PROMPT START ===");
		preview.systemMessages().forEach(System.out::println);
		System.out.println("=== SYSTEM PROMPT END ===");

		// Basic sanity: the actions section should mention aggregateOrderValue
		assertThat(String.join("\n", preview.systemMessages()))
				.contains("aggregateOrderValue");
	}
}

