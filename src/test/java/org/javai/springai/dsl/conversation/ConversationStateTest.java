package org.javai.springai.dsl.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConversationStateTest {

	@Test
	void copiesCollectionsAndAllowsUpdates() {
		ConversationState state = new ConversationState(
				"export control chart",
				List.of(new PendingParamSnapshot("exportControlChartToExcel", "bundleId", "Provide bundle id")),
				Map.of(),
				""
		);

		ConversationState updated = state.withProvidedParam("bundleId", "A12345")
				.withLatestUserMessage("bundle is A12345");

		assertThat(state.providedParams()).isEmpty();
		assertThat(updated.providedParams()).containsEntry("bundleId", "A12345");
		assertThat(updated.latestUserMessage()).isEqualTo("bundle is A12345");
		assertThat(updated.pendingParams()).hasSize(1);
		assertThat(updated.pendingParams().getFirst().paramName()).isEqualTo("bundleId");
	}

	@Test
	void withPendingParamsReplacesList() {
		ConversationState state = new ConversationState(
				"original",
				List.of(),
				Map.of(),
				null
		);

		ConversationState updated = state.withPendingParams(
				List.of(new PendingParamSnapshot("action", "param", "msg")));

		assertThat(state.pendingParams()).isEmpty();
		assertThat(updated.pendingParams()).hasSize(1);
		assertThat(updated.pendingParams().getFirst().paramName()).isEqualTo("param");
	}
}

