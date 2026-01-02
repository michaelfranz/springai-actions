package org.javai.springai.actions;

/**
 * Record of a single planning attempt for observability.
 *
 * <p>Captured by the retry/fallback mechanism to provide detailed
 * telemetry about the planning process.</p>
 *
 * @param modelId identifier of the model used (may be null if not specified)
 * @param tierIndex 0-based index of the tier (0 = default, 1 = first fallback, etc.)
 * @param attemptWithinTier 1-based attempt number within this tier
 * @param outcome result of the attempt
 * @param durationMillis time taken for this attempt in milliseconds
 * @param errorDetails error message if outcome is not SUCCESS, null otherwise
 */
public record AttemptRecord(
        String modelId,
        int tierIndex,
        int attemptWithinTier,
        AttemptOutcome outcome,
        long durationMillis,
        String errorDetails
) {
    /**
     * Canonical constructor with validation.
     */
    public AttemptRecord {
        if (tierIndex < 0) {
            throw new IllegalArgumentException("tierIndex must be >= 0");
        }
        if (attemptWithinTier < 1) {
            throw new IllegalArgumentException("attemptWithinTier must be >= 1");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("outcome must not be null");
        }
        if (durationMillis < 0) {
            throw new IllegalArgumentException("durationMillis must be >= 0");
        }
    }

    /**
     * Check if this attempt was successful.
     */
    public boolean isSuccess() {
        return outcome == AttemptOutcome.SUCCESS;
    }
}

