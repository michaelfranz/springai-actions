package org.javai.springai.actions.internal.instrument;

import java.time.Instant;
import java.util.Map;

/**
 * Unified event covering both tool and action invocations.
 */
public record InvocationEvent(
        InvocationKind kind,
        InvocationEventType type,
        String name,
        String correlationId,
        String invocationId,
        String parentInvocationId,
        Instant timestamp,
        Long durationMs,
        Map<String, Object> attributes) {

    public InvocationEvent {
        kind = kind != null ? kind : InvocationKind.ACTION;
        type = type != null ? type : InvocationEventType.REQUESTED;
        name = name != null ? name : "";
        correlationId = correlationId != null ? correlationId : "";
        invocationId = invocationId != null ? invocationId : "";
        parentInvocationId = parentInvocationId != null ? parentInvocationId : "";
        timestamp = timestamp != null ? timestamp : Instant.now();
        attributes = attributes != null ? Map.copyOf(attributes) : Map.of();
    }
}

