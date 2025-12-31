package org.javai.springai.actions.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.Map;
import org.javai.springai.actions.PlanStep;
import org.junit.jupiter.api.Test;

class ConversationStateTest {

	@Test
	void copiesCollectionsAndAllowsUpdates() {
		ConversationState state = new ConversationState(
				"export control chart",
				List.of(new PlanStep.PendingParam("bundleId", "Provide bundle id")),
				Map.of(),
				"",
				null,
				List.of()
		);

		ConversationState updated = state.withProvidedParam("bundleId", "A12345")
				.withLatestUserMessage("bundle is A12345");

		assertThat(state.providedParams()).isEmpty();
		assertThat(updated.providedParams()).containsEntry("bundleId", "A12345");
		assertThat(updated.latestUserMessage()).isEqualTo("bundle is A12345");
		assertThat(updated.pendingParams()).hasSize(1);
		assertThat(updated.pendingParams().getFirst().name()).isEqualTo("bundleId");
	}

	@Test
	void withPendingParamsReplacesList() {
		ConversationState state = new ConversationState(
				"original",
				List.of(),
				Map.of(),
				null,
				null,
				List.of()
		);

		ConversationState updated = state.withPendingParams(
				List.of(new PlanStep.PendingParam("param", "msg")));

		assertThat(state.pendingParams()).isEmpty();
		assertThat(updated.pendingParams()).hasSize(1);
		assertThat(updated.pendingParams().getFirst().name()).isEqualTo("param");
	}

	@Test
	void initialCreatesEmptyState() {
		ConversationState state = ConversationState.initial("show me orders");

		assertThat(state.originalInstruction()).isEqualTo("show me orders");
		assertThat(state.pendingParams()).isEmpty();
		assertThat(state.providedParams()).isEmpty();
		assertThat(state.latestUserMessage()).isNull();
		assertThat(state.workingContext()).isNull();
		assertThat(state.turnHistory()).isEmpty();
	}

	@Test
	void withWorkingContextPushesToHistory() {
		ConversationState state = ConversationState.initial("original");
		WorkingContext<String> ctx1 = WorkingContext.of("test.type", "payload1");
		WorkingContext<String> ctx2 = WorkingContext.of("test.type", "payload2");

		// Add first context
		ConversationState s1 = state.withWorkingContext(ctx1, 10);
		assertThat(s1.workingContext()).isEqualTo(ctx1);
		assertThat(s1.turnHistory()).isEmpty();

		// Add second context - first should move to history
		ConversationState s2 = s1.withWorkingContext(ctx2, 10);
		assertThat(s2.workingContext()).isEqualTo(ctx2);
		assertThat(s2.turnHistory()).hasSize(1);
		assertThat(s2.turnHistory().get(0)).isEqualTo(ctx1);
	}

	@Test
	void withWorkingContextCapsHistorySize() {
		ConversationState state = ConversationState.initial("original");
		
		// Add 5 contexts with history size of 3
		ConversationState current = state;
		for (int i = 1; i <= 5; i++) {
			current = current.withWorkingContext(
					WorkingContext.of("test", "payload" + i), 3);
		}

		// Should have current context + 3 in history (oldest removed)
		assertThat(current.workingContext().payload()).isEqualTo("payload5");
		assertThat(current.turnHistory()).hasSize(3);
		// History should contain payloads 2, 3, 4 (1 was dropped)
		assertThat(current.turnHistory().get(0).payload()).isEqualTo("payload2");
		assertThat(current.turnHistory().get(1).payload()).isEqualTo("payload3");
		assertThat(current.turnHistory().get(2).payload()).isEqualTo("payload4");
	}

	@Test
	void emptyReturnsBlankState() {
		ConversationState empty = ConversationState.empty();

		assertThat(empty.originalInstruction()).isNull();
		assertThat(empty.pendingParams()).isEmpty();
		assertThat(empty.providedParams()).isEmpty();
		assertThat(empty.workingContext()).isNull();
		assertThat(empty.turnHistory()).isEmpty();
	}

	@Test
	void hasWorkingContextReturnsTrueWhenPresent() {
		ConversationState withoutCtx = ConversationState.initial("test");
		ConversationState withCtx = withoutCtx.withWorkingContext(
				WorkingContext.of("test", "payload"), 10);

		assertThat(withoutCtx.hasWorkingContext()).isFalse();
		assertThat(withCtx.hasWorkingContext()).isTrue();
	}

	@Test
	void lastHistoryEntryReturnsNewest() {
		ConversationState state = ConversationState.initial("test");
		WorkingContext<String> ctx1 = WorkingContext.of("test", "first");
		WorkingContext<String> ctx2 = WorkingContext.of("test", "second");
		WorkingContext<String> ctx3 = WorkingContext.of("test", "third");

		ConversationState s = state
				.withWorkingContext(ctx1, 10)
				.withWorkingContext(ctx2, 10)
				.withWorkingContext(ctx3, 10);

		assertThat(s.lastHistoryEntry().payload()).isEqualTo("second");
	}
}
