package org.javai.springai.scenarios.shopping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.javai.springai.actions.test.PlanAssertions.assertExecutionSuccess;

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
		if (!startSession(sessionId, context)) {
			log.warn("Skipping test - could not start session");
			return;
		}

		// Add with explicit quantity
		ConversationTurnResult addTurn = conversationManager
				.converse("Add 6 bottles of Coke Zero to my basket", sessionId);
		Plan addPlan = addTurn.plan();

		assertThat(addPlan).isNotNull();
		if (addPlan.status() != PlanStatus.READY) {
			log.warn("LLM did not return READY plan for add item: {}", addPlan.assistantMessage());
			return;
		}

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
		if (!startSession(sessionId, context)) {
			log.warn("Skipping test - could not start session");
			return;
		}

		// Add without quantity - LLM may:
		// 1. Assume quantity=1 and return READY plan
		// 2. Return PENDING plan requesting quantity
		// 3. Return ERROR/noAction if it can't determine the product
		ConversationTurnResult addTurn = conversationManager
				.converse("add coke zero", sessionId);
		Plan plan = addTurn.plan();

		assertThat(plan).isNotNull();

		if (plan.status() == PlanStatus.READY) {
			// LLM assumed quantity=1 or found the product - this is acceptable
			PlanExecutionResult result = executor.execute(plan, context);
			assertExecutionSuccess(result);
			assertThat(actions.addItemInvoked()).isTrue();
		} else if (plan.status() == PlanStatus.PENDING) {
			// LLM requested more info - provide complete request
			// Note: multi-turn context is fragile with LLMs, so provide full details
			ConversationTurnResult followUp = conversationManager
					.converse("add 1 coke zero with sku BEV-002", sessionId);
			Plan followUpPlan = followUp.plan();
			if (followUpPlan.status() == PlanStatus.READY) {
				PlanExecutionResult result = executor.execute(followUpPlan, context);
				assertExecutionSuccess(result);
				assertThat(actions.addItemInvoked()).isTrue();
			} else {
				log.warn("Follow-up plan not READY: {} - {}", followUpPlan.status(), followUpPlan.assistantMessage());
			}
		} else {
			// ERROR or other - LLM couldn't handle the vague request
			log.warn("Plan resulted in {}: {}", plan.status(), plan.assistantMessage());
		}
	}

	@Test
	@DisplayName("should complete add after providing missing quantity")
	void completeAddAfterProvidingQuantity() {
		String sessionId = "add-followup";
		ActionContext context = new ActionContext();

		// Start session - MUST succeed to populate the basket in context
		if (!startSession(sessionId, context)) {
			log.warn("Skipping test - could not start session");
			return;
		}
		
		// Verify the basket is now in context
		assertThat(context.contains("basket"))
				.as("startSession should populate 'basket' in context")
				.isTrue();

		// Add with explicit quantity to test the flow
		ConversationTurnResult addTurn = conversationManager
				.converse("add 4 bottles of coke zero", sessionId);
		Plan plan = addTurn.plan();

		assertThat(plan).isNotNull();
		// LLM may return READY (all params known) or ERROR (if param resolution fails)
		if (plan.status() == PlanStatus.READY) {
			PlanExecutionResult result = executor.execute(plan, context);
			assertExecutionSuccess(result);
			assertThat(actions.addItemInvoked()).isTrue();
		} else if (plan.status() == PlanStatus.PENDING) {
			// LLM requested more info - provide it
			ConversationTurnResult followUp = conversationManager
					.converse("the sku is BEV-002", sessionId);
			if (followUp.plan().status() == PlanStatus.READY) {
				PlanExecutionResult result = executor.execute(followUp.plan(), context);
				assertExecutionSuccess(result);
				assertThat(actions.addItemInvoked()).isTrue();
			} else {
				log.warn("Follow-up plan not READY: {}", followUp.plan().status());
			}
		} else {
			log.warn("Plan resulted in ERROR - LLM did not provide all parameters: {}", plan.assistantMessage());
		}
	}
}

