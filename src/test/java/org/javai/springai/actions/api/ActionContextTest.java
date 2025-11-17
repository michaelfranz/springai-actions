package org.javai.springai.actions.api;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ActionContextTest {

	@Test
	void putAndGetStoresValues() {
		ActionContext ctx = new ActionContext();
		ctx.put("name", "Spring");

		String value = ctx.get("name", String.class);

		assertEquals("Spring", value);
		assertTrue(ctx.contains("name"));
	}

	@Test
	void getThrowsWhenKeyMissing() {
		ActionContext ctx = new ActionContext();

		IllegalStateException ex = assertThrows(IllegalStateException.class, () -> ctx.get("missing", String.class));

		assertEquals("No value for context key: missing", ex.getMessage());
		assertFalse(ctx.contains("missing"));
	}
}

