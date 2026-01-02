package org.javai.springai.actions.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.javai.springai.actions.Plan;
import org.javai.springai.actions.PlanStatus;
import org.javai.springai.actions.PlanStep;
import org.javai.springai.actions.Planner;
import org.javai.springai.actions.internal.bind.ActionBinding;
import org.javai.springai.actions.internal.plan.PlanFormulationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SuppressWarnings({ "DataFlowIssue", "NullAway" })
class ConversationManagerUnitTest {

	@Mock
	private Planner mockPlanner;

	@Mock
	private ConversationStateStore mockStore;

	@Mock
	private ConversationStateSerializer mockSerializer;

	@Mock
	private ActionBinding mockBinding;

	ConversationManagerUnitTest() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	void constructorRequiresDependencies() {
		assertThatThrownBy(() -> new ConversationManager(null, mockStore)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new ConversationManager(mockPlanner, null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	@SuppressWarnings("NullAway")
	void conversationFlowPendingThenResolved() {
		ConversationManager manager = new ConversationManager(mockPlanner, mockStore);

		// Turn 1: missing bundleId -> pending
		Plan pendingPlan = new Plan("desc",
				List.of(new PlanStep.PendingActionStep("",
						"exportControlChartToExcel",
						new PlanStep.PendingParam[] { new PlanStep.PendingParam("bundleId", "Provide bundle id") },
						Map.of("domainEntity", "displacement", "measurementConcept", "values"))));
		when(mockPlanner.formulatePlan(eq("export control chart to excel for displacement values"), argThat(Objects::nonNull)))
				.thenReturn(new PlanFormulationResult("", pendingPlan, null, false, null));
		when(mockStore.load("session-1")).thenReturn(Optional.of(ConversationState.initial("export control chart to excel for displacement values")));

		ConversationTurnResult first = manager.converse("export control chart to excel for displacement values", "session-1");
		assertThat(first.state().pendingParams()).hasSize(1);
		assertThat(first.plan().status()).isEqualTo(PlanStatus.PENDING);

		// Turn 2: user provides bundleId -> action step with binding
		when(mockBinding.id()).thenReturn("exportControlChartToExcel");
		Plan resolvedPlan = new Plan("desc",
				List.of(new PlanStep.ActionStep(mockBinding, List.of())));
		when(mockPlanner.formulatePlan(eq("bundle id is A12345"), argThat(Objects::nonNull)))
				.thenReturn(new PlanFormulationResult("", resolvedPlan, null, false, null));

		ConversationTurnResult second = manager.converse("bundle id is A12345", "session-1");
		assertThat(second.plan().status()).isEqualTo(PlanStatus.READY);
		assertThat(second.state().pendingParams()).isEmpty();
	}

	@Nested
	@DisplayName("User Message Augmentation")
	class UserMessageAugmentationTests {

		@Test
		@DisplayName("registerAugmenter returns manager for fluent chaining")
		void registerAugmenterReturnsSelfForChaining() {
			ConversationManager manager = new ConversationManager(mockPlanner, mockStore);

			UserMessageAugmenter augmenter1 = createTestAugmenter("type1");
			UserMessageAugmenter augmenter2 = createTestAugmenter("type2");

			// Should support fluent chaining
			ConversationManager result = manager
					.registerAugmenter(augmenter1)
					.registerAugmenter(augmenter2);

			assertThat(result).isSameAs(manager);
		}

		@Test
		@DisplayName("augments user message with blob-based manager")
		void augmentsUserMessageWithBlobBasedManager() {
			// Arrange - use blob-based mode which accepts config
			PayloadTypeRegistry registry = new PayloadTypeRegistry();
			registry.register("test.type", String.class);

			ConversationStateConfig config = ConversationStateConfig.builder()
					.augmentUserMessage(true)
					.contextPrefix("Current state:")
					.requestPrefix("User request:")
					.build();

			ConversationManager manager = new ConversationManager(
					mockPlanner, mockSerializer, registry, config);

			// Register a test augmenter
			manager.registerAugmenter(createTestAugmenter("test.type"));

			// Create state with working context
			WorkingContext<String> workingContext = WorkingContext.of("test.type", "test-payload");
			ConversationState stateWithContext = ConversationState.initial("initial")
					.withWorkingContext(workingContext, 10);

			// Setup serializer to return state with context
			when(mockSerializer.deserialize(any(byte[].class), any(PayloadTypeRegistry.class)))
					.thenReturn(stateWithContext);

			// Setup planner to capture the augmented message
			ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
			Plan emptyPlan = new Plan("desc", List.of());
			when(mockPlanner.formulatePlan(messageCaptor.capture(), any(ConversationState.class)))
					.thenReturn(new PlanFormulationResult("", emptyPlan, null, false, null));
			when(mockSerializer.serialize(any(ConversationState.class), any(PayloadTypeRegistry.class)))
					.thenReturn(new byte[]{1, 2, 3});

			// Act
			manager.converse("add a filter", new byte[]{1, 2, 3});

			// Assert - verify the message was augmented
			String capturedMessage = messageCaptor.getValue();
			assertThat(capturedMessage).contains("Current state:");
			assertThat(capturedMessage).contains("User request:");
			assertThat(capturedMessage).contains("add a filter");
		}

		@Test
		@DisplayName("does not augment when disabled in config")
		void doesNotAugmentWhenDisabled() {
			// Arrange
			PayloadTypeRegistry registry = new PayloadTypeRegistry();
			registry.register("test.type", String.class);

			ConversationStateConfig config = ConversationStateConfig.builder()
					.augmentUserMessage(false)
					.build();

			ConversationManager manager = new ConversationManager(
					mockPlanner, mockSerializer, registry, config);

			manager.registerAugmenter(createTestAugmenter("test.type"));

			WorkingContext<String> workingContext = WorkingContext.of("test.type", "payload");
			ConversationState stateWithContext = ConversationState.initial("initial")
					.withWorkingContext(workingContext, 10);

			when(mockSerializer.deserialize(any(byte[].class), any(PayloadTypeRegistry.class)))
					.thenReturn(stateWithContext);

			ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
			Plan emptyPlan = new Plan("desc", List.of());
			when(mockPlanner.formulatePlan(messageCaptor.capture(), any(ConversationState.class)))
					.thenReturn(new PlanFormulationResult("", emptyPlan, null, false, null));
			when(mockSerializer.serialize(any(ConversationState.class), any(PayloadTypeRegistry.class)))
					.thenReturn(new byte[]{1, 2, 3});

			// Act
			manager.converse("user message", new byte[]{1, 2, 3});

			// Assert - message should not be augmented
			assertThat(messageCaptor.getValue()).isEqualTo("user message");
		}

		@Test
		@DisplayName("does not augment without working context")
		void doesNotAugmentWithoutWorkingContext() {
			// Arrange
			PayloadTypeRegistry registry = new PayloadTypeRegistry();
			registry.register("test.type", String.class);

			ConversationStateConfig config = ConversationStateConfig.builder()
					.augmentUserMessage(true)
					.build();

			ConversationManager manager = new ConversationManager(
					mockPlanner, mockSerializer, registry, config);

			manager.registerAugmenter(createTestAugmenter("test.type"));

			// State WITHOUT working context
			ConversationState stateWithoutContext = ConversationState.initial("initial");

			when(mockSerializer.deserialize(any(byte[].class), any(PayloadTypeRegistry.class)))
					.thenReturn(stateWithoutContext);

			ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
			Plan emptyPlan = new Plan("desc", List.of());
			when(mockPlanner.formulatePlan(messageCaptor.capture(), any(ConversationState.class)))
					.thenReturn(new PlanFormulationResult("", emptyPlan, null, false, null));
			when(mockSerializer.serialize(any(ConversationState.class), any(PayloadTypeRegistry.class)))
					.thenReturn(new byte[]{1, 2, 3});

			// Act
			manager.converse("user message", new byte[]{1, 2, 3});

			// Assert - message should not be augmented
			assertThat(messageCaptor.getValue()).isEqualTo("user message");
		}

		@Test
		@DisplayName("does not augment without matching augmenter")
		void doesNotAugmentWithoutMatchingAugmenter() {
			// Arrange
			PayloadTypeRegistry registry = new PayloadTypeRegistry();
			registry.register("test.type", String.class);

			ConversationStateConfig config = ConversationStateConfig.builder()
					.augmentUserMessage(true)
					.build();

			ConversationManager manager = new ConversationManager(
					mockPlanner, mockSerializer, registry, config);

			// Note: No augmenter registered for "test.type"

			WorkingContext<String> workingContext = WorkingContext.of("test.type", "payload");
			ConversationState stateWithContext = ConversationState.initial("initial")
					.withWorkingContext(workingContext, 10);

			when(mockSerializer.deserialize(any(byte[].class), any(PayloadTypeRegistry.class)))
					.thenReturn(stateWithContext);

			ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
			Plan emptyPlan = new Plan("desc", List.of());
			when(mockPlanner.formulatePlan(messageCaptor.capture(), any(ConversationState.class)))
					.thenReturn(new PlanFormulationResult("", emptyPlan, null, false, null));
			when(mockSerializer.serialize(any(ConversationState.class), any(PayloadTypeRegistry.class)))
					.thenReturn(new byte[]{1, 2, 3});

			// Act
			manager.converse("user message", new byte[]{1, 2, 3});

			// Assert - message should not be augmented
			assertThat(messageCaptor.getValue()).isEqualTo("user message");
		}

		private UserMessageAugmenter createTestAugmenter(String type) {
			return new UserMessageAugmenter() {
				@Override
				public Optional<String> formatForUserMessage(WorkingContext<?> ctx) {
					return Optional.of("Current state: test-context");
				}

				@Override
				public Optional<String> formatForUserMessage(WorkingContext<?> ctx, ConversationStateConfig cfg) {
					return Optional.of(cfg.contextPrefix() + " test-context");
				}

				@Override
				public String contextType() {
					return type;
				}

				@Override
				public boolean shouldAugment(WorkingContext<?> ctx) {
					return true;
				}
			};
		}
	}
}
