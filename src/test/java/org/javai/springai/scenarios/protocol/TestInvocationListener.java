package org.javai.springai.scenarios.protocol;

import java.util.ArrayList;
import java.util.List;
import org.javai.springai.actions.internal.instrument.InvocationEvent;
import org.javai.springai.actions.internal.instrument.InvocationListener;

/**
 * Test listener for capturing invocation events during protocol notebook scenario.
 */
final class TestInvocationListener implements InvocationListener {
	public final List<InvocationEvent> events = new ArrayList<>();

	@Override
	public void onEvent(InvocationEvent event) {
		if (event != null) {
			events.add(event);
		}
	}
}

