package org.javai.springai.actions.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class ActionContextTest {

	@Test
	void putAndGetStoresValues() {
		ActionContext ctx = new ActionContext();
		ctx.put("name", "Spring");

		String value = ctx.get("name", String.class);

		assertThat(value).isEqualTo("Spring");
		assertThat(ctx.contains("name")).isTrue();
	}

	@Test
	void getThrowsWhenKeyMissing() {
		ActionContext ctx = new ActionContext();

		assertThatThrownBy(() -> ctx.get("missing", String.class))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("No value for context key: missing");
		assertThat(ctx.contains("missing")).isFalse();
	}
}

