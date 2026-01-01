# Shopping Scenario

This package implements a comprehensive shopping assistant scenario that models the conversational dynamics of a Shopify-like retail experience. The scenario demonstrates how a natural language interface backed by structured actions can orchestrate a complete shopping workflow—from browsing to basket management to checkout.

## The Shopping Workflow

Shopping is not a single transaction but a *workflow*—a sequence of interrelated activities spanning an entire session. A customer arrives with intent, browses, curates a basket, reconsiders, and eventually commits to purchase. This scenario models that journey as a series of conversational turns, each potentially advancing the workflow state.

### Session Lifecycle

A shopping session begins explicitly when the customer indicates intent to shop:

> "I want to start a new shopping basket"

This triggers `startSession`, which initialises a fresh basket and resets any prior state. The assistant immediately proactively surfaces today's special offers via the `SpecialOfferTool`, priming the customer with relevant deals before they dive in.

The session concludes with checkout confirmation and optional feedback collection:

> "I'm ready to pay"  
> "How was your experience?"

Between these bookends lies the heart of the workflow—iterative basket curation.

---

## The Assistant's Persona

The shopping assistant embodies the persona of a knowledgeable store associate who:

- **Has real-time inventory visibility**: knows what's in stock, what's running low, and what's unavailable
- **Understands the customer**: recognises returning shoppers, recalls their purchase history, and anticipates preferences
- **Respects the customer's context**: honours expressed budget constraints and adapts recommendations accordingly
- **Supports mission-based shopping**: can orchestrate complex shopping goals like catering an event

The assistant operates under clear constraints:

1. Never invent products or prices—always consult the inventory and pricing tools
2. Never commit a checkout without explicit customer confirmation
3. Confirm missing quantities before adding items to the basket
4. Keep responses action-focused; don't overwhelm with information

---

## Core Shopping Activities

### Starting and Ending Sessions

```
Customer: "I'd like to start shopping"
Assistant: [startSession] → Basket initialised
           [listSpecialOffers] → "Today we have 10% off Coke Zero and 5% off party-size nuts..."
```

The session establishes context. All subsequent turns operate within this context until checkout or abandonment.

### Adding Items to the Basket

Customers can add items in various ways:

**Explicit product and quantity:**
> "Add 6 bottles of Coke Zero to my basket"

**Product without quantity (triggers pending state):**
> "Add coke zero"  
> *Assistant*: "How many bottles would you like?"  
> "Make it 4"

**Mission-driven additions:**
> "Add crisps and nuts for around 10 people"

The assistant interprets intent and maps it to appropriate actions with sensible quantities.

### Viewing and Managing the Basket

At any point, the customer can inspect their basket:

> "Show me my basket"  
> "What have I added so far?"

The assistant surfaces a summary: items, quantities, and running total.

Customers can also remove items:

> "Remove the Coke Zero"  
> "Actually, take out the nuts"

Or adjust quantities:

> "Change the Coke Zero to just 2 bottles"  
> "I need twice as much of the crisps"

### Computing Totals and Checkout

When ready to commit:

> "What's my total?"  
> "I'm ready to checkout"

The assistant computes the basket total, applies any relevant discounts, and proceeds to checkout—but *only* with explicit confirmation. This constraint exists because payment is an irreversible action.

---

## Intelligent Recommendations

### Special Offers and Bargains

The assistant proactively surfaces relevant offers at session start and when adding products that have active promotions:

```
Customer: "Add some Coca Cola"
Assistant: "Adding Coca Cola to your basket. By the way, regular Coca Cola 
           is 10% off today, and Coke Zero is also discounted if you'd prefer that."
```

Offers are retrieved via the `SpecialOfferTool`, which queries the store's current promotions.

### Personalised Recommendations

For authenticated customers, the assistant has access to purchase history and preferences:

> "Show me things I might like"

This invokes a tool that queries the customer's profile and returns relevant product suggestions—perhaps items they've bought before, items that complement their current basket, or products from categories they frequently browse.

### Stock-Aware Advice

The assistant monitors inventory levels in real-time:

**Low stock warnings:**
```
Customer: "Add 20 bottles of Coke Zero"
Assistant: "I can add them, but just so you know—we only have 8 bottles 
           left in stock. Would you like 8, or should I suggest an alternative?"
```

**Alternative suggestions when out of stock:**
```
Customer: "Add UnavailableProduct"
Assistant: "I'm sorry, UnavailableProduct is currently out of stock. 
           However, we have SimilarProduct which customers often choose 
           as an alternative. Would you like to try that?"
```

---

## Mission-Based Shopping

One of the most powerful capabilities is supporting shopping "missions"—complex goals that require the assistant to reason about quantities, dietary requirements, and inventory constraints.

