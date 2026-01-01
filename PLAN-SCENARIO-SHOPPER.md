# Shopping Scenario Implementation Plan

This document outlines the implementation plan for enhancing the shopping scenario described in `src/test/java/org/javai/springai/scenarios/shopping/README.md`. It identifies what currently exists, what needs to be built, and the approach for creating a mock infrastructure to make the scenario realistic and testable.

---

## Current State Assessment

### Already Implemented

| Capability | Implementation | Status |
|------------|----------------|--------|
| Start shopping session | `ShoppingActions.startSession()` | ✓ Complete |
| Present special offers | `ShoppingActions.presentOffers()` + `SpecialOfferTool.listSpecialOffers()` | ✓ Complete (hardcoded offers) |
| Add item to basket | `ShoppingActions.addItem(product, quantity)` | ✓ Complete (no inventory validation) |
| Add party snacks | `ShoppingActions.addPartySnacks(partySize)` | ✓ Complete (hardcoded items) |
| View basket summary | `ShoppingActions.viewBasketSummary()` | ✓ Complete |
| Remove item from basket | `ShoppingActions.removeItem(product)` | ✓ Complete |
| Compute basket total | `ShoppingActions.computeTotal()` | ✓ Stub only (no actual pricing) |
| Checkout basket | `ShoppingActions.checkoutBasket()` | ✓ Complete |
| Request feedback | `ShoppingActions.requestFeedback()` | ✓ Complete |
| Pending state for missing params | Framework + tests | ✓ Complete |
| Error state for invalid requests | Framework + tests | ✓ Complete |
| Multi-turn conversation context | ConversationManager | ✓ Complete |

### Gaps to Address

| Capability | Current State | Required Enhancement |
|------------|---------------|---------------------|
| Inventory awareness | None—any product name is accepted | Tool to query product catalog and stock levels |
| Stock level validation | None | Check stock before adding; warn if low |
| Out-of-stock handling | Test exists but no real validation | Reject or suggest alternatives |
| Price computation | Stub only | Calculate actual totals with prices |
| Discount application | Offers listed but not applied | Apply discounts at checkout |
| Customer identity | None | Associate session with customer ID |
| Purchase history | None | Store and query past purchases |
| Personalised recommendations | None | Tool to suggest products based on history |
| Budget constraints | None | Track and respect session budget |
| Mission-based shopping | Limited (`addPartySnacks`) | Full mission interpreter with dietary filters |
| Update item quantity | None | Action to change quantity without remove/add |
| Product categories | None | Categorised catalog for filtering |
| Dietary attributes | None | Allergen and dietary info per product |

---

## Mock Infrastructure Design

To make the scenario realistic without external dependencies, we will create a mock store infrastructure in a sub-package:

```
src/test/java/org/javai/springai/scenarios/shopping/
└── store/                          ← New sub-package
    ├── MockStoreApi.java           ← Façade coordinating all mock services
    ├── ProductCatalog.java         ← Product definitions and categories
    ├── InventoryService.java       ← Stock levels and availability
    ├── PricingService.java         ← Prices and discount computation
    ├── CustomerProfileService.java ← Preferences and purchase history
    └── model/                      ← Domain models
        ├── Product.java
        ├── StockLevel.java
        ├── CustomerProfile.java
        ├── PurchaseHistory.java
        └── SpecialOffer.java
```

### 1. Product Catalog (`ProductCatalog.java`)

**Purpose**: Define the store's product inventory with rich metadata.

**Data Model** (`Product.java`):
```java
record Product(
    String sku,
    String name,
    String category,           // "beverages", "snacks", "dairy", "produce", etc.
    BigDecimal unitPrice,
    String unit,               // "bottle", "pack", "kg", "each"
    Set<String> dietaryFlags,  // "vegetarian", "vegan", "gluten-free"
    Set<String> allergens,     // "peanuts", "dairy", "gluten"
    String description
) {}
```

**Sample Catalog** (approximately 20-30 products):
- Beverages: Coca Cola, Coke Zero, Sparkling Water, Orange Juice
- Snacks: Crisps (variety), Mixed Nuts, Hummus, Vegetable Crudités
- Dairy: Milk, Cheese selection, Yogurt
- Produce: Fruit Salad, Caprese ingredients
- Party items: Party platters, serving packs

**Key Methods**:
- `findByName(String name)` → fuzzy matching for natural language
- `findByCategory(String category)` → category browsing
- `findByDietaryFlag(String flag)` → filter vegetarian, vegan, etc.
- `findWithoutAllergens(Set<String> allergens)` → allergy-safe search
- `searchProducts(String query)` → full-text search

