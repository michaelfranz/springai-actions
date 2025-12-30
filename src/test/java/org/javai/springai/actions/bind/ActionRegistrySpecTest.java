package org.javai.springai.actions.bind;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionParam;
import org.javai.springai.actions.internal.bind.ActionDescriptor;
import org.javai.springai.actions.internal.bind.ActionDescriptorFactory;
import org.javai.springai.actions.internal.bind.ActionDescriptorJsonMapper;
import org.javai.springai.actions.internal.bind.ActionParameterDescriptor;
import org.javai.springai.actions.internal.bind.ActionRegistry;
import org.javai.springai.actions.sql.Query;
import org.junit.jupiter.api.Test;

/**
 * Tests for ActionRegistry and related classes.
 */
class ActionRegistrySpecTest {

	@Test
	void buildsActionSpecFromAnnotatedBean() {
		ActionRegistry registry = new ActionRegistry();
		registry.registerActions(new SampleActions());

		List<ActionDescriptor> descriptors = registry.getActionDescriptors();
		assertThat(descriptors).hasSize(1);

		ActionDescriptor descriptor = descriptors.getFirst();
		assertThat(descriptor.id()).isEqualTo("runQuery");
		assertThat(descriptor.description()).isEqualTo("Run a query");
		assertThat(descriptor.actionParameterSpecs()).hasSize(2);

		ActionParameterDescriptor p0 = descriptor.actionParameterSpecs().get(0);
		assertThat(p0.name()).isEqualTo("query");
		assertThat(p0.typeName()).isEqualTo(Query.class.getName());
		assertThat(p0.typeId()).isEqualTo("sql-query:Query");
		assertThat(p0.dslId()).isEqualTo("sql-query");

		ActionParameterDescriptor p1 = descriptor.actionParameterSpecs().get(1);
		assertThat(p1.name()).isEqualTo("note");
		assertThat(p1.typeName()).isEqualTo(String.class.getName());
		assertThat(p1.typeId()).isEqualTo("String");
		assertThat(p1.dslId()).isNull();
		assertThat(p1.description()).contains("note to include");
	}

	@Test
	void jsonMapperIncludesDslIdAndFields() {
		List<ActionDescriptor> descriptors = ActionDescriptorFactory.fromBeans(new SampleActions());

		var json = ActionDescriptorJsonMapper.toJsonArray(descriptors);
		assertThat(json).hasSize(1);
		var first = json.get(0);
		assertThat(first.get("id").asText()).isEqualTo("runQuery");
		assertThat(first.get("description").asText()).isEqualTo("Run a query");
		var params = first.get("parameters");
		assertThat(params.size()).isEqualTo(2);
		assertThat(params.get(0).get("typeId").asText()).isEqualTo("sql-query:Query");
		assertThat(params.get(0).get("dslId").asText()).isEqualTo("sql-query");
		assertThat(params.get(1).get("typeId").asText()).isEqualTo("String");
		assertThat(params.get(1).has("dslId")).isFalse();
	}

	@Test
	void filterStrategyCanExcludeSpecs() {
		List<ActionDescriptor> descriptors = ActionDescriptorFactory.fromBeans(
				spec -> spec.id().endsWith("runQuery"),
				new SampleActions(),
				new OtherActions());

		assertThat(descriptors).hasSize(1);
		assertThat(descriptors.getFirst().id()).isEqualTo("runQuery");
	}

	private static class SampleActions {
		@Action(description = "Run a query")
		public void runQuery(Query query, @ActionParam(description = "note to include") String note) {
		}
	}

	private static class OtherActions {
		@Action(description = "Other action")
		public void otherAction(String input) {
		}
	}
}
