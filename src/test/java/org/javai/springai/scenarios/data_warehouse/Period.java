package org.javai.springai.scenarios.data_warehouse;

import java.time.LocalDate;

/**
 * Time period with start and end dates.
 */
public record Period(LocalDate start, LocalDate end) {
}

