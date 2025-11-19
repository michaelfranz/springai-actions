package org.javai.springai.actions.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.Map;
import org.javai.springai.actions.api.ActionContext;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ContextExpressionResolverTest {

	@Nested
	class MapResolution {

		@Test
		void resolvesNestedMapValues() {
			ActionContext ctx = new ActionContext();
			ctx.put("order", Map.of("status", "PLACED", "details", Map.of("total", 42)));

			assertThat(ContextExpressionResolver.resolve(ctx, "order.status"))
					.isEqualTo("PLACED");
			assertThat(ContextExpressionResolver.resolve(ctx, "order.details.total"))
					.isEqualTo("42");
		}

		@Test
		void failsWhenMapKeyMissing() {
			ActionContext ctx = new ActionContext();
			ctx.put("order", Map.of("status", "PLACED"));

			assertThatThrownBy(() -> ContextExpressionResolver.resolve(ctx, "order.total"))
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("Map does not contain key");
		}
	}

	@Nested
	class RecordAndBeanResolution {

		@Test
		void resolvesRecordComponents() {
			ActionContext ctx = new ActionContext();
			ctx.put("order", new Order("ABC", "PLACED"));

			assertThat(ContextExpressionResolver.resolve(ctx, "order.orderId"))
					.isEqualTo("ABC");
			assertThat(ContextExpressionResolver.resolve(ctx, "order.status"))
					.isEqualTo("PLACED");
		}

		@Test
		void resolvesStandardGetters() {
			ActionContext ctx = new ActionContext();
			ctx.put("customer", new Customer("123", "Alice"));

			assertThat(ContextExpressionResolver.resolve(ctx, "customer.id"))
					.isEqualTo("123");
			assertThat(ContextExpressionResolver.resolve(ctx, "customer.name"))
					.isEqualTo("Alice");
		}

		@Test
		void failsWhenPropertyMissing() {
			ActionContext ctx = new ActionContext();
			ctx.put("order", new Order("ABC", "PLACED"));

			assertThatThrownBy(() -> ContextExpressionResolver.resolve(ctx, "order.createdAt"))
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("Cannot resolve property");
		}
	}

	@Nested
	class RootResolution {

		@Test
		void resolvesRootKey() {
			ActionContext ctx = new ActionContext();
			ctx.put("value", "root");

			assertThat(ContextExpressionResolver.resolve(ctx, "value"))
					.isEqualTo("root");
		}

		@Test
		void failsForUnknownRoot() {
			ActionContext ctx = new ActionContext();

			assertThatThrownBy(() -> ContextExpressionResolver.resolve(ctx, "missing"))
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("No value for context key");
		}
	}

	private record Order(String orderId, String status) {
	}

	private record Customer(String id, String name) {}
}

