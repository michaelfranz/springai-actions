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

### Phase 5: Budget-Aware Shopping

**Objective**: Respect customer budget constraints throughout the session.

| Task | Description | Deliverable |
|------|-------------|-------------|
| 5.1 | Add `setBudget` action | Customer states spending limit |
| 5.2 | Track budget in session context | Persist budget constraint |
| 5.3 | Warn when approaching/exceeding budget | Proactive budget alerts |
| 5.4 | Update persona principles | Respect budget in recommendations |
| 5.5 | Write budget constraint tests | Add item exceeds budget scenarios |

**Outcome**: The assistant tracks budget and warns before the customer overspends.

---

### Phase 6: Mission-Based Shopping

**Objective**: Support complex shopping missions with constraints.

| Task | Description | Deliverable |
|------|-------------|-------------|
| 6.1 | Define `MissionRequest` model | Captures party size, dietary needs, budget, occasion |
| 6.2 | Define `MissionPlan` model | Proposed product set with quantities and rationale |
| 6.3 | Implement mission planning in `MockStoreApi` | Algorithm to select products |
| 6.4 | Create `MissionTool` | Tool exposing `planMission` |
| 6.5 | Create `executeMission` action | Add mission products to basket with confirmation |
| 6.6 | Handle dietary exclusions | Filter allergens (e.g., peanut allergy) |
| 6.7 | Handle portion scaling | Calculate quantities for party size |
| 6.8 | Write mission scenario tests | Vegetarian party, budget-constrained dinner |

**Mission Request Model**:
```java
record MissionRequest(
    String description,             // "midday party"
    int headcount,                  // 10 people
    Set<String> dietaryRequirements, // ["vegetarian"]
    Set<String> allergenExclusions,  // ["peanuts"]
    BigDecimal budgetLimit,          // optional
    String occasion                  // "party", "dinner", "picnic"
) {}
```

**Outcome**: The assistant can orchestrate complex shopping goals, proposing complete product sets that satisfy all constraints.

---

## Enhanced Actions Summary

After all phases, `ShoppingActions` will include:

| Action | Parameters | Description |
|--------|------------|-------------|
| `startSession` | `customerId?` | Start session, optionally authenticated |
| `presentOffers` | — | Surface current promotions |
| `addItem` | `product`, `quantity` | Add with inventory validation |
| `updateItemQuantity` | `product`, `newQuantity` | Change quantity |
| `removeItem` | `product` | Remove from basket |
| `viewBasketSummary` | — | Show items, prices, subtotal |
| `computeTotal` | — | Calculate total with discounts |
| `setBudget` | `amount` | Set session spending limit |
| `showRecommendations` | — | Display personalised suggestions |
| `executeMission` | `missionPlan` | Add mission items with confirmation |
| `checkoutBasket` | — | Complete purchase |
| `requestFeedback` | — | End-of-session feedback |

---

## New Tools Summary

| Tool | Methods | Purpose |
|------|---------|---------|
| `ProductSearchTool` | `searchProducts`, `getByCategory`, `getSafeProducts` | Catalog queries |
| `InventoryTool` | `checkAvailability`, `getAlternatives`, `getStockLevel` | Stock queries |
| `PricingTool` | `calculateTotal`, `getApplicableOffers` | Pricing queries |
| `CustomerTool` | `getRecommendations`, `getFrequentPurchases`, `getProfile` | Customer queries |
| `MissionTool` | `planMission` | Mission planning |

---

## Test Scenarios to Add

Building on existing tests, add coverage for:

| Scenario | Description |
|----------|-------------|
| Low stock warning | Add item with stock < threshold; verify warning |
| Out of stock rejection | Add unavailable item; verify alternatives offered |
| Partial availability | Request 10, only 5 in stock; verify partial offer |
| Price calculation | Add items; verify correct total with discounts |
| Discount application | Add offer-eligible item; verify discount reflected |
| Customer recommendations | Authenticated customer; verify personalised suggestions |
| Frequent purchases | Query history; verify top items returned |
| Budget warning | Set budget, exceed it; verify warning |
| Budget enforcement | Set budget, try to exceed; verify blocking option |
| Simple mission | "Snacks for 6"; verify appropriate products and quantities |
| Complex mission | Vegetarian party with allergy; verify exclusions applied |
| Mission with budget | Party under £50; verify cost-optimised selection |

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
├── AddItemRequest.java              (existing)
├── ShoppingActions.java             (enhanced)
├── ShoppingApplicationScenarioTest.java (extended)
├── SpecialOfferTool.java            (refactored to use PricingService)
├── ProductSearchTool.java           (new)
├── InventoryTool.java               (new)
├── PricingTool.java                 (new)
├── CustomerTool.java                (new)
├── MissionTool.java                 (new)
└── store/                           (new sub-package)
    ├── MockStoreApi.java
    ├── ProductCatalog.java
    ├── InventoryService.java
    ├── PricingService.java
    ├── CustomerProfileService.java
    └── model/
        ├── Product.java
        ├── StockLevel.java
        ├── AvailabilityResult.java
        ├── SpecialOffer.java
        ├── PricingBreakdown.java
        ├── CustomerProfile.java
        ├── PurchaseHistory.java
        ├── MissionRequest.java
        └── MissionPlan.java
```