### Example: Party Planning

```
Customer: "Help me prepare for a midday party of 10 vegetarians, 
           one of whom has a severe peanut allergy"
```

The assistant must:

1. **Interpret the mission**: midday → lunch/snacks; 10 people → scaling; vegetarian → dietary filter; peanut allergy → critical exclusion
2. **Query inventory**: find vegetarian-friendly options, check for peanut-containing products or cross-contamination risks
3. **Calculate quantities**: scale portions appropriately for 10 guests
4. **Propose a plan**: suggest a coherent set of products that satisfy all constraints
5. **Seek confirmation**: present the proposed basket for customer approval before adding

```
Assistant: "For a vegetarian midday party of 10, I'd suggest:
           - Hummus platter (serves 12) × 1
           - Vegetable crudités tray × 2  
           - Caprese skewers (20 pack) × 1
           - Sparkling water (6-pack) × 2
           - Fruit salad bowl (serves 10) × 1
           
           All items are peanut-free and suitable for vegetarians. 
           The total comes to approximately £45. Shall I add these to your basket?"
```

### Budget-Conscious Shopping

Missions can include budget constraints:

> "I need to feed 6 people dinner for under £30"

The assistant must balance cost against quantity and quality, suggesting economical options while ensuring the meal is satisfying.

---

## Workflow State Management

The shopping workflow maintains state across conversational turns:

| State Element | Persisted Across Turns |
|---------------|------------------------|
| Basket contents (items, quantities) | ✓ |
| Applied discounts and offers | ✓ |
| Customer ID and preferences | ✓ |
| Session budget constraint | ✓ |
| Active mission context | ✓ |
| Checkout confirmation status | ✓ |

This allows natural multi-turn conversations:

```
Turn 1: "Start shopping"          → Session created
Turn 2: "Add 3 Coke Zero"         → Item added
Turn 3: "Also add party snacks for 5" → More items added
Turn 4: "What's in my basket?"    → Summary with both items
Turn 5: "Remove the Coke Zero"    → Item removed
Turn 6: "Let's checkout"          → Checkout initiated
```

---

## Error Handling and Edge Cases

### Insufficient Information

When the customer provides incomplete information, the assistant enters a pending state:

```
Customer: "Add milk"
Assistant: "What quantity of milk would you like—1 litre, 2 litres, or a specific amount?"
```

The system tracks what information is missing and resumes once provided.

### Unrecognised Requests

If a customer request falls outside the shopping domain:

```
Customer: "Change the oil in my car"
Assistant: "I'm sorry, I can only help with shopping-related tasks. 
           Would you like to continue with your basket?"
```

The plan enters an error state with a helpful redirect.

### Out-of-Stock Items

When requested items are unavailable:

```
Customer: "Add 10 bottles of DiscontinuedBrand"
Assistant: "I'm sorry, DiscontinuedBrand is no longer available. 
           Based on your previous purchases, you might enjoy NewBrand 
           which has a similar taste profile. Would you like to try it?"
```

---

## Implementation Notes

### Actions vs Tools

This scenario demonstrates the distinction between **actions** (domain-specific operations the assistant can execute) and **tools** (Spring AI integrations for external queries):

- **Actions** (`ShoppingActions`): `startSession`, `addItem`, `removeItem`, `viewBasketSummary`, `checkoutBasket`, etc.
- **Tools** (`SpecialOfferTool`): `listSpecialOffers` for querying promotional data

Actions modify workflow state; tools gather information to inform decisions.

### The Pending State Pattern

When required parameters are missing, the planner returns a `PENDING` status with the list of parameters still needed. The conversation manager preserves this context so subsequent user messages can provide the missing values without repeating the entire request.

### Persona-Driven Behaviour

The assistant's persona is not merely decorative—it governs:

- **Proactive behaviour**: automatically showing offers at session start
- **Confirmation requirements**: never checking out without explicit consent
- **Tone and brevity**: friendly but action-focused, not chatty
- **Constraint awareness**: respecting budget and dietary requirements

---

## Future Extensions

Potential enhancements to this scenario include:

- **Payment integration**: actual payment processing with card tokenisation
- **Delivery scheduling**: choosing delivery slots with availability checking
- **Loyalty points**: tracking and redeeming reward points
- **Recurring orders**: "order my usual" based on purchase history
- **Price comparison**: comparing prices with alternative stores
- **Substitution preferences**: remembering customer preferences for auto-substitution when items are unavailable

---

## Summary

The shopping scenario exemplifies how structured action planning transforms conversational commerce. Rather than rigid button-driven UIs or unstructured chat, customers engage in natural dialogue while the assistant orchestrates a coherent workflow behind the scenes. The key insight is that shopping is a workflow—and the assistant is its conductor.
