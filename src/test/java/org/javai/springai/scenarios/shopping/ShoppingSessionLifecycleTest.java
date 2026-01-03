package org.javai.springai.scenarios.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.javai.springai.actions.test.PlanAssertions.assertExecutionSuccess;
import static org.javai.springai.actions.test.PlanAssertions.assertPlanReady;

import java.util.List;
import java.util.Map;
import org.javai.springai.actions.Plan;
import org.javai.springai.actions.PlanExecutionResult;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.conversation.ConversationTurnResult;
import org.javai.springai.scenarios.shopping.actions.Notification;
import org.javai.springai.scenarios.shopping.actions.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Session Lifecycle Tests.
 * <p>
 * Tests starting, managing, and ending shopping sessions:
 * <ul>
 *   <li>Starting a new session</li>
 *   <li>Surfacing offers on session start</li>
 *   <li>Checkout and ending session</li>
 * </ul>
 */
@DisplayName("Session Lifecycle")
public class ShoppingSessionLifecycleTest extends AbstractShoppingScenarioTest {

	@Test
	@DisplayName("should start a new shopping session")
	void startNewSession() {
		String sessionId = "lifecycle-start-session";
		ActionContext context = new ActionContext();

		ConversationTurnResult turn = conversationManager
				.converse("I want to start a new shopping basket", sessionId);
		Plan plan = turn.plan();

		assertThat(plan).isNotNull();
		assertPlanReady(plan);

		PlanExecutionResult result = executor.execute(plan, context);
		assertExecutionSuccess(result);
		assertThat(actions.startSessionInvoked()).isTrue();
		
		// Verify basket was placed in context
		assertThat(context.contains("basket")).isTrue();
	}

	@Test
	@DisplayName("should surface special offers when session starts")
	void surfaceOffersOnSessionStart() {
		String sessionId = "lifecycle-offers-session";
		ActionContext context = new ActionContext();

		ConversationTurnResult turn = conversationManager
				.converse("I'd like to start shopping", sessionId);
		PlanExecutionResult result = executor.execute(turn.plan(), context);

		// Verify offers are programmatically included in action result
		// This is a business-critical requirement - offers are NOT left to LLM discretion
		assertThat(result.wasExecuted()).isTrue();
		assertThat(result.success()).isTrue();
		
		// Extract notifications from the step results
		List<Notification> notifications = extractNotifications(result);
		assertThat(notifications)
				.filteredOn(n -> n.type() == NotificationType.OFFER)
				.isNotEmpty();
	}

	@Test
	@DisplayName("should complete checkout and end session")
	void completeCheckoutAndEndSession() {
		String sessionId = "lifecycle-checkout-session";
		ActionContext context = new ActionContext();

		// Start session - this puts the basket into context
		executor.execute(conversationManager.converse("start shopping", sessionId).plan(), context);
		assertThat(context.contains("basket")).isTrue();

		// Add items - uses basket from context
		executor.execute(conversationManager.converse("add 2 Coke Zero", sessionId).plan(), context);

		// Checkout
		ConversationTurnResult checkoutTurn = conversationManager
				.converse("I'm ready to pay", sessionId);
		Plan checkoutPlan = checkoutTurn.plan();

		assertThat(checkoutPlan).isNotNull();
		assertPlanReady(checkoutPlan);

		PlanExecutionResult result = executor.execute(checkoutPlan, context);
		assertExecutionSuccess(result);
		assertThat(actions.checkoutInvoked()).isTrue();
		
		// Verify basket is empty after checkout
		@SuppressWarnings("unchecked")
		Map<String, Integer> basket = context.get("basket", Map.class);
		assertThat(basket).isEmpty();
	}
}