---

### 2. Inventory Service (`InventoryService.java`)

**Purpose**: Track stock levels and availability.

**Data Model** (`StockLevel.java`):
```java
record StockLevel(
    String sku,
    int quantityAvailable,
    int lowStockThreshold,     // e.g., 5 units
    boolean discontinued
) {
    boolean isLowStock() { return quantityAvailable <= lowStockThreshold; }
    boolean isOutOfStock() { return quantityAvailable == 0 || discontinued; }
}
```

**Key Methods**:
- `getStockLevel(String sku)` → current availability
- `checkAvailability(String sku, int requestedQty)` → returns `AvailabilityResult`
- `reserveStock(String sku, int qty)` → decrement for basket (reversible)
- `releaseStock(String sku, int qty)` → return to inventory (on remove)
- `findAlternatives(String sku)` → suggest similar in-stock products

**Availability Result**:
```java
sealed interface AvailabilityResult {
    record Available(int quantity) implements AvailabilityResult {}
    record PartiallyAvailable(int available, int requested) implements AvailabilityResult {}
    record OutOfStock(List<Product> alternatives) implements AvailabilityResult {}
    record Discontinued(List<Product> alternatives) implements AvailabilityResult {}
}
```

---

### 3. Pricing Service (`PricingService.java`)

**Purpose**: Calculate prices and apply discounts.

**Data Model** (`SpecialOffer.java`):
```java
record SpecialOffer(
    String offerId,
    String description,
    Set<String> applicableSkus,    // or empty for category-wide
    String applicableCategory,      // or null for SKU-specific
    DiscountType type,
    BigDecimal discountValue        // percentage or fixed amount
) {}

enum DiscountType { PERCENTAGE, FIXED_AMOUNT, BUY_X_GET_Y }
```

**Key Methods**:
- `getUnitPrice(String sku)` → base price lookup
- `calculateLineTotal(String sku, int qty)` → price × quantity
- `getApplicableOffers(Set<String> basketSkus)` → active promotions for basket
- `calculateBasketTotal(Map<String, Integer> basket)` → full computation
- `calculateBasketTotalWithDiscounts(...)` → apply offers, return breakdown

**Pricing Breakdown**:
```java
record PricingBreakdown(
    List<LineItem> items,
    BigDecimal subtotal,
    List<AppliedDiscount> discounts,
    BigDecimal total
) {}
```

---

### 4. Customer Profile Service (`CustomerProfileService.java`)

**Purpose**: Manage customer identity, preferences, and history.

**Data Model** (`CustomerProfile.java`):
```java
record CustomerProfile(
    String customerId,
    String name,
    Set<String> dietaryPreferences,    // "vegetarian", "low-sugar"
    Set<String> allergens,             // personal allergen list
    BigDecimal defaultBudget,          // optional spending limit
    List<String> favouriteCategories
) {}
```

**Data Model** (`PurchaseHistory.java`):
```java
record PurchaseHistory(
    String customerId,
    List<PastOrder> orders
) {}

record PastOrder(
    Instant timestamp,
    Map<String, Integer> items,
    BigDecimal total
) {}
```

**Key Methods**:
- `getProfile(String customerId)` → retrieve preferences
- `getPurchaseHistory(String customerId)` → past orders
- `getFrequentlyBoughtItems(String customerId)` → top products
- `getRecommendations(String customerId, Map<String, Integer> currentBasket)` → suggestions
- `recordPurchase(String customerId, Map<String, Integer> basket, BigDecimal total)` → save order

---

### 5. Mock Store API Façade (`MockStoreApi.java`)

**Purpose**: Single entry point coordinating all services; the interface that tools will call.

```java
public class MockStoreApi {
    private final ProductCatalog catalog;
    private final InventoryService inventory;
    private final PricingService pricing;
    private final CustomerProfileService customers;
    
    // Catalog queries
    public List<Product> searchProducts(String query);
    public List<Product> getProductsByCategory(String category);
    public List<Product> getSafeProducts(Set<String> excludeAllergens);
    
    // Inventory queries
    public AvailabilityResult checkAvailability(String productName, int quantity);
    public List<Product> getAlternatives(String productName);
    
    // Pricing
    public PricingBreakdown calculateTotal(Map<String, Integer> basket);
    public List<SpecialOffer> getActiveOffers();
    public List<SpecialOffer> getOffersForCustomer(String customerId);
    
    // Customer
    public CustomerProfile getCustomer(String customerId);
    public List<Product> getRecommendationsFor(String customerId);
    public List<Product> getFrequentPurchases(String customerId);
    
    // Mission support
    public MissionPlan planMission(MissionRequest request);
}
```

