package org.javai.springai.scenarios.data_warehouse;

/**
 * Query for aggregate order values with customer name and time period.
 */
public record OrderValueQuery(String customer_name, Period period) {
}

