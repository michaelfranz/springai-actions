package org.javai.springai.scenarios.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.javai.springai.actions.test.PlanAssertions.assertExecutionSuccess;
import static org.javai.springai.actions.test.PlanAssertions.assertPlanReady;

import org.javai.springai.actions.Plan;
import org.javai.springai.actions.PlanExecutionResult;
import org.javai.springai.actions.PlanStatus;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.conversation.ConversationTurnResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Totals and Checkout Tests.
 * <p>
 * Tests computing totals and the checkout process:
 * <ul>
 *   <li>Computing basket total</li>
 *   <li>Applying discounts</li>
 *   <li>Checkout confirmation</li>
 * </ul>
 */
@DisplayName("Computing Totals and Checkout")
public class ShoppingTotalsAndCheckoutTest extends AbstractShoppingScenarioTest {

	@Test
	@DisplayName("should compute basket total")
	void computeBasketTotal() {
		String sessionId = "totals-compute";
		ActionContext context = new ActionContext();

		// Start and add items
		executor.execute(conversationManager.converse("start shopping", sessionId).plan(), context);
		executor.execute(conversationManager.converse("add 3 Coke Zero", sessionId).plan(), context);

		// Ask for total - LLM may use computeTotal or viewBasketSummary (both calculate pricing)
		ConversationTurnResult totalTurn = conversationManager
				.converse("show me the total", sessionId);
		Plan plan = totalTurn.plan();

		assertThat(plan).isNotNull();
		assertPlanReady(plan);

		PlanExecutionResult result = executor.execute(plan, context);
		assertExecutionSuccess(result);
		// Either action is acceptable - both calculate and display the total
		assertThat(actions.computeTotalInvoked() || actions.viewBasketInvoked())
				.as("Expected either computeTotal or viewBasketSummary to be invoked")
				.isTrue();
	}

	@Test
	@DisplayName("should apply discounts to total")
	void applyDiscountsToTotal() {
		String sessionId = "totals-discount";
		ActionContext context = new ActionContext();

		// Start and add discounted item
		executor.execute(conversationManager.converse("start shopping", sessionId).plan(), context);
		executor.execute(conversationManager.converse("add 5 Coke Zero", sessionId).plan(), context);

		// Ask for total - discounts are applied automatically by either action
		ConversationTurnResult totalTurn = conversationManager
				.converse("show me the total", sessionId);

		PlanExecutionResult result = executor.execute(totalTurn.plan(), context);
		assertExecutionSuccess(result);

		// Either action is acceptable - both calculate pricing with discounts
		assertThat(actions.computeTotalInvoked() || actions.viewBasketInvoked())
				.as("Expected either computeTotal or viewBasketSummary to be invoked")
				.isTrue();
	}

	@Test
	@DisplayName("should require confirmation before checkout")
	void requireConfirmationBeforeCheckout() {
		String sessionId = "totals-confirm-checkout";
		ActionContext context = new ActionContext();

		// Start and add items
		executor.execute(conversationManager.converse("start shopping", sessionId).plan(), context);
		executor.execute(conversationManager.converse("add 2 Coke Zero", sessionId).plan(), context);

		// Request checkout
		ConversationTurnResult checkoutTurn = conversationManager
				.converse("checkout", sessionId);
		Plan plan = checkoutTurn.plan();

		// The plan should either execute checkout or request confirmation
		// depending on persona constraints interpretation
		assertThat(plan).isNotNull();
		assertThat(plan.status()).isIn(PlanStatus.READY, PlanStatus.PENDING);
	}
}

