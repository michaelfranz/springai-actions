package org.javai.springai.scenarios.data_warehouse;

import java.time.LocalDate;

/**
 * Date range for order value queries.
 */
public record Period(LocalDate start, LocalDate end) {
}

