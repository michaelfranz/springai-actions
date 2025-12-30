package org.javai.springai.actions.bind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionParam;
import org.javai.springai.actions.internal.bind.ActionBinding;
import org.javai.springai.actions.internal.bind.ActionDescriptor;
import org.javai.springai.actions.internal.bind.ActionParameterDescriptor;
import org.javai.springai.actions.internal.bind.ActionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActionRegistryTest {

	private ActionRegistry registry;

	@BeforeEach
	void setup() {
		registry = new ActionRegistry();
	}

	@Test
	void registersDescriptorAndBindingFromAnnotatedBean() {
		SampleActions bean = new SampleActions();

		registry.registerActions(bean);

		var descriptors = registry.getActionDescriptors();
		var bindings = registry.getActionBindings();

		assertThat(descriptors).hasSize(1);
		assertThat(bindings).hasSize(1);

		ActionDescriptor descriptor = descriptors.getFirst();
		ActionBinding binding = bindings.getFirst();

		assertThat(descriptor.id()).isEqualTo("greet");
		assertThat(descriptor.description()).isEqualTo("Say hello");
		assertThat(descriptor.actionParameterSpecs()).hasSize(2);

		assertThat(binding.id()).isEqualTo("greet");
		assertThat(binding.description()).isEqualTo("Say hello");
		assertThat(binding.bean()).isSameAs(bean);
		assertThat(binding.method().getName()).isEqualTo("greet");
		assertThat(binding.parameters()).hasSize(2);

		// Parameter alignment
		assertThat(binding.parameters().get(0).name()).isEqualTo(descriptor.actionParameterSpecs().get(0).name());
		assertThat(binding.parameters().get(1).name()).isEqualTo(descriptor.actionParameterSpecs().get(1).name());
	}

	@Test
	void duplicateActionIdsThrow() {
		registry.registerActions(new SampleActions());

		assertThatThrownBy(() -> registry.registerActions(new DuplicateActions()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Duplicate action definition");
	}

	@Test
	void capturesParameterMetadata() {
		registry.registerActions(new SampleActions());

		ActionDescriptor descriptor = registry.getActionDescriptors().getFirst();
		var params = descriptor.actionParameterSpecs();

		assertThat(params).hasSize(2);

		ActionParameterDescriptor p0 = params.getFirst();
		assertThat(p0.name()).isEqualTo("name");
		assertThat(p0.typeName()).isEqualTo(String.class.getName());
		assertThat(p0.typeId()).isEqualTo("String");
		assertThat(p0.description()).contains("name");
		assertThat(p0.dslId()).isNull();

		ActionParameterDescriptor p1 = params.get(1);
		assertThat(p1.name()).isEqualTo("note");
		assertThat(p1.typeName()).isEqualTo(String.class.getName());
		assertThat(p1.typeId()).isEqualTo("String");
		assertThat(p1.description()).isEqualTo("short note");
		assertThat(p1.dslId()).isNull();
	}

	private static class SampleActions {
		@Action(description = "Say hello")
		public void greet(String name, @ActionParam(description = "short note") String note) {
		}
	}

	private static class DuplicateActions {
		@Action(description = "Say hello differently")
		public void greet(String name) {
		}
	}
}

