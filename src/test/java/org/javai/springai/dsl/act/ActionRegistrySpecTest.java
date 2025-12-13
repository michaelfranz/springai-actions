package org.javai.springai.dsl.act;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.javai.springai.actions.api.Action;
import org.javai.springai.actions.api.ActionParam;
import org.javai.springai.dsl.bind.TypeFactoryBootstrap;
import org.javai.springai.dsl.sql.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActionRegistrySpecTest {

	@BeforeEach
	void setup() {
		TypeFactoryBootstrap.registerBuiltIns();
	}

	@Test
	void buildsActionSpecFromAnnotatedBean() {
		ActionRegistry registry = new ActionRegistry();
		registry.registerActions(new SampleActions());

		List<ActionSpec> specs = registry.getActionSpecs();
		assertThat(specs).hasSize(1);

		ActionSpec spec = specs.getFirst();
		assertThat(spec.id()).endsWith(".runQuery");
		assertThat(spec.description()).isEqualTo("Run a query");
		assertThat(spec.actionParameterSpecs()).hasSize(2);

		ActionParameterSpec p0 = spec.actionParameterSpecs().get(0);
		assertThat(p0.name()).isEqualTo("query");
		assertThat(p0.typeName()).isEqualTo(Query.class.getName());
		assertThat(p0.typeId()).isEqualTo("sxl-sql:Query");
		assertThat(p0.dslId()).isEqualTo("sxl-sql");

		ActionParameterSpec p1 = spec.actionParameterSpecs().get(1);
		assertThat(p1.name()).isEqualTo("note");
		assertThat(p1.typeName()).isEqualTo(String.class.getName());
		assertThat(p1.typeId()).isEqualTo("String");
		assertThat(p1.dslId()).isNull();
		assertThat(p1.description()).contains("note to include");
	}

	@Test
	void jsonMapperIncludesDslIdAndFields() {
		List<ActionSpec> specs = ActionSpecFactory.fromBeans(new SampleActions());

		var json = ActionSpecJsonMapper.toJsonArray(specs);
		assertThat(json).hasSize(1);
		var first = json.get(0);
		assertThat(first.get("id").asText()).endsWith(".runQuery");
		assertThat(first.get("description").asText()).isEqualTo("Run a query");
		var params = first.get("parameters");
		assertThat(params.size()).isEqualTo(2);
		assertThat(params.get(0).get("typeId").asText()).isEqualTo("sxl-sql:Query");
		assertThat(params.get(0).get("dslId").asText()).isEqualTo("sxl-sql");
		assertThat(params.get(1).get("typeId").asText()).isEqualTo("String");
		assertThat(params.get(1).has("dslId")).isFalse();
	}

	@Test
	void filterStrategyCanExcludeSpecs() {
		List<ActionSpec> specs = ActionSpecFactory.fromBeans(
				spec -> spec.id().endsWith("runQuery"),
				new SampleActions(),
				new OtherActions());

		assertThat(specs).hasSize(1);
		assertThat(specs.getFirst().id()).endsWith("runQuery");
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