---

## Implementation Phases

### Phase 1: Core Mock Infrastructure ✅ COMPLETE

**Objective**: Establish the mock store with product catalog, inventory, and pricing.

| Task | Description | Deliverable |
|------|-------------|-------------|
| 1.1 | Create `store/` sub-package structure | Package hierarchy |
| 1.2 | Implement `Product` record and `ProductCatalog` | Catalog with ~25 products |
| 1.3 | Implement `StockLevel` and `InventoryService` | Stock tracking with low-stock detection |
| 1.4 | Implement `SpecialOffer` and `PricingService` | Price calculation with discounts |
| 1.5 | Create `MockStoreApi` façade | Unified API |
| 1.6 | Write unit tests for mock infrastructure | Test coverage for all services |

**Outcome**: A testable mock store that can answer product, inventory, and pricing queries.

---

### Phase 2: Inventory-Aware Shopping ✅ COMPLETE

**Objective**: Integrate stock awareness into the shopping workflow.

| Task | Description | Deliverable |
|------|-------------|-------------|
| 2.1 | Create `InventoryTool` | Tool exposing `checkAvailability`, `getAlternatives` |
| 2.2 | Create `ProductSearchTool` | Tool exposing `searchProducts`, `getByCategory` |
| 2.3 | Enhance `addItem` action | Validate against inventory before adding |
| 2.4 | Add `updateItemQuantity` action | Change quantity of existing basket item |
| 2.5 | Implement low-stock warnings | Return advisory message when stock is low |
| 2.6 | Implement out-of-stock handling | Return alternatives when unavailable |
| 2.7 | Update `SpecialOfferTool` | Query `PricingService` instead of hardcoded |
| 2.8 | Write integration tests | Stock scenarios (available, low, out) |

**Outcome**: The assistant validates stock before adding items and proactively advises on availability.

---

### Phase 3: Real Pricing and Checkout ✅ COMPLETE

**Objective**: Compute actual basket totals with discounts.

| Task | Description | Deliverable |
|------|-------------|-------------|
| 3.1 | Create `PricingTool` | Tool exposing `calculateTotal`, `getApplicableOffers` |
| 3.2 | Enhance `computeTotal` action | Return actual pricing breakdown |
| 3.3 | Enhance `viewBasketSummary` action | Show items with prices and subtotals |
| 3.4 | Apply discounts at checkout | Integrate offer application |
| 3.5 | Update persona constraints | Ensure assistant uses tools for prices |
| 3.6 | Write pricing integration tests | Discount application scenarios |

**Outcome**: The assistant can provide accurate totals and clearly communicates applied discounts.

---

### Phase 4: Customer Personalisation ✅ COMPLETE

**Objective**: Support customer identity, preferences, and recommendations.

| Task | Description | Deliverable |
|------|-------------|-------------|
| 4.1 | Implement `CustomerProfile` and `PurchaseHistory` | Data models |
| 4.2 | Implement `CustomerProfileService` | Profile and history management |
| 4.3 | Create `CustomerTool` | Tool exposing `getRecommendations`, `getFrequentPurchases` |
| 4.4 | Add customer ID to session context | Track customer across turns |
| 4.5 | Create `getPersonalisedOffers` action or tool | Offers tailored to customer |
| 4.6 | Create `showRecommendations` action | Surface personalised suggestions |
| 4.7 | Seed mock data | 3-5 sample customers with diverse histories |
| 4.8 | Write personalisation tests | Recommendation accuracy |

**Outcome**: The assistant recognises returning customers and offers relevant suggestions.

---

### Phase 5: Budget & Mission Store Infrastructure

**Objective**: Extend the mock store with budget tracking and mission planning capabilities at the infrastructure level only.

#### Part A: Budget Infrastructure

| Task | Description | Deliverable |
|------|-------------|-------------|
| 5.1 | Add `budgetLimit` and `budgetRemaining` to session state | `ShoppingSession` model |
| 5.2 | Implement `BudgetService` | Tracks spending, calculates remaining budget |
| 5.3 | Add budget-aware methods to `MockStoreApi` | `setBudget`, `getRemainingBudget`, `wouldExceedBudget` |
| 5.4 | Implement budget validation in pricing | Check if basket + new item exceeds limit |
| 5.5 | Write budget infrastructure unit tests | Service-level budget tracking tests |

