package org.javai.springai.actions.definition;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.javai.springai.actions.api.Action;
import org.junit.jupiter.api.Test;

class ActionDefinitionFactoryTest {

	@Test
	void fromDiscoversAnnotatedMethodsAndBuildsDescriptions() {
		SampleActions bean = new SampleActions();

		List<ActionDefinition> definitions = ActionDefinitionFactory.from(bean);

  assertThat(definitions).hasSize(2);

		ActionDefinition greet = definitions.stream()
				.filter(def -> def.name().equals("greet"))
				.findFirst()
				.orElseThrow();

  assertThat(greet.description()).isEqualTo("Friendly greeting");
  assertThat(greet.argumentSchema()).isNotNull();
  assertThat(greet.bean()).isSameAs(bean);
  assertThat(greet.method().getName()).isEqualTo("greet");

		ActionDefinition fallback = definitions.stream()
				.filter(def -> def.name().equals("fallback"))
				.findFirst()
				.orElseThrow();

  assertThat(fallback.description()).isEqualTo("Action fallback");
  assertThat(fallback.argumentSchema()).isNotNull();
	}

	static class SampleActions {

		@Action(description = "Friendly greeting")
		public void greet(String name) {
		}

		@Action
		public void fallback() {
		}

		public void ignored() {
		}
	}
}

