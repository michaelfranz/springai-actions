package org.javai.springai.scenarios.shopping.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.javai.springai.actions.test.PlanAssertions.assertExecutionSuccess;
import static org.javai.springai.actions.test.PlanAssertions.assertPlanReady;

import java.util.Map;
import org.javai.springai.actions.Plan;
import org.javai.springai.actions.PlanExecutionResult;
import org.javai.springai.actions.PlanStatus;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.conversation.ConversationTurnResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Adding Items to Basket Tests.
 * <p>
 * Tests the core functionality of adding products to the shopping basket:
 * <ul>
 *   <li>Adding with explicit quantity</li>
 *   <li>Handling missing quantity (LLM may assume or ask)</li>
 *   <li>Multi-turn quantity clarification</li>
 * </ul>
 */
@DisplayName("Adding Items to Basket")
public class ShoppingAddItemsTest extends AbstractShoppingScenarioTest {

	@Test
	@DisplayName("should add item with explicit product and quantity")
	void addItemWithExplicitQuantity() {
		String sessionId = "add-explicit";
		ActionContext context = new ActionContext();

		// Start session - initializes basket in context
		executor.execute(conversationManager.converse("start shopping", sessionId).plan(), context);

		// Add with explicit quantity
		ConversationTurnResult addTurn = conversationManager
				.converse("Add 6 bottles of Coke Zero to my basket", sessionId);
		Plan addPlan = addTurn.plan();

		assertThat(addPlan).isNotNull();
		assertPlanReady(addPlan);

		PlanExecutionResult result = executor.execute(addPlan, context);
		assertExecutionSuccess(result);
		assertThat(actions.addItemInvoked()).isTrue();
		
		// Verify item was added to basket
		@SuppressWarnings("unchecked")
		Map<String, Integer> basket = context.get("basket", Map.class);
		assertThat(basket).isNotEmpty();
	}

	@Test
	@DisplayName("should request quantity when not provided or assume default")
	void requestQuantityWhenMissing() {
		String sessionId = "add-pending";
		ActionContext context = new ActionContext();

		// Start session
		executor.execute(conversationManager.converse("start shopping", sessionId).plan(), context);

		// Add without quantity - LLM may either:
		// 1. Assume quantity=1 and return READY plan
		// 2. Return PENDING plan requesting quantity
		ConversationTurnResult addTurn = conversationManager
				.converse("add coke zero", sessionId);
		Plan plan = addTurn.plan();

		assertThat(plan).isNotNull();
		assertThat(plan.status()).isIn(PlanStatus.READY, PlanStatus.PENDING);

		if (plan.status() == PlanStatus.READY) {
			// LLM assumed quantity=1 - this is acceptable
			PlanExecutionResult result = executor.execute(plan, context);
			assertExecutionSuccess(result);
			assertThat(actions.addItemInvoked()).isTrue();
		} else {
			// LLM requested quantity - provide it and verify plan completes
			assertThat(addTurn.pendingParams()).isNotEmpty();
			ConversationTurnResult followUp = conversationManager
					.converse("just 1", sessionId);
			Plan followUpPlan = followUp.plan();
			assertPlanReady(followUpPlan);
			PlanExecutionResult result = executor.execute(followUpPlan, context);
			assertExecutionSuccess(result);
			assertThat(actions.addItemInvoked()).isTrue();
		}
	}

	@Test
	@DisplayName("should complete add after providing missing quantity")
	void completeAddAfterProvidingQuantity() {
		String sessionId = "add-followup";
		ActionContext context = new ActionContext();

		// Start session
		executor.execute(conversationManager.converse("start shopping", sessionId).plan(), context);

		// Add without quantity -> may be pending or ready
		conversationManager.converse("add coke zero", sessionId);

		// Provide quantity - should complete the add or update
		ConversationTurnResult followUp = conversationManager
				.converse("make it 4 bottles", sessionId);
		Plan plan = followUp.plan();

		assertThat(plan).isNotNull();
		assertPlanReady(plan);

		PlanExecutionResult result = executor.execute(plan, context);
		assertExecutionSuccess(result);
		// Either addItem or updateQuantity is acceptable here
		assertThat(actions.addItemInvoked() || actions.updateQuantityInvoked()).isTrue();
	}
}