**Budget Models**:
```java
record ShoppingSession(
    String sessionId,
    String customerId,              // optional
    BigDecimal budgetLimit,         // optional spending cap
    BigDecimal currentSpend,        // running total
    Instant startedAt
) {}
```

#### Part B: Mission Infrastructure

| Task | Description | Deliverable |
|------|-------------|-------------|
| 5.6 | Define `MissionRequest` model | Captures occasion, headcount, dietary needs, allergens, budget |
| 5.7 | Define `MissionPlan` model | Proposed products with quantities, rationale, and estimated cost |
| 5.8 | Define `MissionItem` model | Individual item in a mission plan with quantity rationale |
| 5.9 | Implement `MissionPlanningService` | Algorithm to select products satisfying constraints |
| 5.10 | Add portion scaling logic | Calculate quantities based on headcount and occasion |
| 5.11 | Add constraint filtering | Filter by dietary flags, allergens, and budget |
| 5.12 | Integrate mission planning into `MockStoreApi` | `planMission(MissionRequest)` method |
| 5.13 | Write mission infrastructure unit tests | Planning algorithm tests |

**Mission Models**:
```java
record MissionRequest(
    String description,              // "midday party"
    int headcount,                   // 10 people
    Set<String> dietaryRequirements, // ["vegetarian"]
    Set<String> allergenExclusions,  // ["peanuts"]
    BigDecimal budgetLimit,          // optional
    String occasion                  // "party", "dinner", "picnic", "snacks"
) {}

record MissionPlan(
    MissionRequest request,
    List<MissionItem> items,
    BigDecimal estimatedTotal,
    List<String> notes              // e.g., "Budget allows for extras"
) {}

record MissionItem(
    Product product,
    int quantity,
    String rationale                // e.g., "2 bottles per person"
) {}
```

**Outcome**: Store infrastructure supports budget tracking and mission planning queries without any changes to actions, tools, or persona.

---

### Phase 6: Budget-Aware Shopping Integration

**Objective**: Wire budget capabilities into the shopping workflow via actions, tools, and persona.

| Task | Description | Deliverable |
|------|-------------|-------------|
| 6.1 | Create `BudgetTool` | Tool exposing `getRemainingBudget`, `wouldExceedBudget` |
| 6.2 | Add `setBudget` action | Customer states spending limit |
| 6.3 | Enhance `addItem` action | Warn if adding item would exceed budget |
| 6.4 | Enhance `computeTotal` action | Show budget remaining if set |
| 6.5 | Enhance `showRecommendations` action | Filter by budget if set |
| 6.6 | Update persona principles | Add "Respect customer's budget" constraint |
| 6.7 | Update persona style guidance | "Mention remaining budget when relevant" |
| 6.8 | Write budget integration tests | End-to-end budget scenarios |

**Outcome**: The assistant tracks budget, warns before overspending, and respects budget in recommendations.

---

### Phase 7: Mission-Based Shopping Integration

**Objective**: Wire mission planning into the shopping workflow via tools and actions.

| Task | Description | Deliverable |
|------|-------------|-------------|
| 7.1 | Create `MissionTool` | Tool exposing `planMission`, `refineMission` |
| 7.2 | Create `startMission` action | Customer describes shopping goal |
| 7.3 | Create `reviewMissionPlan` action | Present proposed items for approval |
| 7.4 | Create `executeMission` action | Add approved mission items to basket |
| 7.5 | Create `adjustMissionPlan` action | Modify quantities or swap items |
| 7.6 | Update persona principles | Add mission-aware guidance |
| 7.7 | Write mission integration tests | Vegetarian party, allergy exclusions, budget-constrained |
| 7.8 | Write complex multi-turn mission tests | Mission refinement across turns |

**Mission Workflow**:
1. Customer: "Help me prepare for a party of 10 vegetarians"
2. Assistant calls `planMission` tool → receives `MissionPlan`
3. Assistant presents plan via `reviewMissionPlan` action
4. Customer approves or requests changes
5. Assistant executes via `executeMission` action

**Outcome**: The assistant can orchestrate complex shopping goals, proposing and refining product sets that satisfy all constraints.

---

## Enhanced Actions Summary

After all phases, `ShoppingActions` will include:

