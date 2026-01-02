package org.javai.springai.scenarios.shopping.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.javai.springai.actions.test.PlanAssertions.assertExecutionSuccess;
import static org.javai.springai.actions.test.PlanAssertions.assertPlanReady;

import java.util.Map;
import org.javai.springai.actions.Plan;
import org.javai.springai.actions.PlanExecutionResult;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.conversation.ConversationTurnResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Basket Management Tests.
 * <p>
 * Tests viewing and modifying basket contents:
 * <ul>
 *   <li>Viewing basket contents</li>
 *   <li>Removing items</li>
 *   <li>Changing quantities</li>
 *   <li>Persistence across turns</li>
 * </ul>
 */
@DisplayName("Viewing and Managing Basket")
public class ShoppingBasketManagementTest extends AbstractShoppingScenarioTest {

	@Test
	@DisplayName("should show basket contents")
	void showBasketContents() {
		String sessionId = "basket-view";
		ActionContext context = new ActionContext();

		// Start and add items
		executor.execute(conversationManager.converse("start shopping", sessionId).plan(), context);
		executor.execute(conversationManager.converse("add 3 Coke Zero", sessionId).plan(), context);

		// View basket
		ConversationTurnResult viewTurn = conversationManager
				.converse("show me my basket", sessionId);
		Plan plan = viewTurn.plan();

		assertThat(plan).isNotNull();
		assertPlanReady(plan);

		PlanExecutionResult result = executor.execute(plan, context);
		assertExecutionSuccess(result);
		assertThat(actions.viewBasketInvoked()).isTrue();
	}

	@Test
	@DisplayName("should remove item from basket")
	void removeItemFromBasket() {
		String sessionId = "basket-remove";
		ActionContext context = new ActionContext();

		// Start and add items
		executor.execute(conversationManager.converse("start shopping", sessionId).plan(), context);
		executor.execute(conversationManager.converse("add 2 Coke Zero", sessionId).plan(), context);
		
		// Verify item is in basket before removal
		@SuppressWarnings("unchecked")
		Map<String, Integer> basket = context.get("basket", Map.class);
		assertThat(basket).isNotEmpty();

		// Remove item
		ConversationTurnResult removeTurn = conversationManager
				.converse("remove the Coke Zero", sessionId);
		Plan plan = removeTurn.plan();

		assertThat(plan).isNotNull();
		assertPlanReady(plan);

		PlanExecutionResult result = executor.execute(plan, context);
		assertExecutionSuccess(result);
		assertThat(actions.removeItemInvoked()).isTrue();
		
		// Verify item was removed from basket
		assertThat(basket).isEmpty();
	}

	@Test
	@DisplayName("should change quantity of existing item")
	void changeItemQuantity() {
		String sessionId = "basket-change-qty";
		ActionContext context = new ActionContext();

		// Start and add items
		executor.execute(conversationManager.converse("start shopping", sessionId).plan(), context);
		executor.execute(conversationManager.converse("add 5 Coke Zero", sessionId).plan(), context);

		// Change quantity
		ConversationTurnResult changeTurn = conversationManager
				.converse("change the Coke Zero to just 2 bottles", sessionId);
		Plan plan = changeTurn.plan();

		assertThat(plan).isNotNull();
		assertPlanReady(plan);

		PlanExecutionResult result = executor.execute(plan, context);
		assertExecutionSuccess(result);
		assertThat(actions.updateQuantityInvoked()).isTrue();
	}

	@Test
	@DisplayName("should persist basket across multiple turns")
	void persistBasketAcrossTurns() {
		String sessionId = "basket-persistence";
		ActionContext context = new ActionContext();

		// Turn 1: Start
		executor.execute(conversationManager.converse("start shopping", sessionId).plan(), context);

		// Turn 2: Add first item
		executor.execute(conversationManager.converse("add 2 Coke Zero", sessionId).plan(), context);

		// Turn 3: Add second item
		executor.execute(conversationManager.converse("add 3 Sea Salt Crisps", sessionId).plan(), context);

		// Turn 4: View basket - should have both items
		ConversationTurnResult viewTurn = conversationManager
				.converse("what's in my basket?", sessionId);
		executor.execute(viewTurn.plan(), context);

		assertThat(actions.viewBasketInvoked()).isTrue();
		
		// Verify basket contains both items
		@SuppressWarnings("unchecked")
		Map<String, Integer> basket = context.get("basket", Map.class);
		assertThat(basket).hasSize(2);
	}
}

