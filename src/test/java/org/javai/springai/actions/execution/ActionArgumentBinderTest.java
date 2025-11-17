package org.javai.springai.actions.execution;

import static org.junit.jupiter.api.Assertions.*;

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

		assertSame(ctx, bound[0]);
		assertEquals("Alice", bound[1]);
		assertEquals(5, bound[2]);
		assertEquals("details", bound[3]);
	}

	@Test
	void bindArgumentsThrowsWhenJsonArgumentMissing() throws Exception {
		Method method = BinderSample.class.getMethod("requiresArgument", String.class);
		ActionContext ctx = new ActionContext();

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> binder.bindArguments(method, mapper.createObjectNode(), ctx));

		String expectedParam = method.getParameters()[0].getName();
		assertEquals("Missing required argument '%s' for action '%s'".formatted(expectedParam, "requiresArgument"),
				ex.getMessage());
	}

	static class BinderSample {

		public void fullAction(ActionContext context, @FromContext("user") String username, int quantity, String details) {
		}

		public void requiresArgument(String value) {
		}
	}
}