| Action | Parameters | Description | Phase |
|--------|------------|-------------|-------|
| `startSession` | `customerId?` | Start session, optionally authenticated | 1-4 ✅ |
| `presentOffers` | — | Surface current promotions | 1-3 ✅ |
| `addItem` | `product`, `quantity` | Add with inventory validation | 2 ✅ |
| `updateItemQuantity` | `product`, `newQuantity` | Change quantity | 2 ✅ |
| `removeItem` | `product` | Remove from basket | 1-2 ✅ |
| `viewBasketSummary` | — | Show items, prices, subtotal | 3 ✅ |
| `computeTotal` | — | Calculate total with discounts | 3 ✅ |
| `showRecommendations` | — | Display personalised suggestions | 4 ✅ |
| `checkoutBasket` | — | Complete purchase, record history | 4 ✅ |
| `requestFeedback` | — | End-of-session feedback | 1 ✅ |
| `setBudget` | `amount` | Set session spending limit | 6 |
| `startMission` | `description`, `headcount`, etc. | Begin mission-based shopping | 7 |
| `reviewMissionPlan` | — | Present proposed items | 7 |
| `executeMission` | — | Add mission items to basket | 7 |
| `adjustMissionPlan` | `changes` | Modify mission plan | 7 |

---

## New Tools Summary

| Tool | Methods | Purpose | Phase |
|------|---------|---------|-------|
| `ProductSearchTool` | `searchProducts`, `getByCategory`, `getSafeProducts` | Catalog queries | 2 ✅ |
| `InventoryTool` | `checkAvailability`, `getAlternatives`, `getStockLevel` | Stock queries | 2 ✅ |
| `PricingTool` | `calculateTotal`, `getApplicableOffers` | Pricing queries | 3 ✅ |
| `EnhancedSpecialOfferTool` | `listSpecialOffers`, `getOffersForProducts` | Offer queries | 3 ✅ |
| `CustomerTool` | `getRecommendations`, `getFrequentPurchases`, `getProfile`, `checkProductSafety` | Customer queries | 4 ✅ |
| `BudgetTool` | `getRemainingBudget`, `wouldExceedBudget` | Budget queries | 6 |
| `MissionTool` | `planMission`, `refineMission` | Mission planning | 7 |

---

## Test Scenarios Summary

### Completed (Phases 1-4) ✅

| Scenario | Description | Phase |
|----------|-------------|-------|
| Low stock warning | Add item with stock < threshold; verify warning | 2 ✅ |
| Out of stock rejection | Add unavailable item; verify alternatives offered | 2 ✅ |
| Partial availability | Request 10, only 5 in stock; verify partial offer | 2 ✅ |
| Price calculation | Add items; verify correct total with discounts | 3 ✅ |
| Discount application | Add offer-eligible item; verify discount reflected | 3 ✅ |
| Customer recommendations | Authenticated customer; verify personalised suggestions | 4 ✅ |
| Frequent purchases | Query history; verify top items returned | 4 ✅ |
| Product safety check | Verify allergen warnings for customer | 4 ✅ |
| Purchase history recording | Checkout records order in customer history | 4 ✅ |

### Pending (Phases 5-7)

| Scenario | Description | Phase |
|----------|-------------|-------|
| Budget tracking | Set budget; verify remaining tracked | 5 |
| Budget validation | Check if basket would exceed budget | 5 |
| Mission planning (unit) | Plan mission; verify constraint satisfaction | 5 |
| Portion scaling | Verify quantities scale with headcount | 5 |
| Budget warning | Set budget, approach limit; verify warning | 6 |
| Budget enforcement | Set budget, try to exceed; verify blocking | 6 |
| Budget-aware recommendations | Recommendations filtered by remaining budget | 6 |
| Simple mission | "Snacks for 6"; verify products and quantities | 7 |
| Complex mission | Vegetarian party with allergy; verify exclusions | 7 |
| Mission with budget | Party under £50; verify cost-optimised selection | 7 |
| Mission refinement | Multi-turn mission adjustment | 7 |

---

## Mock Data Seeding

The mock infrastructure will be initialised with realistic sample data:

### Products (~25 items across categories)

| Category | Sample Products |
|----------|-----------------|
| Beverages | Coca Cola, Coke Zero, Sparkling Water, Orange Juice, Lemonade |
| Snacks | Sea Salt Crisps, Cheese & Onion Crisps, Mixed Nuts, Hummus (tub), Guacamole |
| Dairy | Semi-skimmed Milk (1L), Cheddar Cheese, Greek Yogurt |
| Produce | Fruit Salad Bowl, Vegetable Crudités Tray, Cherry Tomatoes |
| Party | Caprese Skewers (20 pack), Bruschetta Platter, Cheese Board Selection |
| Bakery | Baguette, Croissants (4 pack), Brownie Bites |

