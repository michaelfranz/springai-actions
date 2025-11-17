package org.javai.springai.actions.definition;

import static org.junit.jupiter.api.Assertions.*;

import org.javai.springai.actions.api.Action;
import org.junit.jupiter.api.Test;

class ActionRegistryTest {

	@Test
	void registerCapturesAnnotatedMethods() {
		ActionRegistry registry = new ActionRegistry();
		registry.register(new RegistryBean());

		RegisteredAction registered = registry.find("doWork");

		assertEquals("doWork", registered.method().getName());
		assertTrue(registered.bean() instanceof RegistryBean);
	}

	@Test
	void findThrowsWhenActionMissing() {
		ActionRegistry registry = new ActionRegistry();

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> registry.find("absent"));

		assertEquals("Unknown steps: absent", ex.getMessage());
	}

	static class RegistryBean {
		@Action
		public void doWork() {
		}

		public void ignored() {
		}
	}
}

