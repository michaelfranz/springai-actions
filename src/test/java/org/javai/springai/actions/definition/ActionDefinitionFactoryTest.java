package org.javai.springai.actions.definition;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.javai.springai.actions.api.Action;
import org.junit.jupiter.api.Test;

class ActionDefinitionFactoryTest {

	@Test
	void fromDiscoversAnnotatedMethodsAndBuildsDescriptions() {
		SampleActions bean = new SampleActions();

		List<ActionDefinition> definitions = ActionDefinitionFactory.from(bean);

		assertEquals(2, definitions.size());

		ActionDefinition greet = definitions.stream()
				.filter(def -> def.name().equals("greet"))
				.findFirst()
				.orElseThrow();

		assertEquals("Friendly greeting", greet.description());
		assertNotNull(greet.argumentSchema());
		assertEquals(bean, greet.bean());
		assertEquals("greet", greet.method().getName());

		ActionDefinition fallback = definitions.stream()
				.filter(def -> def.name().equals("fallback"))
				.findFirst()
				.orElseThrow();

		assertEquals("Action fallback", fallback.description());
		assertNotNull(fallback.argumentSchema());
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

