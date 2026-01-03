package org.javai.springai.scenarios.shopping;

import static org.assertj.core.api.Assertions.assertThat;

import org.javai.springai.actions.Plan;
import org.javai.springai.actions.PlanExecutionResult;
import org.javai.springai.actions.PlanStatus;
import org.javai.springai.actions.PlanStep;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.conversation.ConversationTurnResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Error Handling Tests.
 * <p>
 * Tests graceful handling of error scenarios:
 * <ul>
 *   <li>Non-shopping requests</li>
 *   <li>Unrecognised products</li>
 * </ul>
 */
@DisplayName("Error Handling")
public class ShoppingErrorHandlingTest extends AbstractShoppingScenarioTest {

	@Test
	@DisplayName("should reject non-shopping requests gracefully")
	void rejectNonShoppingRequests() {
		String sessionId = "error-nonshop";
		ActionContext context = new ActionContext();

		ConversationTurnResult turn = conversationManager
				.converse("change the oil in my car", sessionId);
		Plan plan = turn.plan();

		assertThat(plan).isNotNull();
		
		// Non-shopping requests result in no actions - triggers noAction handler
		// The plan may have empty steps or a NoActionStep
		if (plan.planSteps().isEmpty()) {
			// Empty steps - noAction handler will be invoked on execute
			PlanExecutionResult result = executor.execute(plan, context);
			assertThat(result.success()).isFalse();
			// The assistant message should explain what the assistant can help with
			assertThat(plan.assistantMessage()).isNotBlank();
		} else if (plan.planSteps().getFirst() instanceof PlanStep.NoActionStep noAction) {
			// Explicit noAction step with explanation
			assertThat(noAction.message()).isNotBlank();
			PlanExecutionResult result = executor.execute(plan, context);
			assertThat(result.success()).isFalse();
		} else {
			// Fallback: might still be an error step
			assertThat(plan.status()).isEqualTo(PlanStatus.ERROR);
		}
	}

	@Test
	@DisplayName("should handle unrecognised product gracefully")
	void handleUnrecognisedProduct() {
		String sessionId = "error-unknown";
		ActionContext context = new ActionContext();

		executor.execute(conversationManager.converse("start shopping", sessionId).plan(), context);

		ConversationTurnResult addTurn = conversationManager
				.converse("add 5 bottles of NonExistentProduct", sessionId);
		Plan plan = addTurn.plan();

		assertThat(plan).isNotNull();
		
		// The LLM may handle unrecognised products in several valid ways:
		// 1. Return a READY plan with addItem (validation happens at execution)
		// 2. Return a READY plan with noAction (LLM detected unknown product)
		// 3. Return a plan with NoActionStep (resolved to no-action step type)
		// 4. Return an ERROR or PENDING plan
		// All behaviors are acceptable - the key is graceful handling
		
		// Check for NoActionStep first (this is how noAction action gets resolved)
		boolean hasNoActionStep = plan.planSteps().stream()
				.anyMatch(s -> s instanceof PlanStep.NoActionStep);
		
		// Check for ActionStep with noAction id
		boolean hasNoActionAction = plan.planSteps().stream()
				.filter(s -> s instanceof PlanStep.ActionStep)
				.map(s -> ((PlanStep.ActionStep) s).actionId())
				.anyMatch("noAction"::equals);
		
		if (hasNoActionStep || hasNoActionAction) {
			// LLM correctly identified unknown product
			assertThat(plan.assistantMessage()).isNotBlank();
		} else if (plan.status() == PlanStatus.READY) {
			// Execute the plan - addItem should handle the error gracefully
			executor.execute(plan, context);
			assertThat(actions.addItemInvoked()).isTrue();
		} else {
			// LLM detected the issue
			assertThat(plan.status()).isIn(PlanStatus.ERROR, PlanStatus.PENDING);
		}
	}
}

