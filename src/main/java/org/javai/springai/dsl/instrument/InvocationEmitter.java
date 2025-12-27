package org.javai.springai.dsl.instrument;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Emits invocation events to registered listeners.
 */
public class InvocationEmitter {

	private final String correlationId;
	private final List<InvocationListener> listeners;

	public InvocationEmitter(String correlationId, List<InvocationListener> listeners) {
		this.correlationId = correlationId != null ? correlationId : "";
		this.listeners = listeners != null ? List.copyOf(listeners) : List.of();
	}

	public static InvocationEmitter of(String correlationId, InvocationListener... listeners) {
		List<InvocationListener> list = new ArrayList<>();
		if (listeners != null) {
			for (InvocationListener l : listeners) {
				if (l != null) {
					list.add(l);
				}
			}
		}
		return new InvocationEmitter(correlationId, list);
	}

	public String correlationId() {
		return correlationId;
	}

	public boolean hasListeners() {
		return !listeners.isEmpty();
	}

	public String nextInvocationId() {
		return UUID.randomUUID().toString();
	}

	public void emit(InvocationKind kind, InvocationEventType type, String name, String invocationId,
			String parentInvocationId, Long durationMs, Map<String, Object> attributes) {
		if (listeners.isEmpty()) {
			return;
		}
		InvocationEvent event = new InvocationEvent(
				Objects.requireNonNullElse(kind, InvocationKind.ACTION),
				Objects.requireNonNullElse(type, InvocationEventType.REQUESTED),
				name,
				correlationId,
				invocationId,
				parentInvocationId,
				Instant.now(),
				durationMs,
				attributes);
		for (InvocationListener listener : listeners) {
			try {
				listener.onEvent(event);
			}
			catch (Exception ignored) {
				// Do not let listener failures break execution
			}
		}
	}
}

