package org.javai.springai.actions.execution;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Method;
import java.util.List;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.definition.ActionDefinition;
import org.javai.springai.actions.planning.ActionStep;
import org.junit.jupiter.api.Test;

class ExecutableActionFactoryTest {

	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	void fromCreatesExecutableActionThatInvokesBeanAndStoresReturnValue() throws Exception {
		SampleExecutableActions bean = new SampleExecutableActions();
		Method method = SampleExecutableActions.class.getMethod("greet", String.class);
		ExecutableActionFactory factory = new ExecutableActionFactory(List.of(definition(bean, method)));

		ObjectNode args = mapper.createObjectNode();
		String parameterName = method.getParameters()[0].getName();
		args.put(parameterName, "Bob");

		ExecutableAction executable = factory.from(new ActionStep("greet", args));
		ActionContext context = new ActionContext();
		executable.perform(context);

		assertEquals("Bob", bean.getLastArgument());
		assertEquals("Hello Bob", bean.getLastOutput());
		assertTrue(context.contains("greetingResult"));
		assertEquals("Hello Bob", context.get("greetingResult", String.class));
	}

	@Test
	void fromDoesNotStoreReturnValueWhenNull() throws Exception {
		SampleExecutableActions bean = new SampleExecutableActions();
		Method method = SampleExecutableActions.class.getMethod("returnsNull");
		ExecutableActionFactory factory = new ExecutableActionFactory(List.of(definition(bean, method)));

		ExecutableAction executable = factory.from(new ActionStep("returnsNull", mapper.createObjectNode()));
		ActionContext context = new ActionContext();
		executable.perform(context);

		assertNull(bean.getLastOutput());
		assertFalse(context.contains("nullStore"));
	}

	@Test
	void fromDoesNotStoreWhenAnnotationMissing() throws Exception {
		SampleExecutableActions bean = new SampleExecutableActions();
		Method method = SampleExecutableActions.class.getMethod("nonAnnotated", String.class);
		ExecutableActionFactory factory = new ExecutableActionFactory(List.of(definition(bean, method)));

		ObjectNode args = mapper.createObjectNode();
		args.put(method.getParameters()[0].getName(), "value");

		ExecutableAction executable = factory.from(new ActionStep("nonAnnotated", args));
		ActionContext context = new ActionContext();
		executable.perform(context);

		assertEquals("value", bean.getLastArgument());
		assertEquals("VALUE", bean.getLastOutput());
		assertFalse(context.contains("nonAnnotated"));
	}

	@Test
	void fromThrowsWhenActionUnknown() {
		ExecutableActionFactory factory = new ExecutableActionFactory(List.of());

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> factory.from(new ActionStep("missing", mapper.createObjectNode())));

		assertEquals("Unknown action: missing", ex.getMessage());
	}

	private ActionDefinition definition(Object bean, Method method) {
		return new ActionDefinition(
				method.getName(),
				method.getName(),
				mapper.createObjectNode(),
				bean,
				method
		);
	}

	static class SampleExecutableActions {
		private String lastArgument;
		private String lastOutput;

		@Action(contextKey = "greetingResult")
		public String greet(String name) {
			lastArgument = name;
			lastOutput = "Hello " + name;
			return lastOutput;
		}

		@Action(contextKey = "nullStore")
		public String returnsNull() {
			lastArgument = "returnsNull";
			lastOutput = null;
			return null;
		}

		public String nonAnnotated(String value) {
			lastArgument = value;
			lastOutput = value.toUpperCase();
			return lastOutput;
		}

		String getLastArgument() {
			return lastArgument;
		}

		String getLastOutput() {
			return lastOutput;
		}
	}
}

