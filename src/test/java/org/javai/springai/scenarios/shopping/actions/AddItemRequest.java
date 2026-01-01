package org.javai.springai.scenarios.shopping.actions;

/**
 * Request to add a product and quantity to the shopping basket.
 */
public record AddItemRequest(String product, int quantity) {
}

