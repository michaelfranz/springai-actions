package org.javai.springai.actions.instrument;

import java.util.Map;
import java.util.Objects;

/**
 * Lightweight helper to instrument actions/tools manually.
 * Usage (try-with-resources):
 *
 * <pre>
 * try (var scope = InvocationSupport.start(emitter, InvocationKind.ACTION, "myAction")) {
 *     // do work
 *     scope.succeed(Map.of("key", "value"));
 * }
 * </pre>
 */
public final class InvocationSupport {

	private InvocationSupport() {
	}

	public static InvocationScope start(InvocationEmitter emitter, InvocationKind kind, String name) {
		return start(emitter, kind, name, null, Map.of());
	}

	public static InvocationScope start(InvocationEmitter emitter, InvocationKind kind, String name,
			String parentInvocationId, Map<String, Object> attributes) {
		Objects.requireNonNull(emitter, "emitter must not be null");
		String invocationId = emitter.nextInvocationId();
		return new InvocationScope(emitter, kind, name, invocationId, parentInvocationId, attributes);
	}

	public static InvocationScope startWithId(InvocationEmitter emitter, InvocationKind kind, String name,
			String invocationId, String parentInvocationId, Map<String, Object> attributes) {
		Objects.requireNonNull(emitter, "emitter must not be null");
		return new InvocationScope(emitter, kind, name, invocationId, parentInvocationId, attributes);
	}
}

