package org.javai.springai.actions.planning;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Method;
import java.util.List;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionContext;
import org.javai.springai.actions.execution.ActionPlanResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanningPromptSpecTest {

	@Mock
	private ChatClient.ChatClientRequestSpec requestSpec;

	@Mock
	private ChatClient.CallResponseSpec callResponseSpec;

	private final ObjectMapper mapper = new ObjectMapper();

	private PlanningPromptSpec spec;

	@BeforeEach
	void setUp() {
		when(requestSpec.call()).thenReturn(callResponseSpec);
		spec = new PlanningPromptSpec(requestSpec);
	}

	@Test
	void systemAndUserMessagesAreConcatenatedOnCall() {
		spec.system("sys-1").system("sys-2");
		spec.user("user-1").user("user-2");

		assertSame(callResponseSpec, spec.call());

		ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
		verify(requestSpec).system(systemCaptor.capture());
		assertEquals("sys-1\n\nsys-2", systemCaptor.getValue());

		ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
		verify(requestSpec).user(userCaptor.capture());
		assertEquals("user-1\n\nuser-2", userCaptor.getValue());
	}

	@Test
	void toolsDelegatesToUnderlyingRequestSpec() {
		Object toolBean = new Object();

		spec.tools(toolBean);

		verify(requestSpec).tools(toolBean);
	}

	@Test
	void actionsIncludeSchemaMessageInSystemPrompt() {
		spec.actions(new PlanningActions());
		spec.call();

		ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
		verify(requestSpec).system(systemCaptor.capture());

		String systemPrompt = systemCaptor.getValue();
		assertTrue(systemPrompt.contains("=== ACTION DEFINITIONS ==="));
		assertTrue(systemPrompt.contains("planAction"));
	}

	@Test
	void actionsIgnoreNullValuesGracefully() {
		assertSame(spec, spec.actions((Object[]) null));
		assertSame(spec, spec.actions(null, new PlanningActions(), null));
	}

	@Test
	void planThrowsWhenEntityNull() {
		spec.actions(new PlanningActions());
		when(callResponseSpec.entity(ActionPlan.class)).thenReturn(null);

		assertThrows(IllegalStateException.class, spec::plan);
	}

	@Test
	void planThrowsWhenStepsEmpty() {
		spec.actions(new PlanningActions());
		when(callResponseSpec.entity(ActionPlan.class)).thenReturn(new ActionPlan(List.of()));

		assertThrows(IllegalStateException.class, spec::plan);
	}

	@Test
	void planReturnsExecutableActionsFromDefinitions() throws Exception {
		PlanningActions bean = new PlanningActions();
		spec.actions(bean);

		Method method = PlanningActions.class.getMethod("planAction", String.class);
		String paramName = method.getParameters()[0].getName();
		ObjectNode arguments = mapper.createObjectNode();
		arguments.put(paramName, "value");
		ActionPlan plan = new ActionPlan(List.of(new ActionStep("planAction", arguments)));

		when(callResponseSpec.entity(ActionPlan.class)).thenReturn(plan);

		ActionPlanResult result = spec.plan();
		assertSame(plan, result.plan());
		assertEquals(1, result.executableActions().size());

		ActionContext ctx = new ActionContext();
		result.executableActions().get(0).perform(ctx);
		assertEquals("VALUE", ctx.get("planResult", String.class));
	}

	public static class PlanningActions {

		@Action(contextKey = "planResult", description = "Transforms the provided value.")
		public String planAction(String value) {
			return value.toUpperCase();
		}
	}
}

