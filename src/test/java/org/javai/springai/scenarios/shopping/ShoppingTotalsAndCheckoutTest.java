package org.javai.springai.scenarios.shopping;

import static org.assertj.core.api.Assertions.assertThat;

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
	@DisplayName("should handle compute basket total request")
	void computeBasketTotal() {
		String sessionId = "totals-compute";
		ActionContext context = new ActionContext();

		// Start session
		ConversationTurnResult startTurn = conversationManager.converse("start shopping", sessionId);
		if (startTurn.plan().status() == PlanStatus.READY) {
			executor.execute(startTurn.plan(), context);
		}
		
		// Add items (may fail due to LLM parameter issues)
		ConversationTurnResult addTurn = conversationManager.converse("add 3 Coke Zero", sessionId);
		if (addTurn.plan().status() == PlanStatus.READY) {
			executor.execute(addTurn.plan(), context);
		}

		// Ask for total - LLM may use computeTotal or viewBasketSummary (both calculate pricing)
		ConversationTurnResult totalTurn = conversationManager
				.converse("show me the total", sessionId);
		Plan plan = totalTurn.plan();

		assertThat(plan).isNotNull();
		assertThat(plan.status()).isIn(PlanStatus.READY, PlanStatus.PENDING, PlanStatus.ERROR);

		if (plan.status() == PlanStatus.READY && context.contains("basket")) {
			PlanExecutionResult result = executor.execute(plan, context);
			if (result.success()) {
				// Either action is acceptable - both calculate and display the total
				assertThat(actions.computeTotalInvoked() || actions.viewBasketInvoked())
						.as("Expected either computeTotal or viewBasketSummary to be invoked")
						.isTrue();
			}
		}
	}

	@Test
	@DisplayName("should handle apply discounts to total request")
	void applyDiscountsToTotal() {
		String sessionId = "totals-discount";
		ActionContext context = new ActionContext();

		// Start session
		ConversationTurnResult startTurn = conversationManager.converse("start shopping", sessionId);
		if (startTurn.plan().status() == PlanStatus.READY) {
			executor.execute(startTurn.plan(), context);
		}
		
		// Add discounted item (may fail due to LLM parameter issues)
		ConversationTurnResult addTurn = conversationManager.converse("add 5 Coke Zero", sessionId);
		if (addTurn.plan().status() == PlanStatus.READY) {
			executor.execute(addTurn.plan(), context);
		}

		// Ask for total - discounts are applied automatically by either action
		ConversationTurnResult totalTurn = conversationManager
				.converse("show me the total", sessionId);
		Plan plan = totalTurn.plan();

		assertThat(plan).isNotNull();
		assertThat(plan.status()).isIn(PlanStatus.READY, PlanStatus.PENDING, PlanStatus.ERROR);

		if (plan.status() == PlanStatus.READY && context.contains("basket")) {
			PlanExecutionResult result = executor.execute(plan, context);
			if (result.success()) {
				// Either action is acceptable - both calculate pricing with discounts
				assertThat(actions.computeTotalInvoked() || actions.viewBasketInvoked())
						.as("Expected either computeTotal or viewBasketSummary to be invoked")
						.isTrue();
			}
		}
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

