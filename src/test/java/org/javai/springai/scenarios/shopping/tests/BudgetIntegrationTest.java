package org.javai.springai.scenarios.shopping.tests;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import org.javai.springai.scenarios.shopping.actions.ActionResult;
import org.javai.springai.scenarios.shopping.actions.InventoryAwareShoppingActions;
import org.javai.springai.scenarios.shopping.store.MockStoreApi;
import org.javai.springai.scenarios.shopping.store.model.BudgetStatus;
import org.javai.springai.scenarios.shopping.store.model.ShoppingSession;
import org.javai.springai.scenarios.shopping.tools.BudgetTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for budget-aware shopping (Phase 6).
 * 
 * Tests:
 * - Budget setting via actions
 * - Budget status queries via BudgetTool
 * - Budget tracking across shopping operations
 * - Budget warnings (not blocking)
 */
@DisplayName("Budget Integration Tests")
class BudgetIntegrationTest {

	private MockStoreApi storeApi;
	private InventoryAwareShoppingActions actions;
	private BudgetTool budgetTool;

	@BeforeEach
	void setUp() {
		storeApi = new MockStoreApi();
		actions = new InventoryAwareShoppingActions(storeApi);
		budgetTool = new BudgetTool(storeApi);
	}

	@Nested
	@DisplayName("Setting Budget via Actions")
	class SetBudgetTests {

		@Test
		@DisplayName("should set budget on session")
		void setBudgetOnSession() {
			// Start a session
			actions.startSession(null);

			// Set a budget
			ActionResult result = actions.setBudget(null, new BigDecimal("50.00"));

			assertThat(result.success()).isTrue();
			assertThat(result.message()).contains("£50.00");
			assertThat(actions.setBudgetInvoked()).isTrue();

			// Verify session has budget
			ShoppingSession session = actions.getCurrentSession();
			assertThat(session).isNotNull();
			assertThat(session.hasBudget()).isTrue();
			assertThat(session.budgetLimit()).isEqualByComparingTo(new BigDecimal("50.00"));
		}

		@Test
		@DisplayName("should reject invalid budget amounts")
		void rejectInvalidBudget() {
			actions.startSession(null);

			// Zero budget
			ActionResult result1 = actions.setBudget(null, BigDecimal.ZERO);
			assertThat(result1.success()).isFalse();
			assertThat(result1.message()).containsIgnoringCase("valid");

			// Negative budget
			ActionResult result2 = actions.setBudget(null, new BigDecimal("-10.00"));
			assertThat(result2.success()).isFalse();

			// Null budget
			ActionResult result3 = actions.setBudget(null, null);
			assertThat(result3.success()).isFalse();
		}

		@Test
		@DisplayName("should allow updating budget mid-session")
		void updateBudgetMidSession() {
			actions.startSession(null);

			// Set initial budget
			actions.setBudget(null, new BigDecimal("30.00"));
			assertThat(actions.getCurrentSession().budgetLimit()).isEqualByComparingTo(new BigDecimal("30.00"));

			// Update budget
			ActionResult result = actions.setBudget(null, new BigDecimal("75.00"));
			assertThat(result.success()).isTrue();
			assertThat(actions.getCurrentSession().budgetLimit()).isEqualByComparingTo(new BigDecimal("75.00"));
		}
	}

	@Nested
	@DisplayName("Budget Status Queries via Tool")
	class BudgetStatusTests {

		@Test
		@DisplayName("should report no budget when not set")
		void noBudgetStatus() {
			ShoppingSession session = ShoppingSession.create("test-1", null);

			String status = budgetTool.getBudgetStatus(session);

			assertThat(status).containsIgnoringCase("no budget limit");
			assertThat(budgetTool.getBudgetStatusInvoked()).isTrue();
		}

		@Test
		@DisplayName("should report within budget status")
		void withinBudgetStatus() {
			// Create session with budget
			ShoppingSession session = ShoppingSession.create("test-2", null)
					.withBudget(new BigDecimal("50.00"));

			String status = budgetTool.getBudgetStatus(session);

			assertThat(status).contains("Within budget");
			assertThat(status).contains("£50.00");
		}

		@Test
		@DisplayName("should report approaching limit status")
		void approachingLimitStatus() {
			// Create session with budget and items near limit
			ShoppingSession session = ShoppingSession.create("test-3", null)
					.withBudget(new BigDecimal("10.00"));

			// Add items worth about £9 to approach the limit
			// Sparkling Water is £1.00 per bottle (BEV-003)
			session = session.withItemAdded("BEV-003", 9); // 9 × £1.00 = £9.00 = 90% of £10

			BudgetStatus status = storeApi.getBudgetStatus(session);
			assertThat(status).isInstanceOf(BudgetStatus.ApproachingLimit.class);
		}

		@Test
		@DisplayName("should check addition impact on budget")
		void checkAdditionImpact() {
			ShoppingSession session = ShoppingSession.create("test-4", null)
					.withBudget(new BigDecimal("10.00"));

			// Check adding an expensive item
			String result = budgetTool.checkBudgetForAddition(session, "Coca Cola", 5);

			assertThat(result).isNotEmpty();
			assertThat(budgetTool.checkAdditionInvoked()).isTrue();
		}

		@Test
		@DisplayName("should report max affordable quantity")
		void maxAffordableQuantity() {
			ShoppingSession session = ShoppingSession.create("test-5", null)
					.withBudget(new BigDecimal("5.00"));

			String result = budgetTool.getMaxAffordableQuantity(session, "Coca Cola");

			assertThat(result).contains("afford");
			assertThat(budgetTool.getMaxAffordableInvoked()).isTrue();
		}
	}

	@Nested
	@DisplayName("Budget Tracking Across Operations")
	class BudgetTrackingTests {

		@Test
		@DisplayName("should track budget across item additions")
		void trackBudgetAcrossAdditions() {
			actions.startSession(null);
			actions.setBudget(null, new BigDecimal("20.00"));

			// Add items
			actions.addItem("Coca Cola", 2); // ~£3.00
			actions.addItem("Sparkling Water", 3); // ~£4.50

			ShoppingSession session = actions.getCurrentSession();
			BudgetStatus status = storeApi.getBudgetStatus(session);

			// Should still be within budget
			assertThat(status).isInstanceOfAny(
					BudgetStatus.WithinBudget.class,
					BudgetStatus.ApproachingLimit.class
			);
		}

		@Test
		@DisplayName("should report exceeded budget but not block")
		void exceededBudgetNotBlocking() {
			actions.startSession(null);
			actions.setBudget(null, new BigDecimal("5.00"));

			// Add expensive items that exceed budget
			ActionResult result = actions.addItem("Cheese Board Selection", 1); // £12.99

			// Should succeed (not blocked)
			assertThat(result.success()).isTrue();

			// Budget should show exceeded
			ShoppingSession session = actions.getCurrentSession();
			BudgetStatus status = storeApi.getBudgetStatus(session);
			assertThat(status).isInstanceOf(BudgetStatus.Exceeded.class);
		}
	}

	@Nested
	@DisplayName("Budget Tool Reset")
	class ToolResetTests {

		@Test
		@DisplayName("should reset tool invocation flags")
		void resetInvocationFlags() {
			ShoppingSession session = ShoppingSession.create("test-reset", null);

			budgetTool.getBudgetStatus(session);
			assertThat(budgetTool.getBudgetStatusInvoked()).isTrue();

			budgetTool.reset();
			assertThat(budgetTool.getBudgetStatusInvoked()).isFalse();
		}
	}
}

