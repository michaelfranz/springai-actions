package org.javai.springai.actions;

/**
 * Outcome of a single planning attempt.
 *
 * <p>Used by the retry/fallback mechanism to classify why an attempt
 * succeeded or failed, enabling appropriate retry decisions.</p>
 */
public enum AttemptOutcome {
    /**
     * Plan was successfully parsed and validated.
     */
    SUCCESS,

    /**
     * Plan parsed but failed validation (e.g., unknown action, schema violation).
     */
    VALIDATION_FAILED,

    /**
     * LLM response could not be parsed as a valid plan.
     */
    PARSE_FAILED,

    /**
     * Network or API error prevented LLM invocation.
     */
    NETWORK_ERROR
}

