package org.javai.springai.actions.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.lang.reflect.Method;
import java.util.List;
import org.javai.springai.actions.api.Action;
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


		assertThat(spec.call()).isSameAs(callResponseSpec);

		ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
		verify(requestSpec).system(systemCaptor.capture());
		assertThat(systemCaptor.getValue()).isEqualTo("sys-1\n\nsys-2");

		ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
		verify(requestSpec).user(userCaptor.capture());
		assertThat(userCaptor.getValue()).isEqualTo("user-1\n\nuser-2");
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
		assertThat(systemPrompt)
			.contains("=== PLAN AND ACTION DEFINITIONS ===")
			.contains("planAction");
	}

	@Test
	void actionsIgnoreNullValuesGracefully() {
		assertThat(spec.actions((Object[]) null)).isSameAs(spec);
		assertThat(spec.actions(null, new PlanningActions(), null)).isSameAs(spec);
	}

	@Test
	void planThrowsWhenEntityNull() {
		spec.actions(new PlanningActions());
		when(callResponseSpec.entity(Plan.class)).thenReturn(null);

		assertThatThrownBy(spec::plan).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void planThrowsWhenStepsEmpty() {
		spec.actions(new PlanningActions());
		when(callResponseSpec.entity(Plan.class)).thenReturn(new Plan(List.of()));

		assertThatThrownBy(spec::plan).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void buildActionSchemaMessageAlignsWithPlanStepContract() throws Exception {
		spec.actions(new PlanningActions());

		Method method = PlanningPromptSpec.class.getDeclaredMethod("buildActionSchemaMessage");
		method.setAccessible(true);
		String schemaMessage = (String) method.invoke(spec);

		assertThat(schemaMessage)
			.contains("PlanStep entries")
			.contains("\"action\": \"<actionName>\"")
			.contains("PlanStep objects")
			.contains("Action name: planAction")
			.contains("\"action\": \"sendEmail\"")
			.contains("\"steps\"");
	}


	public static class PlanningActions {

		@Action(contextKey = "planResult", description = "Transforms the provided value.")
		public String planAction(String value) {
			return value.toUpperCase();
		}
	}
}