### Inventory Levels (varied for testing)

| Product | Stock | Threshold | Notes |
|---------|-------|-----------|-------|
| Coke Zero | 50 | 10 | Plentiful |
| Sparkling Water | 8 | 10 | Low stock |
| Mixed Nuts | 0 | 5 | Out of stock |
| Discontinued Soda | 0 | — | Discontinued |

### Customers (3-5 profiles)

| Customer ID | Name | Preferences | History |
|-------------|------|-------------|---------|
| `cust-001` | Alex | Vegetarian, low-sugar | Coke Zero, Hummus, Fruit Salad (frequent) |
| `cust-002` | Jordan | No restrictions | Crisps, Coca Cola, Cheese (frequent) |
| `cust-003` | Sam | Vegan, nut allergy | Sparkling Water, Guacamole, Vegetables |

### Special Offers (5-8 promotions)

| Offer | Type | Value | Applicable |
|-------|------|-------|------------|
| Summer Refresh | 10% off | Percentage | Coca Cola, Coke Zero |
| Party Pack | 15% off | Percentage | All snacks category |
| Dairy Deal | £1 off | Fixed | Milk, Yogurt |

---

## Success Criteria

The enhanced shopping scenario is complete when:

1. **Inventory-aware**: Adding an out-of-stock item returns alternatives; low-stock triggers warnings
2. **Price-accurate**: Basket totals reflect real prices and applied discounts
3. **Personalised**: Authenticated customers receive tailored recommendations
4. **Budget-conscious**: Budget constraints are tracked and respected
5. **Mission-capable**: Complex shopping missions produce valid, constraint-satisfying product sets
6. **Fully tested**: All new capabilities have corresponding test coverage
7. **Documented**: README reflects the enhanced capabilities

---

## Appendix: Package Structure After Implementation

```
src/test/java/org/javai/springai/scenarios/shopping/
├── README.md
├── ActionResult.java                    ✅ Phase 2
├── AddItemRequest.java                  (existing)
├── InventoryAwareShoppingActions.java   ✅ Phase 2-4
├── ShoppingPersonaSpec.java             ✅ Phase 3
├── ShoppingApplicationScenarioTest.java (existing, extended)
│
├── # Tools
├── SpecialOfferTool.java                (existing)
├── EnhancedSpecialOfferTool.java        ✅ Phase 3
├── ProductSearchTool.java               ✅ Phase 2
├── InventoryTool.java                   ✅ Phase 2
├── PricingTool.java                     ✅ Phase 3
├── CustomerTool.java                    ✅ Phase 4
├── BudgetTool.java                      Phase 6
├── MissionTool.java                     Phase 7
│
├── # Tests
├── MockStoreApiTest.java                ✅ Phase 1
├── InventoryAwareShoppingTest.java      ✅ Phase 2
├── PricingIntegrationTest.java          ✅ Phase 3
├── CustomerPersonalisationTest.java     ✅ Phase 4
├── BudgetIntegrationTest.java           Phase 6
├── MissionShoppingTest.java             Phase 7
│
└── store/                               ✅ Phase 1
    ├── MockStoreApi.java                ✅ Phase 1-4
    ├── ProductCatalog.java              ✅ Phase 1
    ├── InventoryService.java            ✅ Phase 1
    ├── PricingService.java              ✅ Phase 1
    ├── CustomerProfileService.java      ✅ Phase 4
    ├── BudgetService.java               Phase 5
    ├── MissionPlanningService.java      Phase 5
    └── model/
        ├── Product.java                 ✅ Phase 1
        ├── StockLevel.java              ✅ Phase 1
        ├── AvailabilityResult.java      ✅ Phase 1
        ├── SpecialOffer.java            ✅ Phase 1
        ├── DiscountType.java            ✅ Phase 1
        ├── LineItem.java                ✅ Phase 1
        ├── AppliedDiscount.java         ✅ Phase 1
        ├── PricingBreakdown.java        ✅ Phase 1
        ├── CustomerProfile.java         ✅ Phase 4
        ├── PurchaseHistory.java         ✅ Phase 4
        ├── ShoppingSession.java         Phase 5
        ├── MissionRequest.java          Phase 5
        ├── MissionPlan.java             Phase 5
        └── MissionItem.java             Phase 5
```

