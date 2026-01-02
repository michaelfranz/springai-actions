package org.javai.springai.actions;

import java.util.List;

/**
 * Observability metrics from plan formulation.
 *
 * <p>Provides detailed telemetry about the planning process, including
 * which model ultimately succeeded and how many attempts were made.</p>
 *
 * @param successfulModelId model that produced the final plan (null if all failed)
 * @param totalAttempts total attempts across all tiers
 * @param attempts detailed record of each attempt
 */
public record PlanningMetrics(
        String successfulModelId,
        int totalAttempts,
        List<AttemptRecord> attempts
) {
    /**
     * Canonical constructor with defensive copying.
     */
    public PlanningMetrics {
        attempts = attempts != null ? List.copyOf(attempts) : List.of();
        if (totalAttempts < 0) {
            throw new IllegalArgumentException("totalAttempts must be >= 0");
        }
    }

    /**
     * Create metrics for a single successful attempt (no retries needed).
     *
     * @param modelId identifier of the successful model
     * @param durationMillis time taken for the attempt
     * @return metrics representing a single successful attempt
     */
    public static PlanningMetrics singleSuccess(String modelId, long durationMillis) {
        AttemptRecord record = new AttemptRecord(
                modelId,
                0,
                1,
                AttemptOutcome.SUCCESS,
                durationMillis,
                null
        );
        return new PlanningMetrics(modelId, 1, List.of(record));
    }

    /**
     * Create empty metrics for dry-run or no-op scenarios.
     *
     * @return metrics with no attempts
     */
    public static PlanningMetrics empty() {
        return new PlanningMetrics(null, 0, List.of());
    }

    /**
     * Check if planning ultimately succeeded.
     *
     * <p>Returns true if any attempt resulted in SUCCESS, regardless of whether
     * a modelId was provided.</p>
     *
     * @return true if a model produced a valid plan
     */
    public boolean succeeded() {
        return attempts.stream().anyMatch(AttemptRecord::isSuccess);
    }

    /**
     * Get the number of tiers that were attempted.
     *
     * @return count of distinct tiers used
     */
    public int tiersAttempted() {
        return (int) attempts.stream()
                .mapToInt(AttemptRecord::tierIndex)
                .distinct()
                .count();
    }

    /**
     * Get the final attempt record (the one that determined the outcome).
     *
     * @return the last attempt, or null if no attempts were made
     */
    public AttemptRecord finalAttempt() {
        return attempts.isEmpty() ? null : attempts.getLast();
    }
}

