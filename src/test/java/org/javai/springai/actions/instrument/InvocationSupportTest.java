package org.javai.springai.actions.instrument;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.javai.springai.actions.internal.instrument.InvocationEmitter;
import org.javai.springai.actions.internal.instrument.InvocationEvent;
import org.javai.springai.actions.internal.instrument.InvocationEventType;
import org.javai.springai.actions.internal.instrument.InvocationKind;
import org.javai.springai.actions.internal.instrument.InvocationListener;
import org.javai.springai.actions.internal.instrument.InvocationSupport;
import org.junit.jupiter.api.Test;

class InvocationSupportTest {

	@Test
	void emitsLifecycleEvents() {
		List<InvocationEvent> events = new ArrayList<>();
		InvocationListener listener = events::add;
		InvocationEmitter emitter = InvocationEmitter.of("corr-1", listener);

		try (var scope = InvocationSupport.start(emitter, InvocationKind.ACTION, "demo")) {
			scope.succeed(Map.of("k", "v"));
		}

		assertThat(events).hasSize(3);
		assertThat(events.get(0).type()).isEqualTo(InvocationEventType.REQUESTED);
		assertThat(events.get(1).type()).isEqualTo(InvocationEventType.STARTED);
		assertThat(events.get(2).type()).isEqualTo(InvocationEventType.SUCCEEDED);
		assertThat(events.get(2).attributes()).containsEntry("k", "v");
	}

	@Test
	void failEmitsFailed() {
		List<InvocationEvent> events = new ArrayList<>();
		InvocationListener listener = events::add;
		InvocationEmitter emitter = InvocationEmitter.of("corr-2", listener);

		try (var scope = InvocationSupport.start(emitter, InvocationKind.TOOL, "demoTool")) {
			scope.fail("boom");
		}

		assertThat(events.getLast().type()).isEqualTo(InvocationEventType.FAILED);
		assertThat(events.getLast().attributes()).containsEntry("error", "boom");
	}

	@Test
	void autoCloseEmitsSucceededIfNotExplicit() {
		List<InvocationEvent> events = new ArrayList<>();
		InvocationListener listener = events::add;
		InvocationEmitter emitter = InvocationEmitter.of("corr-3", listener);

		try (var scope = InvocationSupport.start(emitter, InvocationKind.ACTION, "auto")) {
			// no explicit succeed/fail
		}

		assertThat(events.getLast().type()).isEqualTo(InvocationEventType.SUCCEEDED);
	}
}

