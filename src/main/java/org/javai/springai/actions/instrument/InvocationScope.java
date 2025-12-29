package org.javai.springai.actions.instrument;

import java.util.Map;
import java.util.Objects;

/**
 * Helper for emitting invocation lifecycle events with try-with-resources.
 */
public final class InvocationScope implements AutoCloseable {

	private final InvocationEmitter emitter;
	private final InvocationKind kind;
	private final String name;
	private final String invocationId;
	private final String parentInvocationId;
	private final long startNanos;
	private boolean closed;

	InvocationScope(InvocationEmitter emitter, InvocationKind kind, String name, String invocationId,
			String parentInvocationId, Map<String, Object> attributes) {
		this.emitter = Objects.requireNonNull(emitter, "emitter must not be null");
		this.kind = Objects.requireNonNullElse(kind, InvocationKind.ACTION);
		this.name = name != null ? name : "";
		this.invocationId = invocationId;
		this.parentInvocationId = parentInvocationId;
		this.startNanos = System.nanoTime();
		emitter.emit(this.kind, InvocationEventType.REQUESTED, this.name, invocationId, parentInvocationId, null,
				attributes);
		emitter.emit(this.kind, InvocationEventType.STARTED, this.name, invocationId, parentInvocationId, null,
				attributes);
	}

	public String invocationId() {
		return invocationId;
	}

	public void succeed(Map<String, Object> attributes) {
		if (closed) {
			return;
		}
		closed = true;
		long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
		emitter.emit(kind, InvocationEventType.SUCCEEDED, name, invocationId, parentInvocationId, durationMs, attributes);
	}

	public void fail(String message) {
		if (closed) {
			return;
		}
		closed = true;
		long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
		emitter.emit(kind, InvocationEventType.FAILED, name, invocationId, parentInvocationId, durationMs,
				message == null ? Map.of() : Map.of("error", message));
	}

	@Override
	public void close() {
		if (!closed) {
			succeed(Map.of());
		}
	}
}

