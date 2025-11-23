package org.javai.springai.actions.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.api.FromContext;
import org.junit.jupiter.api.Test;

class ActionArgumentBinderTest {

	private final ObjectMapper mapper = new ObjectMapper();
	private final ActionArgumentBinder binder = new ActionArgumentBinder();

	@Test
	void bindArgumentsResolvesContextAndJsonValues() throws Exception {
		Method method = BinderSample.class.getMethod("fullAction", ActionContext.class, String.class, int.class, String.class);
		Parameter[] parameters = method.getParameters();

		ObjectNode args = mapper.createObjectNode();
		args.put(parameters[2].getName(), 5);
		args.put(parameters[3].getName(), "details");

		ActionContext ctx = new ActionContext();
		ctx.put("user", "Alice");

		Object[] bound = binder.bindArguments(method, args, ctx);

		assertThat(bound[0]).isSameAs(ctx);
		assertThat(bound[1]).isEqualTo("Alice");
		assertThat(bound[2]).isEqualTo(5);
		assertThat(bound[3]).isEqualTo("details");
	}

	@Test
	void bindArgumentsThrowsWhenJsonArgumentMissing() throws Exception {
		Method method = BinderSample.class.getMethod("requiresArgument", String.class);
		ActionContext ctx = new ActionContext();

		String expectedParam = method.getParameters()[0].getName();
		assertThatThrownBy(() -> binder.bindArguments(method, mapper.createObjectNode(), ctx))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Missing required argument '%s' for action '%s'".formatted(expectedParam, "requiresArgument"));
	}

	static class BinderSample {

		public void fullAction(ActionContext context, @FromContext("user") String username, int quantity, String details) {
		}

		public void requiresArgument(String value) {
		}
	}
}

